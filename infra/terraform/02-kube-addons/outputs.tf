# Output para o IP do Load Balancer do Nginx Ingress
output "load_balancer_ip" {
  description = "IP do Load Balancer do Nginx Ingress Controller"
  value       = data.kubernetes_service.nginx_ingress_controller.status.0.load_balancer.0.ingress.0.ip
} 