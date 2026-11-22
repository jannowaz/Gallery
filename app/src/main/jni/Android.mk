LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := NativeImageProcessor
LOCAL_SRC_FILES := NativeImageProcessor.cpp
LOCAL_CPP_FEATURES := exceptions rtti
LOCAL_CPPFLAGS := -std=c++17
# Android 15+ devices may use 16 KB memory pages. The linker settings keep the
# filter processor loadable there while preserving its JNI API unchanged.
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384

include $(BUILD_SHARED_LIBRARY)
