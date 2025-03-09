variable "project_id" {
  description = "ID do projeto GCP"
  type        = string
}

variable "region" {
  description = "Região padrão para recursos GCP"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "Zona padrão para recursos GCP"
  type        = string
  default     = "us-central1-a"
}

variable "environment" {
  description = "Ambiente atual (dev, staging, prod)"
  type        = string
  default     = "staging"
}

# Variáveis para o módulo de rede
variable "network_name" {
  description = "Nome da VPC"
  type        = string
  default     = "urbana-connect-vpc-staging"
}

variable "subnet_cidr" {
  description = "CIDR da subnet principal"
  type        = string
  default     = "10.1.0.0/24"
}

# Variáveis para o módulo de computação (GKE)
variable "gke_cluster_name" {
  description = "Nome do cluster GKE"
  type        = string
  default     = "urbana-connect-cluster-staging"
}

variable "gke_node_count" {
  description = "Número de nós no cluster GKE"
  type        = number
  default     = 2
}

variable "gke_machine_type" {
  description = "Tipo de máquina para os nós do GKE"
  type        = string
  default     = "e2-small"
}

# Variáveis para o módulo de banco de dados
variable "db_instance_name" {
  description = "Nome da instância do Cloud SQL"
  type        = string
  default     = "urbana-connect-db-staging"
}

variable "db_tier" {
  description = "Tier da instância do Cloud SQL"
  type        = string
  default     = "db-f1-micro"
}

variable "db_name" {
  description = "Nome do banco de dados"
  type        = string
  default     = "urbana_connect_staging"
}

variable "db_user" {
  description = "Usuário do banco de dados"
  type        = string
  default     = "urbana_connect_user"
}

# Variáveis para o módulo de armazenamento
variable "storage_bucket_name" {
  description = "Nome do bucket de armazenamento"
  type        = string
  default     = "urbana-connect-storage-staging"
} 