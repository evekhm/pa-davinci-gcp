# Table of contents

- [About](#about)
- [Pre-requisites](#pre-requisites)
  - [Access to Git Repo](#access-to-git-repo)
  - [Create new Project in Argolis](#create-new-project-in-argolis)
- [Installation](#installation)
  - [Setting up](#setting-up)
  - [Terraform](#terraform)
  - [Front End Config](#front-end-config)
  - [Enable Firebase Auth](#enable-firebase-auth)
  - [Deploy microservices](#deploy-microservices)
- [Out of the box UI demo flow](#out-of-the-box-ui-demo-flow)
- [Connecting to the existing DocAI processors](#connecting-to-the-existing-docai-processors)
  - [Granting Cross-Org permissions](#granting-cross-org-permissions)
  - [Modifying config.json file](#modifying-configjson-file)
  - [Testing New Configuration](#testing-new-configuration)
- [Troubleshooting](#troubleshooting)
  - [ERROR: This site can’t provide a secure](#error-this-site-cant-provide-a-secure)
## About

This is a customized version of installation Guide that installs CDA engine using Public End point and connects to the existing DocAI Processor Project.
For other flavours and more detailed steps, check the original full [README](../README.md).

## Pre-requisites

### Access to Git Repo
* Request access to [this](https://github.com/hcls-solutions/claims-data-activator) Git Repo by reaching out to [dharmeshpatel](dharmeshpatel@google.com) or [evekhm](evekhm@google.com).

### Create New Project in Argolis
* You will need access to Argolis environment and project owner rights.
* Installation will happen in the newly created Argolis project.

## Installation
### Setting up

* Get the Git Repo in the Cloud Shell:
```shell
git clone ...xxx
cd ...xxx
```

* Set env variable for _PROJECT_ID_:
```shell
export PROJECT_ID=<YOUR_PROJECT_ID>
``` 

```shell
gcloud config set project $PROJECT_ID
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
Build/deploy microservices (using skaffold + kustomize):
```shell
./deploy.sh
```
This command will take **~10 minutes** to complete, and it will take another **10-15 minutes** for ingress to get ready and for the cert to be provisioned.  

