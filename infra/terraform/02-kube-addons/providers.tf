terraform {
  required_providers {
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
      name = "urbana-connect-kube-addons"
    }
  }
}

# Obtém os dados do módulo do cluster DOKS
data "terraform_remote_state" "cluster" {
  backend = "remote"

  config = {
    organization = "urbana-do-brasil"
    workspaces = {
      name = "urbana-connect-doks-cluster"
    }
  }
}

# Configura os providers para Kubernetes e Helm usando os outputs do módulo anterior
provider "kubernetes" {
  host                   = data.terraform_remote_state.cluster.outputs.kube_endpoint
  token                  = data.terraform_remote_state.cluster.outputs.client_token
  cluster_ca_certificate = base64decode(data.terraform_remote_state.cluster.outputs.cluster_ca_certificate)
}

provider "helm" {
  kubernetes {
    host                   = data.terraform_remote_state.cluster.outputs.kube_endpoint
    token                  = data.terraform_remote_state.cluster.outputs.client_token
    cluster_ca_certificate = base64decode(data.terraform_remote_state.cluster.outputs.cluster_ca_certificate)
  }
} 