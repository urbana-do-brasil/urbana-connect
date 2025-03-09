/**
 * Módulo de Rede
 * 
 * Este módulo cria uma VPC e subnet para o projeto Urbana Connect.
 */

# Criação da VPC
resource "google_compute_network" "vpc" {
  name                    = var.network_name
  project                 = var.project_id
  auto_create_subnetworks = false
  description             = "VPC para o projeto Urbana Connect - ${var.environment}"
}

# Criação da subnet principal
resource "google_compute_subnetwork" "subnet" {
  name          = "${var.network_name}-subnet"
  project       = var.project_id
  region        = var.region
  network       = google_compute_network.vpc.id
  ip_cidr_range = var.subnet_cidr
  
  # Habilitar Private Google Access
  private_ip_google_access = true
  
  # Habilitar logs de fluxo para melhor observabilidade
  log_config {
    aggregation_interval = "INTERVAL_5_SEC"
    flow_sampling        = 0.5
    metadata             = "INCLUDE_ALL_METADATA"
  }
  
  description = "Subnet principal para o projeto Urbana Connect - ${var.environment}"
}

# Regra de firewall para permitir comunicação interna
resource "google_compute_firewall" "allow_internal" {
  name    = "${var.network_name}-allow-internal"
  project = var.project_id
  network = google_compute_network.vpc.name

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
  }

  allow {
    protocol = "udp"
  }

  source_ranges = [var.subnet_cidr]
  description   = "Permite comunicação interna na VPC"
}

# Regra de firewall para permitir acesso SSH
resource "google_compute_firewall" "allow_ssh" {
  name    = "${var.network_name}-allow-ssh"
  project = var.project_id
  network = google_compute_network.vpc.name

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  # Em produção, restrinja para IPs específicos
  source_ranges = ["0.0.0.0/0"]
  description   = "Permite acesso SSH de qualquer lugar (restringir em produção)"
  
  # Adicionar tag para aplicar apenas a instâncias específicas
  target_tags = ["ssh-allowed"]
} 