#include "security_config.h"

// To reprovision the development key: fpc -w 1234567890aabbccddeeff1c3dc0ffee
static const uint8_t biometrics_development_mac_key[] = {
  0x12, 0x34, 0x56, 0x78, 0x90, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x1c, 0x3d, 0xc0, 0xff, 0xee,
};

// This key is for signing delta update patch files, *not* the actual
// firmware signing. The purpose of signing patch files is to prevent unauthorized
// access to the delta update code paths, to help defend against potential memory
// corruption vulnerabilities in the underlying libraries.
static const uint8_t fwup_delta_patch_pubkey[] = {
  0x3d, 0xdb, 0x56, 0xba, 0x55, 0x3b, 0x57, 0x8c, 0x9a, 0xa0, 0x45, 0xe9, 0x6e, 0x77, 0x37, 0xe2,
  0x3b, 0x06, 0x19, 0xd9, 0x99, 0xa8, 0x48, 0x2d, 0x0e, 0x32, 0x5a, 0x7f, 0x2c, 0x30, 0x35, 0xff,
  0x66, 0xf5, 0x7a, 0x39, 0xea, 0xe8, 0xa1, 0xd9, 0x7f, 0xb6, 0x73, 0x19, 0x91, 0x9c, 0x2b, 0x50,
  0x07, 0x85, 0x76, 0x8a, 0x1b, 0x20, 0xc3, 0x2f, 0xa8, 0xaf, 0xb5, 0x5d, 0xca, 0x30, 0x3d, 0x39,
};

security_config_t security_config FWUP_TASK_DATA = {
  .is_production = SECURE_FALSE,
  .biometrics_mac_key = (uint8_t*)&biometrics_development_mac_key,
  .fwup_delta_patch_pubkey = (uint8_t*)&fwup_delta_patch_pubkey,
};
