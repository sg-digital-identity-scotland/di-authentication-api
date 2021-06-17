module "token" {
  source = "../modules/endpoint-module"

  endpoint_name   = "token"
  endpoint_method = "POST"
  environment     = var.environment

  handler_environment_variables = {
    BASE_URL = var.api_base_url
  }
  handler_function_name = "uk.gov.di.lambdas.TokenHandler::handleRequest"

  rest_api_id               = module.api_gateway_root.di_authentication_api_id
  root_resource_id          = module.api_gateway_root.root_resource_id
  execution_arn             = module.api_gateway_root.execution_arn
  api_deployment_stage_name = var.api_deployment_stage_name
  lambda_zip_file           = var.lambda_zip_file
  security_group_id         = aws_vpc.authentication.default_security_group_id
  subnet_id                 = aws_subnet.authentication.*.id
  lambda_role_arn           = module.standard_lambda_role.role_arn

  depends_on = [
    aws_vpc.authentication,
    aws_subnet.authentication,
    module.api_gateway_root,
    module.standard_lambda_role,
    aws_elasticache_replication_group.sessions_store,
  ]
}