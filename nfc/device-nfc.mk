#############################################################################################
## Includes NFC Packages
#############################################################################################

# Modify it to project environment
LOCAL_PATH                     := device/samsung/exynos9810-common

$(warning "Building S.LSI SEC NFC packages...")
# HAL
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/nfc/init.nfc.sec.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.nfc.sec.rc

# HIDL service
PRODUCT_PACKAGES += \
    nfc_nci_sec \
    android.hardware.nfc@1.2-service.sec \
    sec_nfc_test

# NFC stack and app service
PRODUCT_PACKAGES += \
    libnfc-sec \
    libnfc_sec_jni \
    NfcSec \
    Tag
