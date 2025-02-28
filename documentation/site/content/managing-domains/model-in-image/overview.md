+++
title = "Overview"
date = 2020-03-11T16:45:16-05:00
weight = 10
pre = "<b> </b>"
description = "Introduction to Model in Image, description of its runtime behavior, and references."
+++

{{< table_of_contents >}}

### Introduction

Model in Image is an alternative to the operator's Domain in Image and Domain on PV domain home source types. For a comparison, see [Choose a domain home source type]({{< relref "/managing-domains/choosing-a-model/_index.md" >}}). Unlike Domain on PV and Domain in Image, Model in Image eliminates the need to pre-create your WebLogic domain home prior to deploying your Domain YAML file.

It enables:

 - Defining a WebLogic domain home configuration using WebLogic Deploy Tooling (WDT) model files and application archives.
 - Embedding these files in a single image that also contains a WebLogic installation,
   and using the WebLogic Image Tool (WIT) to generate this image. Or, alternatively,
   embedding the files in one or more application-specific images.
 - Optionally, supplying additional model files using a Kubernetes ConfigMap.
 - Supplying Kubernetes Secrets that resolve macro references within the models.
   For example, a secret can be used to supply a database credential.
 - Updating WDT model files at runtime. The WDT models are considered the source of truth and match the domain configuration at all times.  For example, you can add a data source
   to a running domain. See [Runtime updates](#runtime-updates) for details.

This feature is supported for standard WLS domains. For JRF domains, use [Domain on PV]({{< relref "/managing-domains/domain-on-pv/overview.md" >}}).

### WebLogic Deploy Tooling models

WDT models are a convenient and simple alternative to WebLogic Scripting Tool (WLST)
configuration scripts.
They compactly define a WebLogic domain using YAML files and support including
application archives in a ZIP file. For a description of the model format
and its integration with Model in Image,
see [Usage]({{< relref "/managing-domains/model-in-image/usage.md" >}})
and [Model files]({{< relref "/managing-domains/working-with-wdt-models/model-files.md" >}}).
The WDT model format is fully described in the open source,
[WebLogic Deploy Tooling](https://oracle.github.io/weblogic-deploy-tooling/) GitHub project.

### Runtime behavior

When you deploy a Model in Image domain resource YAML file:

  - The operator will run a Kubernetes Job called the 'introspector job' that:
    - Merges your WDT models.
    - Runs WDT tooling to generate a domain home.
    - Packages the domain home and passes it to the operator.

  - After the introspector job completes:
    - The operator creates one or more ConfigMaps following the pattern `DOMAIN_UID-weblogic-domain-introspect-cm***`.
    - The operator subsequently boots your domain's WebLogic Server pods.
    - The pods will obtain their domain home from the ConfigMap.

### Runtime updates

Model updates can be applied at runtime by changing an image, secrets, a domain resource, or a WDT model ConfigMap after initial deployment.

Some updates may be applied to a running domain without requiring any WebLogic pod restarts (an online update),
but others may require rolling the pods to propagate the update's changes (an offline update),
and still others may require shutting down the entire domain before applying the update (a full domain restart update).
_It is the administrator's responsibility to make the necessary changes to a domain resource to initiate the correct type of update._

See [Runtime updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}).

### Continuous integration and delivery (CI/CD)

To understand how Model in Image works with CI/CD, see [CI/CD considerations]({{< relref "/managing-domains/cicd/_index.md" >}}).

### References

 - [Model in Image sample]({{< relref "/samples/domains/model-in-image/_index.md" >}})
 - [WebLogic Deploy Tooling (WDT)](https://oracle.github.io/weblogic-deploy-tooling/)
 - [WebLogic Image Tool (WIT)](https://oracle.github.io/weblogic-image-tool/)
 - Domain [schema](https://github.com/oracle/weblogic-kubernetes-operator/blob/{{< latestMinorVersion >}}/documentation/domains/Domain.md), [documentation]({{< relref "/managing-domains/domain-resource.md" >}})
 - HTTP load balancers: Ingress [documentation]({{< relref "/managing-domains/accessing-the-domain/ingress/_index.md" >}}), [sample]({{< relref "/samples/ingress/_index.md" >}})
 - [CI/CD considerations]({{< relref "/managing-domains/cicd/_index.md" >}})
