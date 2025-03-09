/**
 * Módulo de Armazenamento
 * 
 * Este módulo cria recursos de armazenamento (Cloud Storage) para o projeto Urbana Connect.
 */

# Bucket principal de armazenamento
resource "google_storage_bucket" "main" {
  name          = var.storage_bucket_name
  project       = var.project_id
  location      = var.region
  storage_class = var.storage_class
  
  # Configurações de versionamento
  versioning {
    enabled = var.versioning_enabled
  }
  
  # Configurações de ciclo de vida
  lifecycle_rule {
    condition {
      age = 90  # 90 dias
    }
    action {
      type = "Delete"
    }
  }
  
  # Configurações de segurança
  uniform_bucket_level_access = true
  
  # Configurações de CORS para aplicações web
  cors {
    origin          = ["*"]
    method          = ["GET", "HEAD", "PUT", "POST", "DELETE"]
    response_header = ["*"]
    max_age_seconds = 3600
  }
  
  # Adicionar labels
  labels = var.labels
  
  # Evitar exclusão acidental em produção
  force_destroy = var.environment == "prod" ? false : true
}

# IAM para o bucket
resource "google_storage_bucket_iam_binding" "viewers" {
  bucket = google_storage_bucket.main.name
  role   = "roles/storage.objectViewer"
  
  members = [
    "allUsers",  # Público - remova em produção se não for necessário
  ]
}

# Pasta para uploads
resource "google_storage_bucket_object" "uploads_folder" {
  name    = "uploads/"
  content = " "  # Conteúdo vazio, apenas para criar a "pasta"
  bucket  = google_storage_bucket.main.name
}

# Pasta para backups
resource "google_storage_bucket_object" "backups_folder" {
  name    = "backups/"
  content = " "  # Conteúdo vazio, apenas para criar a "pasta"
  bucket  = google_storage_bucket.main.name
} 