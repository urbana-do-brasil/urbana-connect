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

# Instalação da Stack de Monitoramento Prometheus
resource "helm_release" "prometheus_stack" {
  name             = "prometheus-stack"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "kube-prometheus-stack"
  namespace        = "monitoring"
  create_namespace = true
  version          = "58.2.0" # Fixar a versão é uma boa prática

  values = [
    file("../../k8s/prometheus/values-prod.yaml")
  ]

  # Garante que o cert-manager esteja pronto antes de instalar a stack
  depends_on = [helm_release.cert_manager]
}

# Data source para obter o IP do Load Balancer após sua criação
data "kubernetes_service" "nginx_ingress_controller" {
  metadata {
    name      = "ingress-nginx-controller"
    namespace = "ingress-nginx"
  }
  
  depends_on = [helm_release.nginx_ingress]
} 