# Recursos de Observabilidade

# ConfigMap para o dashboard do Spring Boot
resource "kubernetes_config_map" "grafana_dashboard_springboot" {
  metadata {
    name      = "grafana-dashboard-spring-boot"
    namespace = "monitoring"
    labels = {
      grafana_dashboard = "1"
    }
  }

  data = {
    "spring-boot-jvm-micrometer.json" = file("${path.module}/../../k8s/observability/dashboards/spring-boot-jvm-micrometer.json")
  }

  depends_on = [helm_release.prometheus_stack]
}
