output "cluster_id" {
  description = "ID of the Kubernetes cluster"
  value       = digitalocean_kubernetes_cluster.urbana_connect.id
}

output "cluster_name" {
  description = "Name of the Kubernetes cluster"
  value       = digitalocean_kubernetes_cluster.urbana_connect.name
}

output "kubernetes_endpoint" {
  description = "Endpoint for the Kubernetes API"
  value       = digitalocean_kubernetes_cluster.urbana_connect.endpoint
  sensitive   = true
}

output "kubeconfig_path" {
  description = "Path to the kubeconfig file"
  value       = "~/.kube/config"
}

output "cluster_status" {
  description = "Status of the cluster"
  value       = digitalocean_kubernetes_cluster.urbana_connect.status
}

output "node_count" {
  description = "Number of nodes in the cluster"
  value       = var.node_count
}
