variable "nginx_ingress_version" {
  description = "Versão do chart Helm para o Nginx Ingress Controller"
  type        = string
  default     = "4.7.1"
}

variable "cert_manager_version" {
  description = "Versão do chart Helm para o Cert Manager"
  type        = string
  default     = "v1.13.1"
} 