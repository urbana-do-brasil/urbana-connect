/**
 * Módulo de Segurança
 * 
 * Este módulo implementa recursos de segurança para o projeto Urbana Connect.
 */

# Service Account para aplicação
resource "google_service_account" "app_sa" {
  project      = var.project_id
  account_id   = "app-sa-${var.environment}"
  display_name = "Service Account para Aplicação - ${var.environment}"
  description  = "Service Account utilizada pela aplicação no ambiente ${var.environment}"
}

# Atribuir permissões necessárias para o Service Account da aplicação
resource "google_project_iam_member" "app_sa_roles" {
  for_each = toset([
    "roles/storage.objectViewer",
    "roles/cloudsql.client"
  ])
  
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.app_sa.email}"
}

# Regra de firewall para permitir acesso HTTP/HTTPS
resource "google_compute_firewall" "allow_http_https" {
  name    = "allow-http-https-${var.environment}"
  project = var.project_id
  network = var.network_id

  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }

  source_ranges = ["0.0.0.0/0"]
  description   = "Permite acesso HTTP/HTTPS de qualquer lugar"
  
  # Adicionar tag para aplicar apenas a instâncias específicas
  target_tags = ["http-server", "https-server"]
}

# Regra de firewall para permitir acesso ao banco de dados apenas dos nós GKE
resource "google_compute_firewall" "allow_db_from_gke" {
  name    = "allow-db-from-gke-${var.environment}"
  project = var.project_id
  network = var.network_id

  allow {
    protocol = "tcp"
    ports    = ["5432"]  # PostgreSQL
  }

  source_ranges = var.gke_node_ips
  description   = "Permite acesso ao banco de dados apenas dos nós GKE"
  
  # Adicionar tag para aplicar apenas a instâncias específicas
  target_tags = ["db-server"]
}

# Secret Manager para armazenar secrets
resource "google_secret_manager_secret" "db_credentials" {
  project   = var.project_id
  secret_id = "db-credentials-${var.environment}"
  
  replication {
    user_managed {
      replicas {
        location = "us-central1"
      }
    }
  }
}

# IAM para o Secret Manager
resource "google_secret_manager_secret_iam_binding" "secret_accessor" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.db_credentials.secret_id
  role      = "roles/secretmanager.secretAccessor"
  
  members = [
    "serviceAccount:${google_service_account.app_sa.email}"
  ]
} 