output "db_instance_name" {
  description = "Nome da instância do Cloud SQL"
  value       = google_sql_database_instance.instance.name
}

output "db_instance_connection_name" {
  description = "Nome de conexão da instância do Cloud SQL"
  value       = google_sql_database_instance.instance.connection_name
}

output "db_instance_ip" {
  description = "Endereço IP da instância do Cloud SQL"
  value       = google_sql_database_instance.instance.public_ip_address
  sensitive   = true
}

output "db_name" {
  description = "Nome do banco de dados"
  value       = google_sql_database.database.name
}

output "db_user" {
  description = "Usuário do banco de dados"
  value       = google_sql_user.user.name
}

output "db_password" {
  description = "Senha do banco de dados"
  value       = google_sql_user.user.password
  sensitive   = true
}

output "db_connection_string" {
  description = "String de conexão para o banco de dados"
  value       = "postgresql://${google_sql_user.user.name}:${google_sql_user.user.password}@${google_sql_database_instance.instance.public_ip_address}:5432/${google_sql_database.database.name}"
  sensitive   = true
} 