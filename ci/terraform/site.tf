terraform {
  required_version = "> 0.14"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      version = "3.37.0"
    }
  }
//
//  backend "s3" {
//    bucket  = "digital-identity-dev-tfstate"
//    key     = "spike-terraform.tfstate"
//    encrypt = true
//    region  = "eu-west-2"
//  }
}

provider "aws" {
  access_key                  = "mock_access_key"
  region                      = "us-east-1"
  s3_force_path_style         = true
  secret_key                  = "mock_secret_key"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    apigateway     = "http://localhost:45678"
    ecr            = "http://localhost:45678"
    iam            = "http://localhost:45678"
    lambda         = "http://localhost:45678"
  }
}