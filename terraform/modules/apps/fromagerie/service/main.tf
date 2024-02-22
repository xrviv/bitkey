module "this" {
  source    = "../../../lookup/namespacer"
  namespace = var.namespace
  name      = "fromagerie"
}

module "lookup_vpc" {
  source   = "../../../lookup/vpc"
  vpc_name = var.vpc_name
}

data "aws_caller_identity" "this" {}

data "aws_region" "this" {}

locals {
  port = 3000

  ################################################
  # DynamoDB Tables
  ################################################
  tables = {
    address_watchlist_table_name     = "${module.this.id_dot}.address_watchlist"
    notification_table_name          = "${module.this.id_dot}.notification"
    chain_indexer_table_name         = "${module.this.id_dot}.chain_indexer"
    daily_spending_record_table_name = "${module.this.id_dot}.daily_spending_record"
    signed_psbt_cache_table_name     = "${module.this.id_dot}.signed_psbt_cache"
    migration_record_table_name      = "${module.this.id_dot}.migration_records"
    social_recovery_table_name       = "${module.this.id_dot}.social_recovery"
    consent_table_name               = "${module.this.id_dot}.consent"

    # Below are old tables that we retain a name override for the deprecated PrototypeOnboardingStack
    # New tables should be added above without coalesce()
    account_table_name  = coalesce(var.account_table_name, "${module.this.id_dot}.accounts")
    recovery_table_name = coalesce(var.recovery_table_name, "${module.this.id_dot}.account_recovery")
  }
  table_name_list = [for k, name in local.tables : name]

  ################################################
  # S3 Buckets
  ################################################
  buckets = {
    sanctions_screener_bucket_name = "bitkey-${module.this.id_dot}-sanctions-screener-${var.environment}"
  }

  ################################################
  # Push notification endpoints
  ################################################
  // TODO: remove after old apps are gone [W-4150]
  apns_platform_arn            = "arn:aws:sns:${data.aws_region.this.name}:${data.aws_caller_identity.this.account_id}:app/APNS/bitkey-ios"
  apns_customer_platform_arn   = "arn:aws:sns:${data.aws_region.this.name}:${data.aws_caller_identity.this.account_id}:app/APNS/bitkey-customer-ios"
  apns_team_platform_arn       = "arn:aws:sns:${data.aws_region.this.name}:${data.aws_caller_identity.this.account_id}:app/APNS/bitkey-team-ios"
  apns_team_alpha_platform_arn = "arn:aws:sns:${data.aws_region.this.name}:${data.aws_caller_identity.this.account_id}:app/APNS/bitkey-team-alpha-ios"
  // TODO: remove after old apps are gone [W-4150]
  fcm_platform_arn          = "arn:aws:sns:${data.aws_region.this.name}:${data.aws_caller_identity.this.account_id}:app/GCM/bitkey-android"
  fcm_customer_platform_arn = "arn:aws:sns:${data.aws_region.this.name}:${data.aws_caller_identity.this.account_id}:app/GCM/bitkey-customer-android"
  fcm_team_platform_arn     = "arn:aws:sns:${data.aws_region.this.name}:${data.aws_caller_identity.this.account_id}:app/GCM/bitkey-team-android"

  ###############################################
  # Environment variables shared by fromagerie ECS tasks
  ###############################################
  common_env_vars = {
    APNS_PLATFORM_ARN                = local.apns_platform_arn # TODO: remove after old apps are gone [W-4150]
    APNS_CUSTOMER_PLATFORM_ARN       = local.apns_customer_platform_arn
    APNS_TEAM_PLATFORM_ARN           = local.apns_team_platform_arn
    APNS_TEAM_ALPHA_PLATFORM_ARN     = local.apns_team_alpha_platform_arn
    FCM_PLATFORM_ARN                 = local.fcm_platform_arn # TODO: remove after old apps are gone [W-4150]
    FCM_CUSTOMER_PLATFORM_ARN        = local.fcm_customer_platform_arn
    FCM_TEAM_PLATFORM_ARN            = local.fcm_team_platform_arn
    DD_ENV                           = var.environment
    OTEL_RESOURCE_ATTRIBUTES         = "deployment.environment=${var.environment}" # DD_ENV isn't respected for f8e metrics, but this is
    RUST_LOG                         = "info"
    PUSH_QUEUE_URL                   = module.push_notification_queue.queue_url
    EMAIL_QUEUE_URL                  = module.email_notification_queue.queue_url
    SMS_QUEUE_URL                    = module.sms_notification_queue.queue_url
    SERVER_WSM_ENDPOINT              = var.wsm_endpoint
    SERVER_FROMAGERIE_ENDPOINT       = "https://${module.ecs_api.alb_fqdn}"
    SERVER_ENABLE_FUND_SIGNET_WALLET = "true"
    ROCKET_PROFILE                   = var.config_profile

    ACCOUNT_TABLE               = local.tables.account_table_name
    ADDRESS_WATCHLIST_TABLE     = local.tables.address_watchlist_table_name
    CHAIN_INDEXER_TABLE         = local.tables.chain_indexer_table_name
    DAILY_SPENDING_RECORD_TABLE = local.tables.daily_spending_record_table_name
    NOTIFICATION_TABLE          = local.tables.notification_table_name
    RECOVERY_TABLE              = local.tables.recovery_table_name
    SIGNED_PSBT_CACHE_TABLE     = local.tables.signed_psbt_cache_table_name
    SOCIAL_RECOVERY_TABLE       = local.tables.social_recovery_table_name
    CONSENT_TABLE               = local.tables.consent_table_name
  }

  ###############################################
  # Secrets shared by fromagerie ECS tasks
  ###############################################
  common_secrets = {
    LAUNCHDARKLY_SDK_KEY = data.aws_secretsmanager_secret.fromagerie_launchdarkly_sdk_key.arn
    SEGMENT_API_KEY      = data.aws_secretsmanager_secret.fromagerie_segment_api_key.arn
  }
}

data "aws_secretsmanager_secret" "fromagerie_zendesk_credentials" {
  name = "fromagerie/zendesk/credentials"
}

data "aws_secretsmanager_secret" "fromagerie_iterable_credentials" {
  name = "fromagerie/iterable/credentials"
}

data "aws_secretsmanager_secret" "fromagerie_twilio_credentials" {
  name = "fromagerie/twilio/credentials"
}

data "aws_secretsmanager_secret" "fromagerie_launchdarkly_sdk_key" {
  name = "fromagerie/launchdarkly/sdk_key"
}

data "aws_secretsmanager_secret" "fromagerie_segment_api_key" {
  name = "fromagerie/segment/api_key"
}

data "aws_secretsmanager_secret" "fromagerie_coinmarketcap_api_key" {
  name = "fromagerie/coinmarketcap/api_key"
}

data "aws_secretsmanager_secret" "fromagerie_coingecko_api_key" {
  name = "fromagerie/coingecko/api_key"
}

data "aws_acm_certificate" "external_certs" {
  count  = length(var.external_certs)
  domain = var.external_certs[count.index]
}

module "dynamodb_tables" {
  source = "./db"

  enable_deletion_protection = var.enable_deletion_protection

  account_table_name               = local.tables.account_table_name
  address_watchlist_table_name     = local.tables.address_watchlist_table_name
  chain_indexer_table_name         = local.tables.chain_indexer_table_name
  daily_spending_record_table_name = local.tables.daily_spending_record_table_name
  notification_table_name          = local.tables.notification_table_name
  recovery_table_name              = local.tables.recovery_table_name
  signed_psbt_cache_table_name     = local.tables.signed_psbt_cache_table_name
  migration_record_table_name      = local.tables.migration_record_table_name
  social_recovery_table_name       = local.tables.social_recovery_table_name
  consent_table_name               = local.tables.consent_table_name
}

module "ecs_api" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-api"

  internet_facing    = var.internet_facing
  dns_hosted_zone    = var.dns_hosted_zone
  subdomain          = var.subdomain
  additional_certs   = data.aws_acm_certificate.external_certs[*].arn
  port               = local.port
  vpc_name           = var.vpc_name
  cluster_arn        = var.cluster_arn
  security_group_ids = [var.wsm_ingress_security_group]

  environment = var.environment
  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-api,mode=datadog}"
    COGNITO_USER_POOL       = var.cognito_user_pool_id
    COGNITO_CLIENT_ID       = var.cognito_user_pool_client_id
    ROCKET_PORT             = local.port
  })
  secrets = merge(local.common_secrets, {
    ITERABLE_API_KEY      = data.aws_secretsmanager_secret.fromagerie_iterable_credentials.arn,
    COINGECKO_API_KEY     = data.aws_secretsmanager_secret.fromagerie_coingecko_api_key.arn,
    COINMARKETCAP_API_KEY = data.aws_secretsmanager_secret.fromagerie_coinmarketcap_api_key.arn,
    TWILIO_ACCOUNT_SID    = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_ACCOUNT_SID::",
    TWILIO_AUTH_TOKEN     = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_AUTH_TOKEN::",
    TWILIO_KEY_SID        = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_KEY_SID::",
    TWILIO_KEY_SECRET     = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_KEY_SECRET::",
    ZENDESK_AUTHORIZATION = data.aws_secretsmanager_secret.fromagerie_zendesk_credentials.arn,
  })
  image_name       = var.image_name
  image_tag        = var.image_tag
  cpu_architecture = "ARM64"

  cpu                   = 512
  memory                = 1024
  desired_count         = var.api_desired_count
  wait_for_steady_state = var.wait_for_steady_state
}

module "api_migration" {
  source     = "../../../pieces/ecs-containers"
  name       = "${var.name}-api-migration"
  image_name = var.image_name
  image_tag  = coalesce(var.image_tag, "fake-tag-for-template")
  namespace  = var.namespace
  command    = ["migrate"]

  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-api-migration,mode=datadog}"
    COGNITO_USER_POOL       = var.cognito_user_pool_id
    COGNITO_CLIENT_ID       = var.cognito_user_pool_client_id
    MIGRATION_TABLE         = local.tables.migration_record_table_name
    SERVER_ZENDESK          = "{mode=test}" //TODO: Pick apart bootstrap dependence on Zendesk,
  })
  environment = var.environment
  secrets = merge(local.common_secrets, {
    ITERABLE_API_KEY   = data.aws_secretsmanager_secret.fromagerie_iterable_credentials.arn
    TWILIO_ACCOUNT_SID = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_ACCOUNT_SID::",
    TWILIO_AUTH_TOKEN  = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_AUTH_TOKEN::",
    TWILIO_KEY_SID     = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_KEY_SID::",
    TWILIO_KEY_SECRET  = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_KEY_SECRET::",
  })
}

resource "aws_security_group" "api_migration" {
  name        = "${module.this.id}-migration_runner"
  description = "migration runner task security group"
  vpc_id      = module.lookup_vpc.vpc_id
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

resource "aws_cloudwatch_log_group" "service" {
  name = var.namespace == "default" ? "${var.name}-api-migration" : "${var.namespace}/${var.name}-api-migration"
}

module "api_migration_iam" {
  source    = "../../../pieces/ecs-iam-roles"
  namespace = var.namespace
  name      = "${var.name}-api-migration"
}

# API migration task definition
resource "aws_ecs_task_definition" "api_migration" {
  family                   = "${module.this.id}-api-migration"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = module.api_migration_iam.exec_role_arn
  task_role_arn            = module.api_migration_iam.task_role_arn
  container_definitions    = jsonencode(module.api_migration.containers)
  cpu                      = 512
  memory                   = 1024
  runtime_platform {
    cpu_architecture = "ARM64"
  }
}

module "ecs_job_push" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-job-push"

  create_load_balancer = false
  vpc_name             = var.vpc_name
  cluster_arn          = var.cluster_arn

  environment = var.environment
  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-job-push,mode=datadog}"
    SERVER_COGNITO          = "test"        //TODO: Pick apart bootstrap dependence on Cognito,
    SERVER_TWILIO           = "{mode=test}" //TODO: Pick apart bootstrap dependence on Twilio,
    SERVER_ITERABLE         = "{mode=test}" //TODO: Pick apart bootstrap dependence on Iterable,
    SERVER_ZENDESK          = "{mode=test}" //TODO: Pick apart bootstrap dependence on Zendesk,
  })
  secrets          = merge(local.common_secrets, {})
  image_name       = var.image_name
  image_tag        = var.image_tag
  command          = ["worker", "push"]
  port             = local.port
  cpu_architecture = "ARM64"

  desired_count         = var.job_push_desired_count
  wait_for_steady_state = var.wait_for_steady_state
}

module "ecs_job_email" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-job-email"

  create_load_balancer = false
  vpc_name             = var.vpc_name
  cluster_arn          = var.cluster_arn

  environment = var.environment
  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-job-email,mode=datadog}"
    SERVER_COGNITO          = "test"        //TODO: Pick apart bootstrap dependence on Cognito,
    SERVER_TWILIO           = "{mode=test}" //TODO: Pick apart bootstrap dependence on Twilio,
    SERVER_ZENDESK          = "{mode=test}" //TODO: Pick apart bootstrap dependence on Zendesk,
  })
  secrets = merge(local.common_secrets, {
    ITERABLE_API_KEY = data.aws_secretsmanager_secret.fromagerie_iterable_credentials.arn
  })
  image_name       = var.image_name
  image_tag        = var.image_tag
  command          = ["worker", "email"]
  port             = local.port
  cpu_architecture = "ARM64"

  desired_count         = var.job_email_desired_count
  wait_for_steady_state = var.wait_for_steady_state
}

module "ecs_job_sms" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-job-sms"

  create_load_balancer = false
  vpc_name             = var.vpc_name
  cluster_arn          = var.cluster_arn

  environment = var.environment
  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-job-sms,mode=datadog}"
    SERVER_COGNITO          = "test"        //TODO: Pick apart bootstrap dependence on Cognito,
    SERVER_ITERABLE         = "{mode=test}" //TODO: Pick apart bootstrap dependence on Iterable,
    SERVER_ZENDESK          = "{mode=test}" //TODO: Pick apart bootstrap dependence on Zendesk,
  })
  secrets = merge(local.common_secrets, {
    TWILIO_ACCOUNT_SID = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_ACCOUNT_SID::",
    TWILIO_AUTH_TOKEN  = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_AUTH_TOKEN::",
    TWILIO_KEY_SID     = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_KEY_SID::",
    TWILIO_KEY_SECRET  = "${data.aws_secretsmanager_secret.fromagerie_twilio_credentials.arn}:TWILIO_KEY_SECRET::",
  })
  image_name       = var.image_name
  image_tag        = var.image_tag
  command          = ["worker", "sms"]
  port             = local.port
  cpu_architecture = "ARM64"

  desired_count         = var.job_sms_desired_count
  wait_for_steady_state = var.wait_for_steady_state
}

module "ecs_job_scheduled_notification_task" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-job-scheduled-notification"

  create_load_balancer = false
  vpc_name             = var.vpc_name
  cluster_arn          = var.cluster_arn

  image_name  = var.image_name
  image_tag   = var.image_tag
  command     = ["worker", "scheduled-notification"]
  environment = var.environment
  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-job-scheduled-notification,mode=datadog}"
    SERVER_COGNITO          = "test"        //TODO: Pick apart bootstrap dependence on Cognito,
    SERVER_TWILIO           = "{mode=test}" //TODO: Pick apart bootstrap dependence on Twilio,
    SERVER_ITERABLE         = "{mode=test}" //TODO: Pick apart bootstrap dependence on Iterable,
    SERVER_ZENDESK          = "{mode=test}" //TODO: Pick apart bootstrap dependence on Zendesk,
  })
  secrets          = merge(local.common_secrets, {})
  cpu_architecture = "ARM64"

  desired_count         = var.job_email_desired_count
  wait_for_steady_state = var.wait_for_steady_state
}

module "ecs_job_blockchain_polling_task_signet" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-job-blockchain-polling-signet"

  create_load_balancer = false
  vpc_name             = var.vpc_name
  cluster_arn          = var.cluster_arn

  image_name  = var.image_name
  image_tag   = var.image_tag
  command     = ["worker", "blockchain-polling"]
  environment = var.environment
  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-job-blockchain-polling,mode=datadog}"
    SERVER_COGNITO          = "test"        //TODO: Pick apart bootstrap dependence on Cognito,
    SERVER_TWILIO           = "{mode=test}" //TODO: Pick apart bootstrap dependence on Twilio,
    SERVER_ITERABLE         = "{mode=test}" //TODO: Pick apart bootstrap dependence on Iterable,
    SERVER_ZENDESK          = "{mode=test}" //TODO: Pick apart bootstrap dependence on Zendesk,
    CHAIN_INDEXER_BASE_URL  = "https://bitkey.mempool.space/signet/api"
    CHAIN_INDEXER_NETWORK   = "signet"
  })
  secrets          = merge(local.common_secrets, {})
  cpu_architecture = "ARM64"

  desired_count         = var.job_blockchain_desired_count
  wait_for_steady_state = var.wait_for_steady_state
}

module "ecs_job_blockchain_polling_task_mainnet" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-job-blockchain-polling-mainnet"

  create_load_balancer = false
  vpc_name             = var.vpc_name
  cluster_arn          = var.cluster_arn

  image_name  = var.image_name
  image_tag   = var.image_tag
  command     = ["worker", "blockchain-polling"]
  environment = var.environment
  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-job-blockchain-polling,mode=datadog}"
    SERVER_COGNITO          = "test"        //TODO: Pick apart bootstrap dependence on Cognito,
    SERVER_TWILIO           = "{mode=test}" //TODO: Pick apart bootstrap dependence on Twilio,
    SERVER_ITERABLE         = "{mode=test}" //TODO: Pick apart bootstrap dependence on Iterable,
    SERVER_ZENDESK          = "{mode=test}" //TODO: Pick apart bootstrap dependence on Zendesk,
    CHAIN_INDEXER_BASE_URL  = "https://bitkey.mempool.space/api"
    CHAIN_INDEXER_NETWORK   = "bitcoin"
  })
  secrets          = merge(local.common_secrets, {})
  cpu_architecture = "ARM64"

  desired_count         = var.job_blockchain_desired_count
  wait_for_steady_state = var.wait_for_steady_state
}

module "ecs_job_metrics" {
  source = "../../../models/ecs-service"

  namespace = var.namespace
  name      = "${var.name}-job-metrics"

  create_load_balancer = false
  vpc_name             = var.vpc_name
  cluster_arn          = var.cluster_arn

  image_name  = var.image_name
  image_tag   = var.image_tag
  command     = ["worker", "metrics"]
  environment = var.environment
  environment_variables = merge(local.common_env_vars, {
    SERVER_WALLET_TELEMETRY = "{service_name=${var.name}-job-metrics,mode=datadog}"
    SERVER_COGNITO          = "test"        //TODO: Pick apart bootstrap dependence on Cognito,
    SERVER_TWILIO           = "{mode=test}" //TODO: Pick apart bootstrap dependence on Twilio,
    SERVER_ITERABLE         = "{mode=test}" //TODO: Pick apart bootstrap dependence on Iterable,
    SERVER_ZENDESK          = "{mode=test}" //TODO: Pick apart bootstrap dependence on Zendesk,
  })
  secrets          = merge(local.common_secrets, {})
  cpu_architecture = "ARM64"

  desired_count         = var.job_metrics_desired_count
  wait_for_steady_state = var.wait_for_steady_state
}

################################################
# S3 Buckets
################################################

module "screener_s3_bucket" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-s3-bucket//?ref=3a1c80b29fdf8fc682d2749456ec36ecbaf4ce14" // Tag v4.1.0

  bucket = local.buckets.sanctions_screener_bucket_name

  versioning = {
    enabled = true
  }
}

// TODO: Separate policies for each task to only allow them what they need, or maybe don't bother since
// this is all part of the same app?
data "aws_iam_policy_document" "api_iam_policy" {
  statement {
    actions   = ["kms:GenerateRandom"]
    resources = ["*"]
  }

  statement {
    actions = [
      "sns:CreatePlatformEndpoint",
    ]
    resources = [
      local.apns_platform_arn, // TODO: remove after old apps are gone [W-4150]
      local.apns_customer_platform_arn,
      local.apns_team_platform_arn,
      local.apns_team_alpha_platform_arn,
      local.fcm_platform_arn, // TODO: remove after old apps are gone [W-4150]
      local.fcm_customer_platform_arn,
      local.fcm_team_platform_arn
    ]
  }

  statement {
    actions = [
      "sns:Publish"
    ]
    resources = ["*"] // No way to allow direct publishing to SMS without universal wildcard
  }

  statement {
    actions = [
      "sqs:PurgeQueue",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl"
    ]
    resources = [
      module.push_notification_queue.queue_arn,
      module.email_notification_queue.queue_arn,
      module.sms_notification_queue.queue_arn
    ]
  }

  statement {
    actions = [
      "sqs:ChangeMessageVisibility",
      "sqs:DeleteMessage",
      "sqs:ReceiveMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = [
      module.push_notification_queue.queue_arn,
      module.email_notification_queue.queue_arn,
      module.sms_notification_queue.queue_arn
    ]
  }

  statement {
    actions = [
      "sqs:SendMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl"
    ]
    resources = [
      module.push_notification_queue.queue_arn,
      module.email_notification_queue.queue_arn,
      module.sms_notification_queue.queue_arn
    ]
  }

  statement {
    actions = [
      "cognito-idp:AdminCreateUser",
      "cognito-idp:AdminEnableUser",
      "cognito-idp:AdminGetUser",
      "cognito-idp:AdminInitiateAuth",
      "cognito-idp:AdminResetUserPassword",
      "cognito-idp:AdminRespondToAuthChallenge",
      "cognito-idp:AdminSetUserPassword",
      "cognito-idp:AdminSetUserSettings",
      "cognito-idp:AdminUpdateUserAttributes",
      "cognito-idp:AdminUserGlobalSignOut"
    ]
    resources = [var.cognito_user_pool_arn]
  }

  statement {
    actions = [
      "dynamodb:DeleteItem",
    ]
    resources = [
      "arn:aws:dynamodb:*:${data.aws_caller_identity.this.account_id}:table/${local.tables.account_table_name}",
      "arn:aws:dynamodb:*:${data.aws_caller_identity.this.account_id}:table/${local.tables.social_recovery_table_name}"
    ]
  }

  statement {
    actions = [
      "s3:GetObject"
    ]

    resources = [
      "arn:aws:s3:::${local.buckets.sanctions_screener_bucket_name}/*"
    ]
  }
}

resource "aws_iam_role_policy" "api_iam_policy" {
  role   = module.ecs_api.task_role_name
  policy = data.aws_iam_policy_document.api_iam_policy.json
}

resource "aws_iam_role_policy" "job_email_iam_policy" {
  role   = module.ecs_job_email.task_role_name
  policy = data.aws_iam_policy_document.api_iam_policy.json
}

resource "aws_iam_role_policy" "job_push_iam_policy" {
  role   = module.ecs_job_push.task_role_name
  policy = data.aws_iam_policy_document.api_iam_policy.json
}

resource "aws_iam_role_policy" "job_sms_iam_policy" {
  role   = module.ecs_job_sms.task_role_name
  policy = data.aws_iam_policy_document.api_iam_policy.json
}

resource "aws_iam_role_policy" "job_scheduled_notification" {
  role   = module.ecs_job_scheduled_notification_task.task_role_name
  policy = data.aws_iam_policy_document.api_iam_policy.json
}

resource "aws_iam_role_policy" "job_blockchain_polling_signet" {
  role   = module.ecs_job_blockchain_polling_task_signet.task_role_name
  policy = data.aws_iam_policy_document.api_iam_policy.json
}

resource "aws_iam_role_policy" "job_blockchain_polling_mainnet" {
  role   = module.ecs_job_blockchain_polling_task_mainnet.task_role_name
  policy = data.aws_iam_policy_document.api_iam_policy.json
}

resource "aws_iam_role_policy" "job_metrics" {
  role   = module.ecs_job_metrics.task_role_name
  policy = data.aws_iam_policy_document.api_iam_policy.json
}

data "aws_iam_policy_document" "secrets_iam_policy" {
  statement {
    resources = [
      "arn:aws:secretsmanager:*:*:secret:fromagerie/**",
    ]
    actions = [
      "secretsmanager:GetSecretValue",
    ]
  }
}

resource "aws_iam_role_policy" "api_secrets_iam_policy" {
  role   = module.ecs_api.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

resource "aws_iam_role_policy" "job_email_secrets_iam_policy" {
  role   = module.ecs_job_email.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

resource "aws_iam_role_policy" "job_push_secrets_iam_policy" {
  role   = module.ecs_job_push.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

resource "aws_iam_role_policy" "job_sms_secrets_iam_policy" {
  role   = module.ecs_job_sms.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

resource "aws_iam_role_policy" "job_metrics_secrets_iam_policy" {
  role   = module.ecs_job_metrics.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

resource "aws_iam_role_policy" "job_blockchain_mainnet_secrets_iam_policy" {
  role   = module.ecs_job_blockchain_polling_task_mainnet.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

resource "aws_iam_role_policy" "job_blockchain_signet_secrets_iam_policy" {
  role   = module.ecs_job_blockchain_polling_task_signet.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

resource "aws_iam_role_policy" "job_scheduled_notification_secrets" {
  role   = module.ecs_job_scheduled_notification_task.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

resource "aws_iam_role_policy" "task_api_migration_secrets" {
  role   = module.api_migration_iam.exec_role_name
  policy = data.aws_iam_policy_document.secrets_iam_policy.json
}

module "api_table_policy" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.ecs_api.task_role_name
  table_names = local.table_name_list
}

module "job_email_table_policy" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.ecs_job_email.task_role_name
  table_names = local.table_name_list
}

module "job_push_table_policy" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.ecs_job_push.task_role_name
  table_names = local.table_name_list
}

module "job_sms_table_policy" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.ecs_job_sms.task_role_name
  table_names = local.table_name_list
}

module "job_scheduled_notification" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.ecs_job_scheduled_notification_task.task_role_name
  table_names = local.table_name_list
}

module "job_blockchain_polling_signet" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.ecs_job_blockchain_polling_task_signet.task_role_name
  table_names = local.table_name_list
}

module "job_blockchain_polling_mainnet" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.ecs_job_blockchain_polling_task_mainnet.task_role_name
  table_names = local.table_name_list
}

module "job_metrics_table_policy" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.ecs_job_metrics.task_role_name
  table_names = local.table_name_list
}
module "task_api_migration_table_policy" {
  source = "../../../pieces/dynamodb-iam-policy"

  role        = module.api_migration_iam.task_role_name
  table_names = local.table_name_list
}

################################################
# SQS Queues
################################################

module "push_notification_queue" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-sqs//?ref=7ded3fe7c3b2423ad7da00ad90e651ec133e5774" // Tag v4.0.1

  name = "${module.this.id}-push-notification"
}

module "email_notification_queue" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-sqs//?ref=7ded3fe7c3b2423ad7da00ad90e651ec133e5774" // Tag v4.0.1

  name = "${module.this.id}-email-notification"
}

module "sms_notification_queue" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-sqs//?ref=7ded3fe7c3b2423ad7da00ad90e651ec133e5774" // Tag v4.0.1

  name = "${module.this.id}-sms-notification"
}
