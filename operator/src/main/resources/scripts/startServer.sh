#!/bin/bash

# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

#
# startServer.sh
# This is the script WebLogic Operator WLS Pods use to start their WL Server.
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/traceUtils.sh
[ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit 1

trace "Starting WebLogic Server '${SERVER_NAME}'."

#
# Define helper fn to create a folder
#

function createFolder {
  # TBD was 777
  mkdir -m 750 -p $1
  if [ ! -d $1 ]; then
    trace "Unable to create folder $1"
    exit 1
  fi
}

#
# Define helper fn to copy a file only if src & tgt differ
# TBD test this function stand-alone
#

function copyIfChanged() {
  [ ! -f "${1?}" ] && echo "File '$1' not found." && exit 1
  if [ ! -f "${2?}" ] || [ ! -z "`diff $1 $2 2>&1`" ]; then
    trace "Copying '$1' to '$2'."
    cp $1 $2 || exit 1
    chmod 750 $2 || exit 1
  else
    trace "Skipping copy of '$1' to '$2' -- these files already match."
  fi
}

#
# Check and display input env vars
#

checkEnv \
  DOMAIN_UID \
  DOMAIN_NAME \
  DOMAIN_HOME \
  NODEMGR_HOME \
  SERVER_NAME \
  SERVICE_NAME \
  ADMIN_NAME \
  ADMIN_PORT \
  SERVER_OUT_IN_POD_LOG \
  REDIRECT_LOGS \
  AS_SERVICE_NAME || exit 1

trace "LOG_HOME=${LOG_HOME}"
trace "REDIRECT_LOGS=${REDIRECT_LOGS}"
trace "SERVER_OUT_IN_POD_LOG=${SERVER_OUT_IN_POD_LOG}"
trace "USER_MEM_ARGS=${USER_MEM_ARGS}"
trace "JAVA_OPTIONS=${JAVA_OPTIONS}"

#
# Check if introspector actually ran.  This should never fail since
# the operator shouldn't try run a wl pod if the introspector failed.
#

if [ ! -f /weblogic-operator/introspector/boot.properties ]; then
  trace "Error:  Missing introspector file '${bootpfile}'.  Introspector failed to run."
  exit 1
fi

#
# Copy/update domain introspector files for the domain
#
# - Copy introspector boot.properties to '${DOMAIN_HOME}/servers/${SERVER_NAME}/security
# 
# - Copy introspector situational config files to '${DOMAIN_HOME}/optconfig
#
# - Note:  We don't update a file when it's unchanged because a new timestamp might
#          trigger unnecessary situational config overhead.
#

createFolder ${DOMAIN_HOME}/servers/${SERVER_NAME}/security
copyIfChanged /weblogic-operator/introspector/boot.properties \
              ${DOMAIN_HOME}/servers/${SERVER_NAME}/security/boot.properties

createFolder ${DOMAIN_HOME}/optconfig
for local_fname in /weblogic-operator/introspector/*.xml ; do
  copyIfChanged $local_fname ${DOMAIN_HOME}/optconfig/`basename $local_fname`
done

#
# Delete any old situational config files in '${DOMAIN_HOME}/optconfig' 
# that don't have a corresponding /weblogic-operator/introspector file.
#

# TBD test that following actually works - for example, have admin server touch file
#     there and see if the file goes away when the second server boots

for local_fname in ${DOMAIN_HOME}/optconfig/*.xml ; do
  if [ ! -f "/weblogic-operator/introspector/`basename $local_fname`" ]; then
    trace "Deleting '$local_fname' since it has no corresponding /weblogic-operator/introspector file."
    rm -f $local_fname || exit 1
  fi
done

#
# Start NM
#

trace "Start node manager"
# call script to start node manager in same shell 
# $SERVER_OUT_FILE will be set in startNodeManager.sh
. ${SCRIPTPATH}/startNodeManager.sh || exit 1

#
# Start WL Server
#

# TBD We should probably || exit 1 if start-server.py itself fails, and dump NM log to stdout

trace "Start WebLogic Server via the nodemanager"
${SCRIPTPATH}/wlst.sh $SCRIPTPATH/start-server.py

#
# Wait forever.   Kubernetes will monitor this pod via liveness and readyness probes.
#

if [ "${SERVER_OUT_IN_POD_LOG}" == 'true' ] ; then
  trace "Showing the server out file from ${SERVER_OUT_FILE}"
  tail -F -n +0 ${SERVER_OUT_FILE}
else
  trace "Wait indefinitely so that the Kubernetes pod does not exit and try to restart"
  while true; do sleep 60; done
fi

