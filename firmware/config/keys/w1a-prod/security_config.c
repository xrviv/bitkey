#include "security_config.h"

// This key is for signing delta update patch files, *not* the actual
// firmware signing. The purpose of signing patch files is to prevent unauthorized
// access to the delta update code paths, to help defend against potential memory
// corruption vulnerabilities in the underlying libraries.
static const uint8_t fwup_delta_patch_pubkey[] = {
  0xfa, 0x3e, 0xd6, 0x4b, 0x8d, 0x78, 0x4e, 0xf7, 0x53, 0x6d, 0x90, 0x90, 0x41, 0xb6, 0xdd, 0xd3,
  0x4c, 0x05, 0x47, 0x8a, 0x65, 0xce, 0x44, 0x7a, 0xc1, 0xa5, 0x35, 0x1e, 0xb7, 0xca, 0xcb, 0x62,
  0x15, 0x91, 0xf1, 0x16, 0xb2, 0x36, 0x0c, 0xfb, 0xe3, 0x3f, 0x52, 0xdd, 0xa1, 0xd4, 0x21, 0xae,
  0x9b, 0x0d, 0x54, 0x7d, 0xa1, 0x69, 0x1e, 0x08, 0xe2, 0x8c, 0x5b, 0xa7, 0x7f, 0xcc, 0x9f, 0xf6,
};

security_config_t security_config FWUP_TASK_DATA = {
  .is_production = SECURE_TRUE,

  // In prod, the biometrics mac key is generated per-device
  .biometrics_mac_key = NULL,
  .fwup_delta_patch_pubkey = (uint8_t*)&fwup_delta_patch_pubkey,
};
