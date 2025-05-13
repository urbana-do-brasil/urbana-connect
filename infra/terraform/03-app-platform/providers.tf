terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
  }
  required_version = ">= 1.3"

  cloud {
    organization = "urbana-do-brasil"

    workspaces {
      name = "urbana-connect-app-platform"
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

# Obtém os dados do módulo de kube-addons
data "terraform_remote_state" "kube_addons" {
  backend = "remote"

  config = {
    organization = "urbana-do-brasil"
    workspaces = {
      name = "urbana-connect-kube-addons"
    }
  }
}

# Configura o provider Kubernetes usando os outputs do módulo do cluster
provider "kubernetes" {
  host                   = data.terraform_remote_state.cluster.outputs.kube_endpoint
  token                  = data.terraform_remote_state.cluster.outputs.client_token
  cluster_ca_certificate = base64decode(data.terraform_remote_state.cluster.outputs.cluster_ca_certificate)
} 