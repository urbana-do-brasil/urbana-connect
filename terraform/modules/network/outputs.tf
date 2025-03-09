output "network_id" {
  description = "ID da rede VPC criada"
  value       = google_compute_network.vpc.id
}

output "network_name" {
  description = "Nome da rede VPC criada"
  value       = google_compute_network.vpc.name
}

output "subnet_id" {
  description = "ID da subnet principal criada"
  value       = google_compute_subnetwork.subnet.id
}

output "subnet_name" {
  description = "Nome da subnet principal criada"
  value       = google_compute_subnetwork.subnet.name
}

output "subnet_cidr" {
  description = "CIDR da subnet principal"
  value       = google_compute_subnetwork.subnet.ip_cidr_range
} 