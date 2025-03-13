terraform {
  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.30.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11.0"
    }
  }
  
  required_version = ">= 1.0.0"
}

provider "digitalocean" {
  token = var.do_token
}

# Configuração do provider Kubernetes que será ativada após criação do cluster
provider "kubernetes" {
  host                   = var.configure_kubernetes ? digitalocean_kubernetes_cluster.urbana_connect.endpoint : ""
  token                  = var.configure_kubernetes ? digitalocean_kubernetes_cluster.urbana_connect.kube_config[0].token : ""
  cluster_ca_certificate = var.configure_kubernetes ? base64decode(digitalocean_kubernetes_cluster.urbana_connect.kube_config[0].cluster_ca_certificate) : ""
}

# Configuração do provider Helm que será ativada após criação do cluster
provider "helm" {
  kubernetes {
    host                   = var.configure_kubernetes ? digitalocean_kubernetes_cluster.urbana_connect.endpoint : ""
    token                  = var.configure_kubernetes ? digitalocean_kubernetes_cluster.urbana_connect.kube_config[0].token : ""
    cluster_ca_certificate = var.configure_kubernetes ? base64decode(digitalocean_kubernetes_cluster.urbana_connect.kube_config[0].cluster_ca_certificate) : ""
  }
}
