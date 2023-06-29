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

  firestore_region   = var.firestore_region
  multiregion        = var.multiregion
  project_id         = var.project_id
  forms_gcs_path     = "${var.project_id}-pa-forms"
  bucket_name = var.bucket
  services = [
    "appengine.googleapis.com",            # AppEngine
    "artifactregistry.googleapis.com",     # Artifact Registry
    "cloudbuild.googleapis.com",           # Cloud Build
    "compute.googleapis.com",              # Load Balancers, Cloud Armor
    "container.googleapis.com",            # Google Kubernetes Engine
    "containerregistry.googleapis.com",    # Google Container Registry
    "firebase.googleapis.com",             # Firebase
    "iam.googleapis.com",                  # Cloud IAM
    "logging.googleapis.com",              # Cloud Logging
    "monitoring.googleapis.com",           # Cloud Operations Suite
    "run.googleapis.com",                  # Cloud Run
    "secretmanager.googleapis.com",        # Secret Manager
    "storage.googleapis.com",              # Cloud Storage
    "certificatemanager.googleapis.com",   # Certificate Manager
    "serviceusage.googleapis.com",         # Service Usage
    "vpcaccess.googleapis.com",            # VPC Access Connector
    "dns.googleapis.com",                  # Cloud DNS
    "iap.googleapis.com",                  # IAP
    "apigateway.googleapis.com",
    "cloudkms.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "iamcredentials.googleapis.com",
    "servicecontrol.googleapis.com",
    "servicemanagement.googleapis.com"
  ]


  shared_vpc_project = try(var.network_config.host_project, null)
  use_shared_vpc     = var.network_config != null
  region = (
    local.use_shared_vpc
    ? var.network_config.region
    : var.region
  )

  network_config = {
    host_project      = (local.use_shared_vpc ? var.network_config.host_project : var.project_id)
    network           = (local.use_shared_vpc ? var.network_config.network : var.network)
    subnet            = (local.use_shared_vpc ? var.network_config.subnet : var.subnetwork)
    serverless_subnet = (local.use_shared_vpc ? var.network_config.serverless_subnet : var.serverless_subnet)
    gke_secondary_ranges = {
      pods     = (local.use_shared_vpc ? var.network_config.gke_secondary_ranges.pods : var.secondary_ranges_pods.range_name)
      services = (local.use_shared_vpc ? var.network_config.gke_secondary_ranges.services : var.secondary_ranges_services.range_name)
    }
    region = (local.use_shared_vpc ? var.network_config.region : var.region)
  }

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
  count                     = local.use_shared_vpc ? 0 : 1
  source                    = "../../modules/vpc_network"
  project_id                = var.project_id
  vpc_network               = var.network
  region                    = var.region
  subnetwork                = var.subnetwork
  serverless_subnet         = var.serverless_subnet
  secondary_ranges_pods     = var.secondary_ranges_pods
  secondary_ranges_services = var.secondary_ranges_services
  master_cidr_ranges        = ["${var.master_ipv4_cidr_block}"]
  node_pools_tags           = ["gke-${var.cluster_name}"]
}

module "vpc_serverless_connector" {
  depends_on = [module.project_services, module.vpc_network]

  source             = "../../modules/vpc_serverless_connector"
  vpc_connector_name = var.vpc_connector_name
  project_id         = var.project_id
  region             = var.region
  subnet_name        = local.network_config.serverless_subnet
  host_project_id    = local.shared_vpc_project
}


module "gke" {
  depends_on = [module.project_services, module.vpc_network]

  source                    = "../../modules/gke"
  project_id                = var.project_id
  cluster_name              = var.cluster_name
  namespace                 = "default"
  vpc_network               = local.network_config.network
  vpc_subnetwork            = local.network_config.subnet
  network_project_id        = local.network_config.host_project
  secondary_ranges_pods     = local.network_config.gke_secondary_ranges.pods
  secondary_ranges_services = local.network_config.gke_secondary_ranges.services
  master_ipv4_cidr_block    = var.master_ipv4_cidr_block
  region                    = var.region
  min_node_count            = 1
  max_node_count            = 10
  machine_type              = "n1-standard-8"

  # This service account will be created in both GCP and GKE, and will be
  # used for workload federation in all microservices.
  # See microservices/sample_service/kustomize/base/deployment.yaml for example.
  service_account_name = var.service_account_name_gke

  # See latest stable version at https://cloud.google.com/kubernetes-engine/docs/release-notes-stable
  kubernetes_version = "1.24.13-gke.2500"

}


data "google_storage_project_service_account" "gcs_account" {
}


# ================= Storage buckets ====================

resource "google_storage_bucket" "default" {
  name                        = local.bucket_name
  location                    = local.multiregion
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  force_destroy               = true
  labels = {
    goog-packaged-solution = "prior-authorization"
  }
}

# give SA rights on bucket
resource "google_storage_bucket_iam_binding" "pa-sa_storage_output_binding" {
  bucket = google_storage_bucket.default.name
  role   = "roles/storage.admin"
  members = [
    "serviceAccount:${var.service_account_name_gke}@${var.project_id}.iam.gserviceaccount.com",
  ]
  depends_on = [
    google_storage_bucket.default,
    module.gke,
  ]
}


# ================= Validation Rules ====================

# Copying rules JSON files to GCS bucket.
resource "null_resource" "cds-library" {
  depends_on = [
    google_storage_bucket.default
  ]
  provisioner "local-exec" {
    command = "gsutil -m cp ../../../data/CDS-Library.zip gs://${google_storage_bucket.default.name}"
  }
}




