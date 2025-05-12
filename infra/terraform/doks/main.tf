terraform {
  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.30"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
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

# Declaração da variável para o token de acesso da DigitalOcean
variable "DIGITALOCEAN_ACCESS_TOKEN" {
  description = "Token de acesso da DigitalOcean"
  type        = string
  sensitive   = true
}

provider "digitalocean" {
  token = var.DIGITALOCEAN_ACCESS_TOKEN
}

# Novos providers para Kubernetes e Helm
provider "kubernetes" {
  host                   = digitalocean_kubernetes_cluster.primary.endpoint
  token                  = digitalocean_kubernetes_cluster.primary.kube_config[0].token
  cluster_ca_certificate = base64decode(digitalocean_kubernetes_cluster.primary.kube_config[0].cluster_ca_certificate)
}

provider "helm" {
  kubernetes {
    host                   = digitalocean_kubernetes_cluster.primary.endpoint
    token                  = digitalocean_kubernetes_cluster.primary.kube_config[0].token
    cluster_ca_certificate = base64decode(digitalocean_kubernetes_cluster.primary.kube_config[0].cluster_ca_certificate)
  }
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

# Data source para buscar as versões K8s disponíveis/recomendadas
data "digitalocean_kubernetes_versions" "latest" {
  # Você pode adicionar version_prefix se quiser uma versão específica major.minor, ex: "1.28"
  # version_prefix = "1.29" 
}

resource "digitalocean_kubernetes_cluster" "primary" {
  name    = var.cluster_name
  region  = var.do_region
  # Usar a versão mais recente recomendada obtida pelo data source
  version = data.digitalocean_kubernetes_versions.latest.latest_version

  node_pool {
    name       = "default-pool"
    size       = "s-1vcpu-2gb"
    node_count = 2
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