output "app_service_account" {
  description = "Service Account para a aplicação"
  value       = google_service_account.app_sa.email
}

output "app_service_account_id" {
  description = "ID do Service Account para a aplicação"
  value       = google_service_account.app_sa.id
}

output "db_credentials_secret_id" {
  description = "ID do secret para credenciais do banco de dados"
  value       = google_secret_manager_secret.db_credentials.id
}

output "db_credentials_secret_name" {
  description = "Nome do secret para credenciais do banco de dados"
  value       = google_secret_manager_secret.db_credentials.name
} 