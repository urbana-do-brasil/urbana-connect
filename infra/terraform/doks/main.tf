terraform {
  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.30"
    }
  }
  required_version = ">= 1.3"

  cloud {
    organization = "urbana-do-brasil"

    workspaces {
      name = "urbana-connect-doks"
    }
  }
}

provider "digitalocean" {
}

variable "do_region" {
  description = "Região da DigitalOcean para o cluster DOKS"
  type        = string
  default     = "nyc3"
}

variable "cluster_name" {
  description = "Nome do cluster Kubernetes"
  type        = string
  default     = "urbana-connect-cluster"
}

resource "digitalocean_kubernetes_cluster" "primary" {
  name    = var.cluster_name
  region  = var.do_region
  version = "1.29.1-do.0"

  node_pool {
    name       = "default-pool"
    size       = "s-2vcpu-2gb"
    node_count = 1
  }

}

output "kube_endpoint" {
  description = "O endpoint da API do Kubernetes."
  value       = digitalocean_kubernetes_cluster.primary.endpoint
}

output "cluster_id" {
  description = "O ID do cluster DOKS."
  value       = digitalocean_kubernetes_cluster.primary.id
} 