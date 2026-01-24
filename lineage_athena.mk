# Inherit some common Lineage stuff
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Device
$(call inherit-product, device/blackberry/athena/device.mk)

PRODUCT_BRAND := BlackBerry
PRODUCT_DEVICE := athena
PRODUCT_MANUFACTURER := TCL Technology
PRODUCT_MODEL := BlackBerry Key2
PRODUCT_NAME := lineage_athena

# frankenstein release x pie
PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="Athena 9 PQ1A.190105.004 ACB156 release-keys" \
    BuildFingerprint=blackberry/athena/athena:9/PQ1A.190105.004/ACB156:user/release-keys \
    DeviceProduct=athena
