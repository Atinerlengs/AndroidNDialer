LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

incallui_dir := InCallUI
contacts_common_dir := ../ContactsCommon
phone_common_dir := ../PhoneCommon

ifeq ($(TARGET_BUILD_APPS),)
support_library_root_dir := frameworks/support
else
support_library_root_dir := prebuilts/sdk/current/support
endif

src_dirs := src \
    $(incallui_dir)/src \
    $(contacts_common_dir)/src \
    $(phone_common_dir)/src

res_dirs := res \
    $(incallui_dir)/res \
    $(contacts_common_dir)/res \
    $(contacts_common_dir)/icons/res \
    $(phone_common_dir)/res

src_dirs += \
    src-N \
    $(incallui_dir)/src-N \
    $(contacts_common_dir)/src-N \
    $(phone_common_dir)/src-N

# M: Add ContactsCommon ext
src_dirs += $(contacts_common_dir)/ext

# M: Add ext resources
res_dirs += res_ext

# M: Add ContactsCommon ext resources
res_dirs += $(contacts_common_dir)/res_ext

# M: Vilte project not support multi-window @{
$(info Vilte $(MTK_VILTE_SUPPORT), 3GVT $(MTK_VT3G324M_SUPPORT))
ifeq (yes, $(filter yes, $(strip $(MTK_VILTE_SUPPORT)) $(strip $(MTK_VT3G324M_SUPPORT))))
res_dirs += $(incallui_dir)/vt_config
$(info disable multi-window for InCallUi $(res_dirs))
endif
# @}

# M: [InCallUI]additional res
res_dirs += $(incallui_dir)/res_ext
# M: [InCallUI]needed by AddMemberEditView who extends MTKRecipientEditTextView
# M: [InCallUI]FIXME: should replace this with google default RecipientEditTextView
res_dirs += ../../../frameworks/ex/chips/res

# @{freeme.zhaozehong, 2017-07-13, add freeme resources
res_dirs += res_freeme
res_dirs += $(contacts_common_dir)/res_freeme
res_dirs += $(incallui_dir)/res_freeme
# @}
# @{freeme.zhaozehong, 2017-06-28, for use FreemeTabLayout
res_dirs += ../../../vendor/freeme/frameworks/support/design/res
# @}

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    $(support_library_root_dir)/v7/cardview/res \
    $(support_library_root_dir)/v7/recyclerview/res \
    $(support_library_root_dir)/v7/appcompat/res \
    $(support_library_root_dir)/design/res

# @{freeme.zhiwei.zhang, 20170911, FreemeAppTheme
include vendor/freeme/frameworks/support/v7/appcompat/common.mk
# @}

# M: [InCallUI]added com.android.mtkex.chips for MTKRecipientEditTextView
# M: [InCallUI]FIXME: should replace this with google default RecipientEditTextView
LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.v7.cardview \
    --extra-packages android.support.v7.recyclerview \
    --extra-packages android.support.design \
    --extra-packages com.android.incallui \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.phone.common \
    --extra-packages com.android.mtkex.chips

# @{freeme.zhaozehong, 2017-06-28, for use FreemeTabLayout
LOCAL_AAPT_FLAGS += --extra-packages com.freeme.support.design
# @}

LOCAL_JAVA_LIBRARIES := telephony-common ims-common

# M: [InCallUI]additional libraries
LOCAL_JAVA_LIBRARIES += mediatek-framework
# M: Add for ContactsCommon
LOCAL_JAVA_LIBRARIES += voip-common

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    android-support-v13 \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-v7-recyclerview \
    android-support-design \
    com.android.vcard \
    guava \
    libphonenumber

# @{freeme.zhaozehong, 2017-07-06, add libs
LOCAL_STATIC_JAVA_LIBRARIES += pinyin4j
# @}

# M: add mtk-ex
LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.dialer.ext

# M: add for WFC support
LOCAL_STATIC_JAVA_LIBRARIES += wfo-common

# M: add for mtk-tatf case
LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.tatf.common

# M: [InCallUI]ext library
LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.incallui.ext
# M: [InCallUI]added for MTKRecipientEditTextView
# M: [InCallUI]FIXME: should replace this with google default RecipientEditTextView
LOCAL_STATIC_JAVA_LIBRARIES += android-common-chips

# @{freeme.zhaozehong, 2017-06-28, for use FreemeTabLayout
LOCAL_STATIC_JAVA_LIBRARIES += freeme-support-design
# @}

LOCAL_PACKAGE_NAME := Dialer
LOCAL_CERTIFICATE := shared
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags $(incallui_dir)/proguard.flags

# Uncomment the following line to build against the current SDK
# This is required for building an unbundled app.
# M: disable it for mediatek's internal function call.
#LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

# @{freeme.zhaozehong, 2017-07-06, add new libs
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    pinyin4j:libs/pinyin4j-2.5.0.jar
include $(BUILD_MULTI_PREBUILT)
# @}

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
