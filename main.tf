provider "ansible" {
  version = "~> 1.0"
}

provider "google" {
  credentials = file("${var.credential-file}")
  project = var.project_id
  region = var.region

}

############# Variables
variable "gke_username" {
  default     = ""
  description = "gke username"
}



variable "gke_password" {
  default     = ""
  description = "gke password"
}
variable "credential-file" {}
variable "gke_num_nodes" {
  default     = 1
  description = "number of gke nodes"
}

terraform {
  required_version = ">= 0.12"
}

variable "project_id" {
  description = "project id"
}

variable "region" {
  description = "region"
}


variable "gce_ssh_user" {
  default = "spanda"
}

variable "gce_ssh_pub_key_file" {
  default = "/Users/shubhasish/.ssh/my_id.pub"
}
variable "docker_image" {}

variable "instance-type" {}
variable "instance-name" {}


############ VPC


data "http" "myip" {
  url = "http://ipv4.icanhazip.com"
}

# VPC
resource "google_compute_network" "vpc" {
  name                    = "${var.project_id}-vpc"
  auto_create_subnetworks = "false"
}

# Subnet
resource "google_compute_subnetwork" "subnet" {
  name          = "${var.project_id}-subnet"
  region        = var.region
  network       = google_compute_network.vpc.name
  ip_cidr_range = "10.10.0.0/24"

}

resource "google_compute_firewall" "default" {
  name    = "${var.project_id}-firewall"
  network = google_compute_network.vpc.name

  allow {
    protocol = "icmp"
  }

  source_ranges = ["104.56.114.248/32","198.144.216.128/32","103.129.21.173/32","192.195.81.38/32","${chomp(data.http.myip.body)}/32"]
  allow {
    protocol = "tcp"
    ports    = ["80","8080","22"]
  }

  target_tags = ["test-webserver"]
}

output "region" {
  value       = var.region
  description = "region"
}

############# Instance
resource "google_compute_instance" "instance" {
  name         = var.instance-name
  machine_type = var.instance-type
  zone         = "${var.region}-a"

  tags = ["test-webserver"]

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-1804-bionic-v20190403"
    }
  }

  // Local SSD disk

  network_interface {
    network = google_compute_network.vpc.id
    subnetwork = google_compute_subnetwork.subnet.id
    access_config {
      // Ephemeral IP
    }
  }

  metadata = {
    sshKeys = "${var.gce_ssh_user}:${file(var.gce_ssh_pub_key_file)}"
  }

  metadata_startup_script = "echo hi > /test.txt"

  service_account {
    scopes = ["userinfo-email", "compute-ro", "storage-ro"]
  }
}

output "public_ip" {
  value = google_compute_instance.instance.network_interface.0.access_config.0.nat_ip
}

#############################################################  Ansible Host
resource "ansible_host" "webserver" {
  inventory_hostname = google_compute_instance.instance.network_interface.0.access_config.0.nat_ip
  groups = ["webserver"]
  vars = {
    ansible_user = var.gce_ssh_user
    become = "yes"
    interpreter_python = "/usr/bin/python3"
    ansible_ssh_private_key_file = replace(var.gce_ssh_pub_key_file, ".pub","")
    docker_image = var.docker_image


  }
}

################ GKE


# GKE cluster
resource "google_container_cluster" "primary" {
  name     = "${var.project_id}-gke"
  location = var.region

  initial_node_count       = 1

  network    = google_compute_network.vpc.name
  subnetwork = google_compute_subnetwork.subnet.name

  node_config {
    oauth_scopes = [
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring",
    ]

    labels = {
      env = var.project_id
    }

    # preemptible  = true
    machine_type = var.instance-type
    tags = [
      "test-webserver"]
    metadata = {
      disable-legacy-endpoints = "true"
      sshKeys = "${var.gce_ssh_user}:${file(var.gce_ssh_pub_key_file)}"
    }
  }

  master_auth {
    username = var.gke_username
    password = var.gke_password

    client_certificate_config {
      issue_client_certificate = false
    }
  }
}

# Separately Managed Node Pool


output "kubernetes_cluster_name" {
  value       = google_container_cluster.primary.name
  description = "GKE Cluster Name"
}
