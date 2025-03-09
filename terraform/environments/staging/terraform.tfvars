project_id = "urbana-connect-staging"
region     = "us-central1"
zone       = "us-central1-a"
environment = "staging"

# Rede
network_name = "urbana-connect-vpc-staging"
subnet_cidr  = "10.1.0.0/24"

# GKE
gke_cluster_name = "urbana-connect-cluster-staging"
gke_node_count   = 2
gke_machine_type = "e2-small"

# Banco de dados
db_instance_name = "urbana-connect-db-staging"
db_tier          = "db-f1-micro"
db_name          = "urbana_connect_staging"
db_user          = "urbana_connect_user"

# Armazenamento
storage_bucket_name = "urbana-connect-storage-staging" 