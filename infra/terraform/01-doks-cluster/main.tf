resource "digitalocean_kubernetes_cluster" "primary" {
  name   = var.cluster_name
  region = var.do_region
  # Versão do Kubernetes fixada para evitar upgrades automáticos indesejados
  version = "1.32.2-do.1"

  node_pool {
    name       = "default-pool"
    size       = "s-1vcpu-2gb"
    node_count = 2
  }
} 