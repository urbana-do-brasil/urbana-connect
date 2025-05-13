# Criar namespace para a aplicação
resource "kubernetes_namespace" "urbana_connect" {
  metadata {
    name = var.namespace_name
  }
}

# Criar o ClusterIssuer para o cert-manager
resource "kubernetes_manifest" "cluster_issuer" {
  manifest = {
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata = {
      name = "letsencrypt-prod"
    }
    spec = {
      acme = {
        email  = var.email
        server = "https://acme-v02.api.letsencrypt.org/directory"
        privateKeySecretRef = {
          name = "letsencrypt-prod-account-key"
        }
        solvers = [
          {
            http01 = {
              ingress = {
                class = "nginx"
              }
            }
          }
        ]
      }
    }
  }

  # Assegura que o cert-manager esteja instalado antes (implícito pelo ordem dos módulos)
  # Se necessário, adicione um wait/delay aqui se as CRDs do cert-manager não estiverem prontas
}

# Criar o secret para autenticação no registry da DigitalOcean
resource "kubernetes_secret" "do_registry_credentials" {
  metadata {
    name      = "do-registry-credentials"
    namespace = kubernetes_namespace.urbana_connect.metadata[0].name
  }

  type = "kubernetes.io/dockerconfigjson"

  data = {
    ".dockerconfigjson" = jsonencode({
      auths = {
        "registry.digitalocean.com" = {
          auth = base64encode("token:${var.DIGITALOCEAN_ACCESS_TOKEN}")
        }
      }
    })
  }
} 