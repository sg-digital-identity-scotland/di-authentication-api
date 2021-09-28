module "client-info" {
  source = "../modules/endpoint-module"

  endpoint_name   = "client-info"
  path_part       = "client-info"
  endpoint_method = "GET"
  environment     = var.environment

  handler_environment_variables = {
    BASE_URL             = local.api_base_url
    EVENTS_SNS_TOPIC_ARN = aws_sns_topic.events.arn
    LOCALSTACK_ENDPOINT  = var.use_localstack ? var.localstack_endpoint : null
    REDIS_HOST           = local.external_redis_host
    REDIS_PORT           = local.external_redis_port
    REDIS_PASSWORD       = local.external_redis_password
    REDIS_TLS            = var.redis_use_tls
    ENVIRONMENT          = var.environment
    DYNAMO_ENDPOINT      = var.use_localstack ? var.lambda_dynamo_endpoint : null
  }
  handler_function_name = "uk.gov.di.authentication.clientregistry.lambda.ClientInfoHandler::handleRequest"

  rest_api_id               = aws_api_gateway_rest_api.di_authentication_api.id
  root_resource_id          = aws_api_gateway_rest_api.di_authentication_api.root_resource_id
  execution_arn             = aws_api_gateway_rest_api.di_authentication_api.execution_arn
  api_deployment_stage_name = var.api_deployment_stage_name
  lambda_zip_file           = var.client_registry_api_lambda_zip_file
  security_group_id         = local.authentication_security_group_id
  subnet_id                 = local.authentication_subnet_ids
  lambda_role_arn           = local.lambda_iam_role_arn
  logging_endpoint_enabled  = var.logging_endpoint_enabled
  logging_endpoint_arn      = var.logging_endpoint_arn
  default_tags              = local.default_tags
  api_key_required          = true

  keep_lambda_warm             = var.keep_lambdas_warm
  warmer_handler_function_name = "uk.gov.di.lambdawarmer.lambda.LambdaWarmerHandler::handleRequest"
  warmer_lambda_zip_file       = var.lambda_warmer_zip_file

  use_localstack = var.use_localstack

  depends_on = [
    aws_api_gateway_rest_api.di_authentication_api,
    aws_api_gateway_resource.connect_resource,
    aws_api_gateway_resource.wellknown_resource,
  ]
}
