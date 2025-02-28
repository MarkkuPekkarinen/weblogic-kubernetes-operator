+++
title = "Choose a domain home source type"
date = 2019-02-23T16:44:54-05:00
weight = 2
description = "Choose among these domain home types depending on your requirements and create images that are appropriate for your type."
+++

This document describes the domain home source types for deploying a domain, and creating images that are appropriate for each type.

{{< table_of_contents >}}

### Overview of domain home source types

When using the operator to start WebLogic Server instances from a domain, you have the choice of the following WebLogic domain home source types:

{{% notice note %}}The **Domain in Image** domain home source type is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.
{{% /notice %}}

 - **[Model in Image]({{< relref "/samples/domains/model-in-image/_index.md" >}})**:
   - Set the domain resource `domain.spec.domainHomeSourceType` attribute to `FromModel`.
   - Supply a WebLogic installation in an image and supply a WebLogic configuration in one of three ways:
     - As WDT model YAML file supplied in separate
       [auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).
     - As WebLogic Deployment Tool (WDT) model YAML file layered on the WebLogic installation image. **NOTE**: Model in Image without auxiliary images (the WDT model and installation files are included in the same image with the WebLogic Server installation) will be deprecated in WebLogic Kubernetes Operator version 4.0.7. Oracle recommends that you use Model in Image _with_ auxiliary images. See [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).
     - As WDT model YAML file in a Kubernetes ConfigMap.
   - Supply WebLogic applications in one of two ways:
     - In auxiliary images.
     - Layered on the installation image. **NOTE**: Model in Image without auxiliary images (the WDT model and installation files are included in the same image with the WebLogic Server installation) will be deprecated in WebLogic Kubernetes Operator version 4.0.7. Oracle recommends that you use Model in Image _with_ Auxiliary images. See [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).
   - Mutate the WebLogic configuration by supplying a new image and rolling,
     or [model updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}})
     supplied in a Kubernetes ConfigMap.

 - **[Domain in Image]({{< relref "/samples/domains/domain-home-in-image/_index.md" >}})**:
 **NOTE**: The Domain in Image domain home source type is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.
   - Set the domain resource `domain.spec.domainHomeSourceType` attribute to `Image`.
   - Supply a WebLogic installation in an image and supply a WebLogic configuration as a domain home layered on this image.
   - Supply WebLogic applications layered on the installation image.
   - Mutate the WebLogic configuration by supplying a new image and rolling,
     or by [configuration overrides]({{< relref "/managing-domains/configoverrides/_index.md" >}})
     supplied in a Kubernetes ConfigMap.

 - **[Domain on PV]({{< relref "/samples/domains/domain-home-on-pv/_index.md" >}})**:
   - Set the domain resource `domain.spec.domainHomeSourceType` attribute to `PersistentVolume`.
   - Supply a WebLogic installation in an image and supply a WebLogic configuration as a domain home in a persistent volume.
   - Optionally supply the domain resource `domain.spec.configuration.initialDomainOnPV` section to provide information for the Operator to create the initial domain home.
   - Supply WebLogic applications in the persistent volume.
   - Update the WebLogic configuration using WLST, or the WebLogic Server Administration Console.
   - Optionally use [configuration overrides]({{< relref "/managing-domains/configoverrides/_index.md" >}})
     supplied in a Kubernetes ConfigMap.  Use this only if WLST, or the WebLogic Server Administration Console does not fit your deployment strategy.

Note that you can use different domain home types for different domains; there's no restriction on having domains with different domain home types in the same Kubernetes cluster or namespace.

There are advantages for each domain home source type where Model in Image is the most popular choice, but sometimes there are also technical limitations of various cloud providers that may make one type better suited to your needs. The following table compares the types:

| Domain on PV                                                                                                                                                                                                                                                                                                                                                                                                                            | Domain in Image | Model in Image |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| --- | --- |
| Lets you use the same standard WebLogic Server image for every server in every domain.                                                                                                                                                                                                                                                                                                                                                  | Requires a different image for each domain, but all servers in that domain use the same image. | Different domains can use the same image, but require different domainUID and may have different configuration.  |
| No state is kept in images making the containers created from these images completely throw away (cattle not pets).                                                                                                                                                                                                                                                                                                                     | Runtime state should not be kept in the images, but applications and configuration are. | Runtime state should not be kept in the images.  Application and configuration may be. |
| You can deploy new applications using the Administration Console or WLST.                                                                                                                                                                                                                                                                                                                                                               | If you want to deploy application updates, then you must create a new image. | If you want to deploy application updates, then you must create a new image, which optionally can be an [auxiliary image]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) that doesn't include a WebLogic installation. |
| You can use configuration overrides to mutate the domain configuration before it is deployed, but there are [limitations]({{< relref "/managing-domains/configoverrides/_index.md#unsupported-overrides" >}}).                                                                                                                                                                                                                          | Same as Domain on PV. | You can deploy model files to a ConfigMap to mutate the domain before it is deployed. The model file syntax is far simpler and less error prone than the configuration override syntax, and, unlike configuration overrides, allows you to directly add data sources and JMS modules. |
| You can change WebLogic domain configuration at runtime using the Administration Console or WLST. You can also change configuration overrides and [distribute the new overrides]({{< relref "/managing-domains/domain-lifecycle/introspection.md#distributing-changes-to-configuration-overrides" >}}) to running servers; however, non-dynamic configuration attributes can be changed only when servers are starting and some changes may require a full domain restart. |  You also can change configuration overrides and [distribute the new overrides]({{< relref "/managing-domains/domain-lifecycle/introspection.md#distributing-changes-to-configuration-overrides" >}}) to running servers; however, non-dynamic configuration attributes can be changed only when servers are starting and some changes may require a full domain restart. You should not use the Administration Console or WLST for these domains as changes are ephemeral and will be lost when servers restart. |  You can change configuration at runtime using model YAML file snippets supplied in [runtime updates]({{< relref "/managing-domains/model-in-image/runtime-updates.md" >}}) (which are substantially easier to specify than configuration overrides); however, non-dynamic configuration attributes will change only when servers are restarted (rolled) and some changes may require a full domain restart. You should not use the Administration Console or WLST for these domains as changes are ephemeral and will be lost when servers restart. |
| Logs are automatically placed on persistent storage and sent to the pod's stdout.                                                                                                                                                                                                                                                                                                                                                       | Logs are kept in the containers and sent to the pod's log (`stdout`) by default. To change the log location, you can set the Domain `logHomeEnabled` to true and configure the desired directory using `logHome`. | Same as Domain in Image.  |
| Patches can be applied by simply changing the image and rolling the domain.                                                                                                                                                                                                                                                                                                                                                             | To apply patches, you must update the domain-specific image and then restart or roll the domain depending on the nature of the patch.  | Same as Domain on PV when using dedicated [auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) to supply model artifacts; same as Domain in Image otherwise. |
| Many cloud providers do not provide persistent volumes that are shared across availability zones, so you may not be able to use a single persistent volume.  You may need to use some kind of volume replication technology or a clustered file system.                                                                                                                                                                                 | Provided you do not store and state in containers, you do not have to worry about volume replication across availability zones because each pod has its own copy of the domain.  WebLogic replication will handle propagation of any online configuration changes.  | Same as Domain in Image. |
| CI/CD pipelines may be more complicated because you would need to run WLST against the live domain directory to effect changes.                                                                                                                                                                                                                                                                                                         | CI/CD pipelines are simpler because you can create the whole domain in the image and don't have to worry about a persistent copy of the domain.  | CI/CD pipelines are even simpler because you don't need to generate a domain home. The operator will create a domain home for you based on the model that you supply. |
| There are fewer images to manage and store, which could provide significant storage and network savings.  |  There are more images to manage and store in this approach. | Same as Domain in Image unless you use the [auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}) approach. With auxiliary images, you can use a single image to distribute your WebLogic installation (similar to Domain on PV), plus one or more specific dedicated images that contain your WebLogic configuration and applications.|
| You may be able to use standard Oracle-provided images or, at least, a very small number of self-built images, for example, with patches installed.                                                                                                                                                                                                                                                                                     | You may need to do more work to set up processes to build and maintain your images. | Same as Domain in Image.|

### Use or create WebLogic images depending on domain home source type

* Model in Image domains that use [auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}):
  * [Understand Oracle Container Registry images]({{< relref "/base-images/ocr-images#understand-oracle-container-registry-images" >}})
  * [Obtain images from the Oracle Container Registry]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}})
  * [Create a custom image with patches applied]({{< relref "/base-images/custom-images#create-a-custom-image-with-patches-applied" >}})
  * [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}})

* Model in Image domains that _do not_ use auxiliary images:
**NOTE**: Model in Image without auxiliary images (the WDT model and installation files are included in the same image with the WebLogic Server installation) will be deprecated in WebLogic Kubernetes Operator version 4.0.7. Oracle recommends that you use Model in Image _with_ Auxiliary images. See [Auxiliary images]({{< relref "/managing-domains/model-in-image/auxiliary-images.md" >}}).

* Domain in Image domains: **NOTE**: The Domain in Image domain home source type is deprecated in WebLogic Kubernetes Operator version 4.0. Oracle recommends that you choose either Domain on PV or Model in Image, depending on your needs.
  * [Create a custom image with your domain inside the image]({{< relref "/base-images/custom-images#create-a-custom-image-with-your-domain-inside-the-image" >}})

* Domain on Persistent Volume (PV) domains:
  * [Understand Oracle Container Registry images]({{< relref "/base-images/ocr-images#understand-oracle-container-registry-images" >}})
  * [Obtain images from the Oracle Container Registry]({{< relref "/base-images/ocr-images#obtain-images-from-the-oracle-container-registry" >}})
  * [Create a custom image with patches applied]({{< relref "/base-images/custom-images#create-a-custom-image-with-patches-applied" >}})
  * [Domain creation images]({{< relref "/managing-domains/domain-on-pv/domain-creation-images.md" >}})
