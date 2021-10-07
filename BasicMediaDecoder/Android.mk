LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_USE_AAPT2 := true
ifeq ($(BOARD_USES_SYSTEM_EXTIMAGE),true)
    LOCAL_SYSTEM_EXT_MODULE := true
endif
LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := platform
LOCAL_PACKAGE_NAME := BasicMediaDecoder

LOCAL_SDK_VERSION := system_current

LOCAL_SRC_FILES += $(call all-java-files-under, src)


LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/src/main/res \


LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v4 \
    android-support-v7-recyclerview 

LOCAL_JAVACFLAGS := -Xlint:all -Xlint:-deprecation

include $(BUILD_PACKAGE)
