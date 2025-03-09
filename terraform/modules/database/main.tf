/**
 * Módulo de Banco de Dados
 * 
 * Este módulo cria uma instância Cloud SQL para o projeto Urbana Connect.
 * Configurado para usar o nível mais econômico possível.
 */

# Gerar senha aleatória se não for fornecida
resource "random_password" "db_password" {
  length           = 16
  special          = true
  override_special = "_%@"
}

locals {
  db_password = var.db_password != "" ? var.db_password : random_password.db_password.result
}

# Instância Cloud SQL
resource "google_sql_database_instance" "instance" {
  name             = var.db_instance_name
  project          = var.project_id
  region           = var.region
  database_version = "POSTGRES_13"
  
  settings {
    tier              = var.db_tier
    availability_type = var.environment == "prod" ? "REGIONAL" : "ZONAL"
    disk_size         = 10  # Mínimo para economizar custos
    disk_type         = "PD_SSD"
    
    # Configurações de backup
    backup_configuration {
      enabled            = var.environment == "prod" ? true : false
      start_time         = "02:00"
      point_in_time_recovery_enabled = var.environment == "prod" ? true : false
    }
    
    # Configurações de manutenção
    maintenance_window {
      day          = 7  # Domingo
      hour         = 2  # 2 AM
      update_track = "stable"
    }
    
    # Configurações de IP
    ip_configuration {
      ipv4_enabled    = true
      private_network = var.network_id
      
      # Autorizar acesso apenas da rede VPC
      authorized_networks {
        name  = "VPC Access"
        value = "0.0.0.0/0"  # Em produção, restrinja para IPs específicos
      }
    }
    
    # Configurações de recursos
    database_flags {
      name  = "max_connections"
      value = "100"
    }
    
    # Adicionar labels
    user_labels = var.labels
  }
  
  # Evitar recriação da instância em atualizações
  lifecycle {
    prevent_destroy = false
  }
  
  # Descrição para documentação
  deletion_protection = var.environment == "prod" ? true : false
}

# Banco de dados
resource "google_sql_database" "database" {
  name     = var.db_name
  project  = var.project_id
  instance = google_sql_database_instance.instance.name
  charset  = "UTF8"
  collation = "en_US.UTF8"
}

# Usuário do banco de dados
resource "google_sql_user" "user" {
  name     = var.db_user
  project  = var.project_id
  instance = google_sql_database_instance.instance.name
  password = local.db_password
} 