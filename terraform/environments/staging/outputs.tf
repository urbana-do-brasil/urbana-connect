output "network_id" {
  description = "ID da rede VPC"
  value       = module.network.network_id
}

output "subnet_id" {
  description = "ID da subnet principal"
  value       = module.network.subnet_id
}

output "gke_cluster_name" {
  description = "Nome do cluster GKE"
  value       = module.compute.gke_cluster_name
}

output "gke_cluster_endpoint" {
  description = "Endpoint do cluster GKE"
  value       = module.compute.gke_cluster_endpoint
  sensitive   = true
}

output "gke_kubeconfig" {
  description = "Configuração kubeconfig para o cluster GKE"
  value       = module.compute.gke_kubeconfig
  sensitive   = true
}

output "db_instance_connection_name" {
  description = "Nome de conexão da instância do Cloud SQL"
  value       = module.database.db_instance_connection_name
}

output "db_instance_ip" {
  description = "Endereço IP da instância do Cloud SQL"
  value       = module.database.db_instance_ip
  sensitive   = true
}

output "storage_bucket_url" {
  description = "URL do bucket de armazenamento"
  value       = module.storage.storage_bucket_url
} 