terraform {
  backend "gcs" {
    bucket  = "urbana-connect-terraform-state-prod"
    prefix  = "terraform/state"
  }
} 