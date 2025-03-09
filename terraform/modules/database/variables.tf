variable "project_id" {
  description = "ID do projeto GCP"
  type        = string
}

variable "region" {
  description = "Região para recursos de banco de dados"
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

variable "db_instance_name" {
  description = "Nome da instância do Cloud SQL"
  type        = string
}

variable "db_tier" {
  description = "Tier da instância do Cloud SQL"
  type        = string
  default     = "db-f1-micro"
}

variable "db_name" {
  description = "Nome do banco de dados"
  type        = string
}

variable "db_user" {
  description = "Usuário do banco de dados"
  type        = string
}

variable "db_password" {
  description = "Senha do banco de dados"
  type        = string
  default     = ""  # Será gerada automaticamente se não for fornecida
  sensitive   = true
}

variable "labels" {
  description = "Labels a serem aplicadas aos recursos"
  type        = map(string)
  default     = {}
} 