output "gke_cluster_name" {
  description = "Nome do cluster GKE"
  value       = google_container_cluster.primary.name
}

output "gke_cluster_id" {
  description = "ID do cluster GKE"
  value       = google_container_cluster.primary.id
}

output "gke_cluster_endpoint" {
  description = "Endpoint do cluster GKE"
  value       = google_container_cluster.primary.endpoint
  sensitive   = true
}

output "gke_cluster_ca_certificate" {
  description = "Certificado CA do cluster GKE"
  value       = base64decode(google_container_cluster.primary.master_auth[0].cluster_ca_certificate)
  sensitive   = true
}

output "gke_node_pool_name" {
  description = "Nome do node pool do GKE"
  value       = google_container_node_pool.primary_nodes.name
}

output "gke_service_account" {
  description = "Service Account utilizada pelo GKE"
  value       = google_service_account.gke_sa.email
}

output "gke_kubeconfig" {
  description = "Comando para obter o kubeconfig do cluster GKE"
  value       = "gcloud container clusters get-credentials ${google_container_cluster.primary.name} --zone ${var.zone} --project ${var.project_id}"
  sensitive   = true
}

output "gke_node_ips" {
  description = "Lista de IPs dos nós do GKE (para regras de firewall)"
  value       = ["0.0.0.0/0"]  # Simplificação - em produção, use os IPs reais dos nós
} 