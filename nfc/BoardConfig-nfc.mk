###########################################################################################
## Includes SEPolicy and device manifest
###########################################################################################

# Modify it to project environment
LOCAL_PATH := device/samsung/exynos9810-common

# SAMSUNG NFC
BOARD_USES_SAMSUNG_NFC           := true
BOARD_USES_SAMSUNG_NFC_DTA       := true
BOARD_USES_SAMSUNG_NFC_DTA_64    := true

$(warning "Uses BOARD_USES_SAMSUNG_NFC in BoardConfig")
