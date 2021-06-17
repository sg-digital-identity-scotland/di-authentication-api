terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      version = "3.45.0"
    }
  }

  backend "s3" {
  }
}

provider "aws" {
  region = "eu-west-2"

  assume_role {
    role_arn = var.deployer_role_arn
  }
}