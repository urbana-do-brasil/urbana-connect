# Data source para buscar as versões K8s disponíveis/recomendadas
data "digitalocean_kubernetes_versions" "latest" {
  # Você pode adicionar version_prefix se quiser uma versão específica major.minor, ex: "1.28"
  # version_prefix = "1.29" 
}

resource "digitalocean_kubernetes_cluster" "primary" {
  name   = var.cluster_name
  region = var.do_region
  # Usar a versão mais recente recomendada obtida pelo data source
  version = data.digitalocean_kubernetes_versions.latest.latest_version

  node_pool {
    name       = "default-pool"
    size       = "s-1vcpu-2gb"
    node_count = 2
  }
} 