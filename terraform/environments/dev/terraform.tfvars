project_id = "urbana-connect-dev"
region     = "us-central1"
zone       = "us-central1-a"
environment = "dev"

# Rede
network_name = "urbana-connect-vpc-dev"
subnet_cidr  = "10.0.0.0/24"

# GKE
gke_cluster_name = "urbana-connect-cluster-dev"
gke_node_count   = 1
gke_machine_type = "e2-small"

# Banco de dados
db_instance_name = "urbana-connect-db-dev"
db_tier          = "db-f1-micro"
db_name          = "urbana_connect_dev"
db_user          = "urbana_connect_user"

# Armazenamento
storage_bucket_name = "urbana-connect-storage-dev" 