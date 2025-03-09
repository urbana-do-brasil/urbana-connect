locals {
  common_labels = {
    environment = var.environment
    project     = "urbana-connect"
    managed_by  = "terraform"
  }
}

# Módulo de rede
module "network" {
  source = "../../modules/network"

  project_id   = var.project_id
  network_name = var.network_name
  subnet_cidr  = var.subnet_cidr
  region       = var.region
  environment  = var.environment
}

# Módulo de computação (GKE)
module "compute" {
  source = "../../modules/compute"

  project_id       = var.project_id
  region           = var.region
  zone             = var.zone
  environment      = var.environment
  network_id       = module.network.network_id
  subnet_id        = module.network.subnet_id
  gke_cluster_name = var.gke_cluster_name
  gke_node_count   = var.gke_node_count
  gke_machine_type = var.gke_machine_type
  labels           = local.common_labels

  depends_on = [module.network]
}

# Módulo de banco de dados
module "database" {
  source = "../../modules/database"

  project_id       = var.project_id
  region           = var.region
  environment      = var.environment
  network_id       = module.network.network_id
  db_instance_name = var.db_instance_name
  db_tier          = var.db_tier
  db_name          = var.db_name
  db_user          = var.db_user
  labels           = local.common_labels

  depends_on = [module.network]
}

# Módulo de armazenamento
module "storage" {
  source = "../../modules/storage"

  project_id          = var.project_id
  region              = var.region
  environment         = var.environment
  storage_bucket_name = var.storage_bucket_name
  versioning_enabled  = var.versioning_enabled
  labels              = local.common_labels
}

# Módulo de segurança
module "security" {
  source = "../../modules/security"

  project_id   = var.project_id
  environment  = var.environment
  network_id   = module.network.network_id
  gke_node_ips = module.compute.gke_node_ips
  labels       = local.common_labels

  depends_on = [module.network, module.compute]
} 