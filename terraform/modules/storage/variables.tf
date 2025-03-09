variable "project_id" {
  description = "ID do projeto GCP"
  type        = string
}

variable "region" {
  description = "Região para recursos de armazenamento"
  type        = string
}

variable "environment" {
  description = "Ambiente (dev, staging, prod)"
  type        = string
}

variable "storage_bucket_name" {
  description = "Nome do bucket de armazenamento"
  type        = string
}

variable "storage_class" {
  description = "Classe de armazenamento do bucket"
  type        = string
  default     = "STANDARD"
}

variable "versioning_enabled" {
  description = "Habilitar versionamento de objetos"
  type        = bool
  default     = false
}

variable "labels" {
  description = "Labels a serem aplicadas aos recursos"
  type        = map(string)
  default     = {}
} 