# Declaração da variável para o token de acesso da DigitalOcean (utilizado para o secret do registry)
variable "DIGITALOCEAN_ACCESS_TOKEN" {
  description = "Token de acesso da DigitalOcean para autenticação no registry"
  type        = string
  sensitive   = true
}

variable "namespace_name" {
  description = "Nome do namespace da aplicação"
  type        = string
  default     = "urbana-connect"
}

variable "domain" {
  description = "Domínio principal da aplicação"
  type        = string
  default     = "urbanadobrasil.com"
}

variable "email" {
  description = "Email para configuração do Let's Encrypt"
  type        = string
  default     = "emanuel.guimaraes@urbanadobrasil.com"
} 