variable "project_id" {
  description = "ID do projeto GCP"
  type        = string
}

variable "region" {
  description = "Região para recursos de computação"
  type        = string
}

variable "zone" {
  description = "Zona para recursos de computação"
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

variable "subnet_id" {
  description = "ID da subnet"
  type        = string
}

variable "gke_cluster_name" {
  description = "Nome do cluster GKE"
  type        = string
}

variable "gke_node_count" {
  description = "Número de nós no cluster GKE"
  type        = number
  default     = 1
}

variable "gke_machine_type" {
  description = "Tipo de máquina para os nós do GKE"
  type        = string
  default     = "e2-small"
}

variable "labels" {
  description = "Labels a serem aplicadas aos recursos"
  type        = map(string)
  default     = {}
} 