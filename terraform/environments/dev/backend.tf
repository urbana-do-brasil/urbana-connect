terraform {
  backend "gcs" {
    bucket  = "urbana-connect-terraform-state-dev"
    prefix  = "terraform/state"
  }
} 