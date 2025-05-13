# Declaração da variável para o token de acesso da DigitalOcean
variable "DIGITALOCEAN_ACCESS_TOKEN" {
  description = "Token de acesso da DigitalOcean"
  type        = string
  sensitive   = true
}

variable "do_region" {
  description = "Região da DigitalOcean para o cluster DOKS"
  type        = string
  default     = "nyc3"
}

variable "cluster_name" {
  description = "Nome do cluster Kubernetes"
  type        = string
  default     = "urbana-connect-cluster"
} 