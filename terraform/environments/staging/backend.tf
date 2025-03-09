terraform {
  backend "gcs" {
    bucket  = "urbana-connect-terraform-state-staging"
    prefix  = "terraform/state"
  }
} 