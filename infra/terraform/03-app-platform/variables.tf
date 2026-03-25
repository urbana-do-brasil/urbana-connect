# Credenciais do container registry usadas para pull da imagem em homolog
variable "container_registry_server" {
  description = "Servidor do container registry"
  type        = string
  default     = "ghcr.io"
}

variable "container_registry_username" {
  description = "Usuário para autenticação no container registry"
  type        = string
  sensitive   = true
}

variable "container_registry_password" {
  description = "Token/senha para autenticação no container registry"
  type        = string
  sensitive   = true
}

variable "namespace_name" {
  description = "Nome do namespace da aplicação"
  type        = string
  default     = "urbana-connect-hml"
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
