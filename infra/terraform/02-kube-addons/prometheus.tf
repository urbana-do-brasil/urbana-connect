resource "helm_release" "prometheus" {
  name       = "prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "prometheus"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  version    = "25.20.0"

  values = [
    file("${path.module}/prometheus/values.yaml")
  ]

  depends_on = [
    kubernetes_namespace.observability
  ]
}
