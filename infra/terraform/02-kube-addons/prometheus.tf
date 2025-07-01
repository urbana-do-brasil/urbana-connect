resource "helm_release" "prometheus" {
  name       = "prometheus"
  chart      = "prometheus-community/prometheus"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  version    = "25.20.0" # Versão estável para evitar atualizações inesperadas

  values = [
    file("${path.module}/prometheus/values.yaml")
  ]

  depends_on = [
    kubernetes_namespace.observability
  ]
}
