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

variable "project_id" {
  type        = string
  description = "GCP Project ID"

  validation {
    condition     = length(var.project_id) > 0
    error_message = "The project_id value must be an non-empty string."
  }
}

variable "region" {
  type        = string
  description = "GCP Region"
  default     = "us-central1"

  validation {
    condition     = length(var.region) > 0
    error_message = "The region value must be an non-empty string."
  }
}

variable "firestore_region" {
  type        = string
  description = "Firestore Region"
  default     = "us-central"
}


variable "multiregion" {
  type    = string
  default = "us"
}

variable "env" {
  type    = string
  default = "dev"
}

variable "cluster_name" {
  type    = string
  default = "pa-davinci-cluster"
}

variable "gke_sa_name" {
  type    = string
  default = "gke-sa"
  description = "Service Name for GKE workload Identity"
}

variable "network" {
  type    = string
  default = "pa-vpc"
}

variable "subnetwork" {
  type    = string
  default = "pa-subnetwork"
}

#adding new variables for the updated scripts

variable "project_name" {
  type        = string
  default     = ""
  description = "This project name"
}

variable "org_id" {
  default     = ""
  description = "This project organization id"
}

variable "firebase_init" {
  default     = false
  description = "Whether to run Firebase init resource"
}


variable "admin_email" {
  type        = string
  description = "email of the cert issuer"
}

variable "api_domain" {
  type        = string
  description = "api domain name"
}

variable "cds_lib_bucket_name" {
  type        = string
  description = "Bucket to store CDS-libraries"
}