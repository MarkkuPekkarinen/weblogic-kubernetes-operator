# !/bin/sh
# Copyright (c) 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Helper script for creating and labeling a Kubernetes configmap.  The configmap
# is labeled with the specified domain-uid.
#
# Usage:
#
# ./create_configmap.sh [-n mynamespace] [-d mydomainuid] -c myconfigmap [-l key1=val1] [-l key2=val2] ...
# 
# -d <domain_uid>     : Defaults to $DOMAIN_UID if DOMAIN_UID is set, 'sample-domain1' otherwise.
# -n <namespace>      : Defaults to $DOMAIN_NAMESPACE if DOMAIN_NAMESPACE is set, 'DOMAIN_UID-ns' otherwise.
# -c <configmap-name> : Name of configmap. Required.
# -f <filename>       : File or directory location. Can be specified more than once. 
#                       Key will be the file-name(s), value will be file contents.

set -e

DOMAIN_UID="${DOMAIN_UID:-sample-domain1}"
DOMAIN_NAMESPACE="${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}"
CONFIGMAP_NAME=""
FILENAMES=""

while [ ! "$1" = "" ]; do
  if [ "$2" = "" ]; then
    echo Syntax Error
    exit 1
  fi
  case "$1" in
    -c) CONFIGMAP_NAME="${2}"
        ;;
    -n) DOMAIN_NAMESPACE="${2}"
        ;;
    -d) DOMAIN_UID="${2}"
        ;;
    -f) FILENAMES="${FILENAMES} --from-file=${2}"
        ;;
    *)  echo Syntax Error
        exit 1
        ;;
  esac
  shift
  shift
done

set -eux

kubectl -n $DOMAIN_NAMESPACE delete configmap $CONFIGMAP_NAME --ignore-not-found
kubectl -n $DOMAIN_NAMESPACE create configmap $CONFIGMAP_NAME $FILENAMES
kubectl -n $DOMAIN_NAMESPACE label  configmap $CONFIGMAP_NAME weblogic.domainUID=$DOMAIN_UID

