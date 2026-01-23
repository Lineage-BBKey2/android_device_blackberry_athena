#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixup_remove,
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

import extract_utils.tools
extract_utils.tools.DEFAULT_PATCHELF_VERSION = '0_9'

namespace_imports = [
    'device/blackberry/sdm660-common',
    'hardware/qcom-caf/msm8998',
    'hardware/qcom-caf/wlan',
    'vendor/blackberry/sdm660-common',
]

blob_fixups: blob_fixups_user_type =        {
    ('vendor/lib/hw/audio.primary.sdm660.so',
     'vendor/lib64/hw/audio.primary.sdm660.so'
     ): blob_fixup()
        .add_needed('libprocessgroup.so'),

    # Wow. This is terrible. Thanks Goodix.
    ('vendor/lib64/hw/fingerprint.goodix.so',
     'vendor/lib64/libfp_client.so',
     'vendor/lib64/libfpservice.so',
     'vendor/lib64/libvendor.goodix.hardware.fingerprint.hwbinder@2.1.so',
     'vendor/lib64/hw/gxfingerprint.default.so',
     'vendor/bin/gx_fpd'): blob_fixup()
        .remove_needed('libandroid_runtime.so')
        .remove_needed('libkeystore_binder.so')
        .remove_needed('libbacktrace.so')
        .remove_needed('libunwind.so')
        .remove_needed('libkeystore_binder.so')
        .remove_needed('libsoftkeymasterdevice.so')
        .remove_needed('libsoftkeymaster.so')
        .remove_needed('libkeymaster_messages.so')
        .replace_needed('libstdc++.so', 'libstdc++_vendor.so')
        .add_needed('libbinder_shim.so'),
}  # fmt: skip

module = ExtractUtilsModule(
    'athena',
    'blackberry',
    namespace_imports=namespace_imports,
    blob_fixups=blob_fixups,
)

if __name__ == '__main__':
    utils = ExtractUtils.device_with_common(
        module, 'sdm660-common', module.vendor
    )
    utils.run()
