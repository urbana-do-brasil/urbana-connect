/**
 * Módulo de Computação (GKE)
 * 
 * Este módulo cria um cluster GKE para o projeto Urbana Connect.
 * Configurado para usar o nível gratuito do GKE quando possível.
 */

# Service Account para o GKE
resource "google_service_account" "gke_sa" {
  project      = var.project_id
  account_id   = "gke-sa-${var.environment}"
  display_name = "Service Account para GKE - ${var.environment}"
  description  = "Service Account utilizada pelo GKE no ambiente ${var.environment}"
}

# Atribuir permissões necessárias para o Service Account
resource "google_project_iam_member" "gke_sa_roles" {
  for_each = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
    "roles/storage.objectViewer"
  ])
  
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.gke_sa.email}"
}

# Cluster GKE
resource "google_container_cluster" "primary" {
  name     = var.gke_cluster_name
  project  = var.project_id
  location = var.zone
  
  # Usar VPC e subnet existentes
  network    = var.network_id
  subnetwork = var.subnet_id
  
  # Remover o node pool padrão e criar um personalizado
  remove_default_node_pool = true
  initial_node_count       = 1
  
  # Configurações de rede
  networking_mode = "VPC_NATIVE"
  ip_allocation_policy {
    # Configuração automática de ranges de IP para pods e serviços
  }
  
  # Configurações de segurança
  master_auth {
    client_certificate_config {
      issue_client_certificate = false
    }
  }
  
  # Configurações de controle de acesso
  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = false
    master_ipv4_cidr_block  = "172.16.0.0/28"
  }
  
  # Configurações de release channel
  release_channel {
    channel = "REGULAR"
  }
  
  # Configurações de manutenção
  maintenance_policy {
    daily_maintenance_window {
      start_time = "03:00"
    }
  }
  
  # Adicionar labels
  resource_labels = var.labels
}

# Node Pool para o GKE
resource "google_container_node_pool" "primary_nodes" {
  name       = "${var.gke_cluster_name}-node-pool"
  project    = var.project_id
  location   = var.zone
  cluster    = google_container_cluster.primary.name
  node_count = var.gke_node_count
  
  # Configuração de auto-scaling (opcional, desativado para economizar recursos)
  # autoscaling {
  #   min_node_count = 1
  #   max_node_count = 3
  # }
  
  # Configuração de gerenciamento de nós
  management {
    auto_repair  = true
    auto_upgrade = true
  }
  
  # Configuração dos nós
  node_config {
    machine_type = var.gke_machine_type
    disk_size_gb = 10
    disk_type    = "pd-standard"
    
    # Usar service account criada
    service_account = google_service_account.gke_sa.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
    
    # Adicionar labels aos nós
    labels = var.labels
    
    # Adicionar tags de rede
    tags = ["gke-node", "urbana-connect-${var.environment}"]
  }
  
  # Ignorar mudanças em certos atributos para evitar problemas de atualização
  lifecycle {
    ignore_changes = [
      node_config[0].resource_labels,
      node_config[0].kubelet_config,
      node_config[0].labels,
      timeouts
    ]
  }
} 