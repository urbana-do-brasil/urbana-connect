data "digitalocean_kubernetes_versions" "available" {
  version_prefix = "1."
}

resource "digitalocean_kubernetes_cluster" "urbana_connect" {
  name    = var.cluster_name
  region  = var.region
  version = var.kubernetes_version == "latest" ? data.digitalocean_kubernetes_versions.available.latest_version : var.kubernetes_version
  tags    = var.tags

  node_pool {
    name       = "worker-pool"
    size       = var.node_size
    node_count = var.node_count
    auto_scale = var.auto_scale
    min_nodes  = var.min_nodes
    max_nodes  = var.max_nodes
    tags       = var.tags
  }
}

# Script para configurar o kubectl localmente
resource "null_resource" "configure_kubectl" {
  depends_on = [digitalocean_kubernetes_cluster.urbana_connect]

  provisioner "local-exec" {
    command = "doctl kubernetes cluster kubeconfig save ${digitalocean_kubernetes_cluster.urbana_connect.name}"
  }
}
