# Instalação do Nginx Ingress Controller
resource "helm_release" "nginx_ingress" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  version          = "4.7.1"  # Especifique a versão desejada
  
  # Configurações importantes para DigitalOcean
  set {
    name  = "controller.service.type"
    value = "LoadBalancer"
  }
  
  set {
    name  = "controller.publishService.enabled"
    value = "true"
  }
  
  # Garante que o Nginx Ingress só seja instalado após o cluster estar totalmente pronto
  depends_on = [digitalocean_kubernetes_cluster.primary]
}

# Instalação do cert-manager
resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  namespace        = "cert-manager"
  create_namespace = true
  version          = "v1.13.1"  # Especifique a versão desejada
  
  # Habilita CRDs (Custom Resource Definitions)
  set {
    name  = "installCRDs"
    value = "true"
  }
  
  # Instala cert-manager somente após o Nginx Ingress
  depends_on = [helm_release.nginx_ingress]
}

# Criar namespace para a aplicação
resource "kubernetes_namespace" "urbana_connect" {
  metadata {
    name = "urbana-connect"
  }
  
  depends_on = [digitalocean_kubernetes_cluster.primary]
}

# Data source para obter o IP do Load Balancer após sua criação
data "kubernetes_service" "nginx_ingress_controller" {
  metadata {
    name      = "ingress-nginx-controller"
    namespace = "ingress-nginx"
  }
  
  depends_on = [helm_release.nginx_ingress]
}

# Novo output para o IP do Load Balancer do Nginx Ingress
output "load_balancer_ip" {
  description = "IP do Load Balancer do Nginx Ingress Controller"
  value       = data.kubernetes_service.nginx_ingress_controller.status.0.load_balancer.0.ingress.0.ip
  depends_on  = [helm_release.nginx_ingress]
} 