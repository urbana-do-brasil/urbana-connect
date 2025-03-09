project_id = "extreme-mix-447320-i4"
region     = "us-central1"
zone       = "us-central1-a"
environment = "prod"

# Rede
network_name = "urbana-connect-vpc-prod"
subnet_cidr  = "10.2.0.0/24"

# GKE
gke_cluster_name = "urbana-connect-cluster-prod"
gke_node_count   = 3
gke_machine_type = "e2-medium"

# Banco de dados
db_instance_name = "urbana-connect-db-prod"
db_tier          = "db-g1-small"
db_name          = "urbana_connect_prod"
db_user          = "urbana_connect_user"

# Armazenamento
storage_bucket_name = "urbana-connect-storage-prod"
versioning_enabled  = true 