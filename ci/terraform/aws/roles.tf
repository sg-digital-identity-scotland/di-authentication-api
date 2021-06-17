module "standard_lambda_role" {
  source = "../modules/lambda-role"

  environment = var.environment
  role_name = "standard"
}

module "sqs_lambda_role" {
  source = "../modules/lambda-role"

  environment = var.environment
  role_name = "sqs-sender"
}