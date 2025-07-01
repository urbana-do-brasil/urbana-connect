# Cria o namespace para a stack de observabilidade
resource "kubernetes_namespace" "observability" {
  metadata {
    name = "observability"
    labels = {
      name = "observability"
    }
  }
}
