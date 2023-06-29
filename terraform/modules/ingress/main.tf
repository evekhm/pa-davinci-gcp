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

# Terraform Block
terraform {
  required_providers {
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = ">= 1.14.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = ">= 2.7.0"
    }
    google = {
      source  = "hashicorp/google"
      version = "~>4.0"
    }
  }
}

locals {
  address = (
  var.external_address != null
  ? var.external_address
  : google_compute_address.ingress_ip_address[0].address
  )

}

module "cert_manager" {
  source = "terraform-iaac/cert-manager/kubernetes"

  cluster_issuer_email                   = var.cert_issuer_email
  cluster_issuer_name                    = "letsencrypt"
  cluster_issuer_private_key_secret_name = "cert-manager-private-key"
}

resource "kubernetes_namespace" "ingress_nginx" {
  metadata {
    name = "ingress-nginx"
  }
}

resource "google_compute_address" "ingress_ip_address" {
  count = var.external_address == null ? 1: 0
  name   = "nginx-controller"
  region = var.region
}


module "nginx-controller" {
  source    = "terraform-iaac/nginx-controller/helm"
  version   = "2.1.0"
  namespace = "ingress-nginx"

  ip_address = local.address

  # TODO: does this require cert_manager up and running or can they be completed in parallel
  depends_on = [
    module.cert_manager, kubernetes_namespace.ingress_nginx
  ]
}


resource "kubernetes_ingress_v1" "default_ingress" {
  depends_on = [
    module.nginx-controller
  ]
  metadata {
    name = "default-ingress"
    annotations = {
      "kubernetes.io/ingress.class"                        = "nginx"
      "cert-manager.io/cluster-issuer"                     = module.cert_manager.cluster_issuer_name
      "nginx.ingress.kubernetes.io/enable-cors"            = "true"
      "nginx.ingress.kubernetes.io/cors-allow-methods"     = "PUT,GET,POST,DELETE,OPTIONS"
      "nginx.ingress.kubernetes.io/cors-allow-origin"      = var.cors_allow_origin
      "nginx.ingress.kubernetes.io/cors-allow-credentials" = "true"
      "nginx.ingress.kubernetes.io/proxy-read-timeout"     = "3600"
      "nginx.ingress.kubernetes.io/use-regex" = "true"
      "nginx.ingress.kubernetes.io/rewrite-target" = "/$2"
    }
  }

  spec {
    rule {
      host = var.domain
      http {
        # crd-service
        path {
          backend {
            service {
              name = "test-ehr-service"
              port {
                number = 8080
              }
            }
          }
          path_type = "Prefix"
          path      = "/test-ehr(/|$)(.*)"
        }
        path {
          backend {
            service {
              name = "prior-auth-service"
              port {
                number = 9000
              }
            }
          }
          path_type = "Prefix"
          path      = "/prior-auth(/|$)(.*)"
        }
        path {
          backend {
            service {
              name = "crd-service"
              port {
                number = 8090
              }
            }
          }
          path_type = "Prefix"
          path      = "/crd(/|$)(.*)"
        }
        path {
          backend {
            service {
              name = "dtr-service"
              port {
                number = 3005
              }
            }
          }
          path_type = "Prefix"
          path      = "/dtr(/|$)(.*)"
        }
        path {
          backend {
            service {
              name = "auth-service"
              port {
                number = 80
              }
            }
          }
          path_type = "Prefix"
          path      = "/auth(/|$)(.*)"
        }
        path {
          backend {
            service {
              name = "crd-request-generator-service"
              port {
                number = 80
              }
            }
          }
          path_type = "Prefix"
          path      = "/emr(/|$)(.*)"
        }
        path {
          backend {
            service {
              name = "crd-request-generator-service"
              port {
                number = 3001
              }
            }
          }
          path_type = "Prefix"
          path      = "/emr/public_keys"
        }
      }
    }
    tls {
      hosts = [
        var.domain
      ]
      secret_name = "cert-manager-private-key"
    }
  }
}


//resource "kubernetes_ingress_v1" "external_ingress" {
//  count = var.external_ui == true ? 1 : 0
//  metadata {
//    name = "external-ingress"
//    annotations = {
//      "kubernetes.io/ingress.class"                 = "gce"
//      "kubernetes.io/ingress.global-static-ip-name" = local.external_ip_name
//      "networking.gke.io/managed-certificates"      = kubectl_manifest.managed_certificate[0].name
//      "networking.gke.io/v1beta1.FrontendConfig"    = kubectl_manifest.frontend_config[0].name
//    }
//  }
//
//  spec {
//    # Default backend to UI app.
////    default_backend {
////      service {
////        name = "test-ehr"
////        port {
////          number = 80
////        }
////      }
////    }
//
//    rule {
//      host = var.domain
//      http {
//        # Upload Service
//        path {
//          backend {
//            service {
//              name = "crd-service"
//              port {
//                number = 8090
//              }
//            }
//          }
//          path_type = "Prefix"
//          path      = "/crd"
//        }
//
////        path {
////          backend {
////            service {
////              name = "dtr"
////              port {
////                number = 80
////              }
////            }
////          }
////          path_type = "Prefix"
////          path      = "/dtr"
////        }
//
////        path {
////          backend {
////            service {
////              name = "prior-auth"
////              port {
////                number = 80
////              }
////            }
////          }
////          path_type = "Prefix"
////          path      = "/prior-auth"
////        }
////
////        path {
////          backend {
////            service {
////              name = "auth"
////              port {
////                number = 80
////              }
////            }
////          }
////          path_type = "Prefix"
////          path      = "/auth"
////        }
////
////
////        path {
////          backend {
////            service {
////              name = "emr"
////              port {
////                number = 80
////              }
////            }
////          }
////          path_type = "Prefix"
////          path      = "/emr"
////        }
//
//      }
//    }
//
//  }
//}
//
//# Internal Ingress
//
//resource "kubernetes_ingress_v1" "internal_ingress" {
//  count = var.external_ui == false ? 1 : 0
//  metadata {
//    name = "internal-ingress"
//    annotations = {
//      "kubernetes.io/ingress.class"                   = "gce-internal"
//      "kubernetes.io/ingress.regional-static-ip-name" = var.internal_ip_name
//    }
//  }
//
//  spec {
//    # Default backend to UI app.
//    default_backend {
//      service {
//        name = "test-ehr"
//        port {
//          number = 80
//        }
//      }
//    }
//
//    rule {
//      host = var.domain
//      http {
//        # Upload Service
//        path {
//          backend {
//            service {
//              name = "crd"
//              port {
//                number = 80
//              }
//            }
//          }
//          path_type = "Prefix"
//          path      = "/crd"
//        }
//
//        # classification Service
//        path {
//          backend {
//            service {
//              name = "dtr"
//              port {
//                number = 80
//              }
//            }
//          }
//          path_type = "Prefix"
//          path      = "/dtr"
//        }
//
//        # validation Service
//        path {
//          backend {
//            service {
//              name = "prior-auth"
//              port {
//                number = 80
//              }
//            }
//          }
//          path_type = "Prefix"
//          path      = "/prior-auth"
//        }
//
//        # extraction Service
//        path {
//          backend {
//            service {
//              name = "auth"
//              port {
//                number = 80
//              }
//            }
//          }
//          path_type = "Prefix"
//          path      = "/auth"
//        }
//
//        # document-status Service
//        path {
//          backend {
//            service {
//              name = "emr"
//              port {
//                number = 80
//              }
//            }
//          }
//          path_type = "Prefix"
//          path      = "/emr"
//        }
//      }
//    }
//
//  }
//}
