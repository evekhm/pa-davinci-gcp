/**
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

# project-specific locals
locals {
  env = var.env
  region             = var.region
  firestore_region   = var.firestore_region
  multiregion        = var.multiregion
  project_id         = var.project_id
  config_bucket_name = var.cds_lib_bucket_name
  services = [
    "appengine.googleapis.com",            # AppEngine
    "artifactregistry.googleapis.com",     # Artifact Registry
    "cloudbuild.googleapis.com",           # Cloud Build
    "compute.googleapis.com",              # Load Balancers, Cloud Armor
    "container.googleapis.com",            # Google Kubernetes Engine
    "containerregistry.googleapis.com",    # Google Container Registry
    "dataflow.googleapis.com",             # Cloud Dataflow
    "firebase.googleapis.com",             # Firebase
    "firestore.googleapis.com",            # Firestore
    "iam.googleapis.com",                  # Cloud IAM
    "logging.googleapis.com",              # Cloud Logging
    "monitoring.googleapis.com",           # Cloud Operations Suite
    "run.googleapis.com",                  # Cloud Run
    "secretmanager.googleapis.com",        # Secret Manager
    "storage.googleapis.com",              # Cloud Storage
  ]
}

data "google_project" "project" {}

module "project_services" {
  source     = "../../modules/project_services"
  project_id = var.project_id
  services   = local.services
}

module "service_accounts" {
  depends_on = [module.project_services]
  source     = "../../modules/service_accounts"
  project_id = var.project_id
  env        = var.env
}

resource "time_sleep" "wait_for_project_services" {
  depends_on = [
    module.project_services,
    module.service_accounts
  ]
  create_duration = "60s"
}

module "firebase" {
  depends_on       = [time_sleep.wait_for_project_services]
  source           = "../../modules/firebase"
  project_id       = var.project_id
  firestore_region = var.firestore_region
}

module "vpc_network" {
  source      = "../../modules/vpc_network"
  project_id  = var.project_id
  vpc_network = "default-vpc"
  region      = var.region
}

module "gke" {
  depends_on = [module.project_services, module.vpc_network]

  source         = "../../modules/gke"
  project_id     = var.project_id
  cluster_name   = var.cluster_name
  namespace      = "default"
  vpc_network    = module.vpc_network
  region         = var.region
  min_node_count = 1
  max_node_count = 10
  machine_type   = "n1-standard-8"

  # This service account will be created in both GCP and GKE, and will be
  # used for workload federation in all microservices.
  # See microservices/sample_service/kustomize/base/deployment.yaml for example.
  service_account_name = var.gke_sa_name

  # See latest stable version at https://cloud.google.com/kubernetes-engine/docs/release-notes-stable
  kubernetes_version = "1.23.13-gke.900"
}

module "ingress" {
  depends_on        = [module.gke]
  source            = "../../modules/ingress"
  project_id        = var.project_id
  cert_issuer_email = var.admin_email

  # Domains for API endpoint, excluding protocols.
  domain            = var.api_domain
  region            = var.region
  cors_allow_origin = "http://localhost:4200,http://localhost:3000,http://${var.api_domain},https://${var.api_domain}"
}


# ================= Storage buckets ====================

resource "google_storage_bucket" "default" {
  name                        = local.project_id
  location                    = local.multiregion
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  labels = {
    goog-packaged-solution = "prior-authorization"
  }
}

# Bucket to store CDS Library
resource "google_storage_bucket" "cds-lib" {
  name                        = local.config_bucket_name
  location                    = local.multiregion
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  versioning {
    enabled = true
  }
  labels = {
    goog-packaged-solution = "prior-authorization"
  }
}


# ================= CDS Library with CQL Rules ====================

# Copying rules files to GCS bucket.
resource "null_resource" "validation_rules" {
  depends_on = [
    google_storage_bucket.cds-lib
  ]
  provisioner "local-exec" {
    command = "gsutil -m cp ../../../data/CDS-Library/* gs://${var.cds_lib_bucket_name}/CDS-Library/"
  }
}

