output "storage_bucket_name" {
  description = "Nome do bucket de armazenamento"
  value       = google_storage_bucket.main.name
}

output "storage_bucket_url" {
  description = "URL do bucket de armazenamento"
  value       = google_storage_bucket.main.url
}

output "storage_bucket_self_link" {
  description = "Self link do bucket de armazenamento"
  value       = google_storage_bucket.main.self_link
}

output "uploads_folder_path" {
  description = "Caminho para a pasta de uploads"
  value       = "gs://${google_storage_bucket.main.name}/${google_storage_bucket_object.uploads_folder.name}"
}

output "backups_folder_path" {
  description = "Caminho para a pasta de backups"
  value       = "gs://${google_storage_bucket.main.name}/${google_storage_bucket_object.backups_folder.name}"
} 