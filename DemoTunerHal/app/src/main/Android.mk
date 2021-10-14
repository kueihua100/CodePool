LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := DemoTunerHal

LOCAL_SRC_FILES += $(call all-java-files-under, java)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-annotations \
    android-support-core-ui \
    android-support-compat \
    android-support-v7-palette \
    android-support-v7-recyclerview

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

#LOCAL_FULL_MANIFEST_FILE := $(LOCAL_PATH)/AndroidManifest.xml

LOCAL_DEX_PREOPT := false
LOCAL_MODULE_TAGS := optional

LOCAL_SYSTEM_EXT_MODULE := true
LOCAL_CERTIFICATE := platform
LOCAL_PROPRIETARY_MODULE := false
##to use @hide api: marked LOCAL_SDK_VERSION and set LOCAL_PRIVATE_PLATFORM_APIS to true
#LOCAL_SDK_VERSION := current
LOCAL_PRIVATE_PLATFORM_APIS := true

include $(BUILD_PACKAGE)
