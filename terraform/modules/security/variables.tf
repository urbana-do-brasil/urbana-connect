variable "project_id" {
  description = "ID do projeto GCP"
  type        = string
}

variable "environment" {
  description = "Ambiente (dev, staging, prod)"
  type        = string
}

variable "network_id" {
  description = "ID da rede VPC"
  type        = string
}

variable "gke_node_ips" {
  description = "Lista de IPs dos nós do GKE (para regras de firewall)"
  type        = list(string)
}

variable "labels" {
  description = "Labels a serem aplicadas aos recursos"
  type        = map(string)
  default     = {}
} 