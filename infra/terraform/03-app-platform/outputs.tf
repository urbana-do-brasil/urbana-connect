# Output para o nome do namespace
output "namespace_name" {
  description = "Nome do namespace da aplicação"
  value       = kubernetes_namespace.urbana_connect.metadata[0].name
}

# Output para o IP do Load Balancer (apenas para conveniência, obtido do módulo kube-addons)
output "load_balancer_ip" {
  description = "IP do Load Balancer do Nginx Ingress Controller"
  value       = data.terraform_remote_state.kube_addons.outputs.load_balancer_ip
} 