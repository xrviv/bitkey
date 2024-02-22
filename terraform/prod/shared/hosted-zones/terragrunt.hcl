include {
  path = find_in_parent_folders()
}

terraform {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-route53//modules/zones?ref=v2.10.2"
}

inputs = {
  zones = {
    "bitkey.world"         = {}
    "bitkeyproduction.com" = {}
  }
}
