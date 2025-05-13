output "kube_endpoint" {
  description = "O endpoint da API do Kubernetes."
  value       = digitalocean_kubernetes_cluster.primary.endpoint
}

output "cluster_id" {
  description = "O ID do cluster DOKS."
  value       = digitalocean_kubernetes_cluster.primary.id
}

output "cluster_ca_certificate" {
  description = "O certificado CA do cluster Kubernetes."
  value       = digitalocean_kubernetes_cluster.primary.kube_config[0].cluster_ca_certificate
  sensitive   = true
}

output "client_token" {
  description = "O token de cliente para autenticação no cluster."
  value       = digitalocean_kubernetes_cluster.primary.kube_config[0].token
  sensitive   = true
}

output "raw_kubeconfig" {
  description = "Kubeconfig completo para acesso ao cluster"
  value       = digitalocean_kubernetes_cluster.primary.kube_config[0].raw_config
  sensitive   = true
} 