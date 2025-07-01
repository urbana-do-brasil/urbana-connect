# Instalação do Nginx Ingress Controller
resource "helm_release" "nginx_ingress" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  version          = var.nginx_ingress_version
  
  # Configurações importantes para DigitalOcean
  set {
    name  = "controller.service.type"
    value = "LoadBalancer"
  }
  
  set {
    name  = "controller.publishService.enabled"
    value = "true"
  }
}

# Instalação do cert-manager
resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  namespace        = "cert-manager"
  create_namespace = true
  version          = var.cert_manager_version
  
  # Habilita CRDs (Custom Resource Definitions)
  set {
    name  = "installCRDs"
    value = "true"
  }
  
  # Instala cert-manager somente após o Nginx Ingress
  depends_on = [helm_release.nginx_ingress]
}

# Data source para obter o IP do Load Balancer após sua criação
data "kubernetes_service" "nginx_ingress_controller" {
  metadata {
    name      = "ingress-nginx-controller"
    namespace = "ingress-nginx"
  }
  
  depends_on = [helm_release.nginx_ingress]
} 