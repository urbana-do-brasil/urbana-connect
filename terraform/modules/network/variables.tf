variable "project_id" {
  description = "ID do projeto GCP"
  type        = string
}

variable "region" {
  description = "Região para recursos de rede"
  type        = string
}

variable "environment" {
  description = "Ambiente (dev, staging, prod)"
  type        = string
}

variable "network_name" {
  description = "Nome da VPC"
  type        = string
}

variable "subnet_cidr" {
  description = "CIDR da subnet principal"
  type        = string
}

variable "labels" {
  description = "Labels a serem aplicadas aos recursos"
  type        = map(string)
  default     = {}
} 