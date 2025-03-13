variable "do_token" {
  description = "DigitalOcean API Token"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "DigitalOcean region"
  type        = string
  default     = "nyc1"
}

variable "cluster_name" {
  description = "Name of the Kubernetes cluster"
  type        = string
  default     = "urbana-connect-prod"
}

variable "kubernetes_version" {
  description = "Kubernetes version to use"
  type        = string
  default     = "latest"
}

variable "node_size" {
  description = "Size of worker nodes"
  type        = string
  default     = "s-2vcpu-4gb"
}

variable "node_count" {
  description = "Number of worker nodes"
  type        = number
  default     = 2
}

variable "auto_scale" {
  description = "Enable cluster autoscaling"
  type        = bool
  default     = true
}

variable "min_nodes" {
  description = "Minimum number of nodes when autoscaling"
  type        = number
  default     = 2
}

variable "max_nodes" {
  description = "Maximum number of nodes when autoscaling"
  type        = number
  default     = 5
}

variable "configure_kubernetes" {
  description = "Whether to configure kubernetes provider"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags to apply to the cluster"
  type        = list(string)
  default     = ["urbana", "production"]
}
