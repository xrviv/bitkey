locals {
  vpc_name         = "bitkey-main"
  fromagerie_url   = "https://fromagerie.${var.namespace}.dev.wallet.build"
  wsm_url          = "https://wsm.${var.namespace}.dev.wallet.build"
  hosted_zone_name = "dev.wallet.build"
  environment      = "development"
}

data "aws_caller_identity" "current" {}

module "ecs_cluster" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-ecs//?ref=e7647af6055b50b49007ec4d60fb49227bbfd449" // Ref v4.1.3

  cluster_name = "${var.namespace}-bitkey"
}

module "fromagerie" {
  source = "../../apps/fromagerie/service"

  namespace = var.namespace
  name      = "fromagerie"

  config_profile = "development"

  vpc_name        = local.vpc_name
  cluster_arn     = module.ecs_cluster.cluster_name
  internet_facing = true
  dns_hosted_zone = local.hosted_zone_name

  environment = local.environment
  image_name  = "${data.aws_caller_identity.current.account_id}.dkr.ecr.us-west-2.amazonaws.com/wallet-api"
  image_tag   = var.fromagerie_image_tag

  wsm_endpoint               = local.wsm_url
  wsm_ingress_security_group = module.wsm_asg.allow_ingress_security_group_id

  enable_deletion_protection = false

  cognito_user_pool_arn       = module.api_gateway.cognito_user_pool_arn
  cognito_user_pool_client_id = module.api_gateway.cognito_user_pool_client_id
  cognito_user_pool_id        = module.api_gateway.cognito_user_pool_id
  wait_for_steady_state       = false

  depends_on = [
    module.ecs_cluster
  ]
}

module "api_gateway" {
  source = "../../models/bitkey-api-gateway"

  namespace = var.namespace
  name      = "api"

  backend_url      = local.fromagerie_url
  hosted_zone_name = local.hosted_zone_name

  enable_cognito_deletion_protection = false

  depends_on = [module.auth_lambdas]
}

module "auth_lambdas" {
  source = "../../apps/auth"

  namespace = var.namespace
  name      = "auth"

  define_auth_challenge_asset_dir = "${var.auth_lambdas_dir}/define_auth_challenge-lambda"
  create_auth_challenge_asset_dir = "${var.auth_lambdas_dir}/create_auth_challenge-lambda"
  verify_auth_challenge_asset_dir = "${var.auth_lambdas_dir}/verify_auth_challenge-lambda"
}

module "wsm_asg" {
  source = "../../apps/wsm/asg"

  namespace       = var.namespace
  dns_hosted_zone = local.hosted_zone_name
  vpc_name        = local.vpc_name
  environment     = local.environment
}

module "wsm_deploy" {
  source = "../../apps/wsm/deploy"

  namespace = var.namespace
  depends_on = [
    module.wsm_asg,
    module.wsm_dynamodb,
    module.wsm_key
  ]
}

module "wsm_dynamodb" {
  source = "../../apps/wsm/dynamodb"

  namespace = var.namespace
}

module "wsm_key" {
  source = "../../apps/wsm/key"

  namespace        = var.namespace
  enclave_role_arn = module.wsm_asg.enclave_role_arn
  depends_on = [
    module.wsm_asg
  ]
}