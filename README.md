
# Table of contents


- [Table of contents](#table-of-contents)
  * [Pre-requisites](#pre-requisites)
    + [Running from the development machine](#running-from-the-development-machine)
    + [VSAC Key](#vsac-key)
  * [Consumed Resources <a name="resources"></a>](#consumed-resources)
    + [Deploy microservices](#deploy-microservices)
  * [Run the DRLS Flow](#run-the-drls-flow)



This is a customized version of the original Documentation Requirements Lookup Service (DRLS) that deploys all applications as microservices on GKE.
For the original instructions and in-depth explanations, refer to [DaVinci GitHub page](https://github.com/HL7-DaVinci/CRD/blob/master/SetupGuideForMacOS.md) 

## Pre-requisites
### Running from the development machine
Cloud Shell will timeout on the deployment command and therefore development machine is required to build and deploy applications.

### VSAC Key
Additionally, you must have credentials (api key) access to the **[Value Set Authority Center (VSAC)](https://vsac.nlm.nih.gov/)**. These credentials are required for allowing DRLS to pull down updates to value sets that are housed in VSAC. If you don't already have VSAC credentials, you should [create them using UMLS](https://www.nlm.nih.gov/research/umls/index.html).


## Consumed Resources <a name="resources"></a>
Each deployed Prior-Auth Solution consumes following resources:
- [GKE](https://cloud.google.com/kubernetes-engine/pricing) Autopilot cluster:
  - 6 deployed services/workloads
  - 6 GKE Ingress objects setup with NEG ([Container native LB*](https://cloud.google.com/kubernetes-engine/docs/how-to/container-native-load-balancing))
    - 6 GCE_VM_IP_PORT NEGs (zonal NEGs)
  - GCP Cloud Storage
- [Network Premium Tier](https://cloud.google.com/network-tiers/pricing):
  - 1 VPC Network
  - 6 [Global external HTTP(S) Load Balancers (classic)](https://cloud.google.com/load-balancing/docs/https)
    - 12 [URL maps](https://cloud.google.com/load-balancing/docs/url-map) (HTTP+HTTPS for each service)
    - 6 Reserved static IP addresses (used for the EndPoints to provide domain name)
    - 6 Google managed SSL Global Certificates
    - 6 Global Target HTTP proxies; 6 Global Target HTTPS(s) proxies

*Container-native load balancing enables load balancers to target Pods directly and to make load distribution decisions at the Pod-level instead of at the VM-level.


## Installation
* Create new project on GCP
* Set env variable for _PROJECT_ID_:
```shell
export PROJECT_ID=<YOUR_PROJECT_ID>
``` 

```shell
gcloud config set project $PROJECT_ID
```


* Set VSAC key:
> At this point, you should have credentials to access VSAC. If not, please refer to [Prerequisites](#prerequisites) for how to create these credentials and return here after you have confirmed you can access VSAC.
> To download the full ValueSets, your VSAC account will need to be added to the CMS-DRLS author group on https://vsac.nlm.nih.gov/. You will need to request membership access from an admin. If this is not configured, you will get `org.hl7.davinci.endpoint.vsac.errors.VSACValueSetNotFoundException: ValueSet 2.16.840.1.113762.1.4.1219.62 Not Found` errors.

Use your *vsac_api_key* to set VSAC credentials (otherwise the flow will not work):

```sh
  export VSAC_API_KEY=<YOUR_VSAC_API_KEY>
```

* Run **init** step to provision required resources in GCP (will run terraform apply with auto-approve):
```shell
bash -e ./init.sh
```
This command will take **~15 minutes** to complete.
After successfully execution, you should see line like this at the end:

```shell
<...> Completed! Saved Log into /<...>/init.log
```

### Deploy microservices
* Build/deploy microservices (using skaffold + kustomize):
```shell
bash -e ./deploy.sh
```
This command will take **~45 minutes** to complete (due to build time and time required to deploy an end point).

It might take up to 30 minutes for managed certificates to get provisioned, before system becomes functional.
You could check the status by running following command:

```shell
kubectl get managedcertificates
```

Sample Output:
```shell
NAME                                        AGE   STATUS
auth-service-certificate                    43m   Provisioning
crd-request-generator-service-certificate   28m   Active
crd-service-certificate                     40m   Provisioning
dtr-service-certificate                     37m   Provisioning
prior-auth-service-certificate              34m   Active
test-ehr-service-certificate                31m   Active
```

When All are in `Active` Status, system is ready to be used. 


## Run the DRLS Flow
1. Get the emr deploy address:
```shell
gcloud endpoints services list --filter="TITLE=( crd-request-generator-service )" --format "list(NAME)"
```
2. Go to the address retrieved above in the web browser.
5. Click **Patient Select** button in upper left.
6. Find **William Oster** in the list of patients and click the dropdown menu next to his name.
7. Select **E0470** in the dropdown menu.
8. Click anywhere in the row for William Oster.
9. Click **Submit** at the bottom of the page.
10. After several seconds you should receive a response in the form of two **CDS cards**:
  - **Respiratory Assist Device**
  - **Positive Airway Pressure Device**
11. Select **Order Form** on one of those CDS cards.
12. If you are asked for login credentials, use **alice** for username and **alice** for password.
13. A webpage should open in a new tab, and after a few seconds, a questionnaire should appear.

Congratulations! DRLS is fully installed and ready for you to use!