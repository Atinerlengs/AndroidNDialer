/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.incallui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.dialer.R;
import com.android.phone.common.animation.AnimUtils;
import com.mediatek.incallui.InCallUtils;
/// M: add for plugin. @{
import com.mediatek.incallui.ext.ExtensionManager;
/// @}
/// M: add for volte. @{
import com.mediatek.incallui.volte.InCallUIVolteUtils;
import com.mediatek.incallui.wrapper.FeatureOptionWrapper;
/// @}

import java.util.List;
import java.util.Locale;

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter, CallCardPresenter.CallCardUi>
        implements CallCardPresenter.CallCardUi {
    private static final String TAG = "CallCardFragment";
    private static final String IMS_MERGED_SUCCESSFULLY = "IMS_MERGED_SUCCESSFULLY";
    /**
     * Internal class which represents the call state label which is to be applied.
     */
    private class CallStateLabel {
        private CharSequence mCallStateLabel;
        private boolean mIsAutoDismissing;

        public CallStateLabel(CharSequence callStateLabel, boolean isAutoDismissing) {
            mCallStateLabel = callStateLabel;
            mIsAutoDismissing = isAutoDismissing;
        }

        public CharSequence getCallStateLabel() {
            return mCallStateLabel;
        }

        /**
         * Determines if the call state label should auto-dismiss.
         *
         * @return {@code true} if the call state label should auto-dismiss.
         */
        public boolean isAutoDismissing() {
            return mIsAutoDismissing;
        }
    };

    private static final String IS_DIALPAD_SHOWING_KEY = "is_dialpad_showing";

    /**
     * The duration of time (in milliseconds) a call state label should remain visible before
     * resetting to its previous value.
     */
    private static final long CALL_STATE_LABEL_RESET_DELAY_MS = 3000;
    /**
     * Amount of time to wait before sending an announcement via the accessibility manager.
     * When the call state changes to an outgoing or incoming state for the first time, the
     * UI can often be changing due to call updates or contact lookup. This allows the UI
     * to settle to a stable state to ensure that the correct information is announced.
     */
    private static final long ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS = 500;

    private AnimatorSet mAnimatorSet;
    private int mShrinkAnimationDuration;
    private int mFabNormalDiameter;
    private int mFabSmallDiameter;
    private boolean mIsLandscape;
    private boolean mHasLargePhoto;
    private boolean mIsDialpadShowing;

    // Primary caller info
    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mPrimaryName;
    private View mCallStateButton;
    private ImageView mCallStateIcon;
    private ImageView mCallStateVideoCallIcon;
    private TextView mCallStateLabel;
    private TextView mCallTypeLabel;
    private ImageView mHdAudioIcon;
    private ImageView mForwardIcon;
    private View mCallNumberAndLabel;
    private TextView mElapsedTime;
    private Drawable mPrimaryPhotoDrawable;
    private TextView mCallSubject;
    private ImageView mWorkProfileIcon;

    // Container view that houses the entire primary call card, including the call buttons
    private View mPrimaryCallCardContainer;
    // Container view that houses the primary call information
    private ViewGroup mPrimaryCallInfo;
    private View mCallButtonsContainer;
    private ImageView mPhotoSmall;

    // Secondary caller info
    private View mSecondaryCallInfo;
    private TextView mSecondaryCallName;
    private View mSecondaryCallProviderInfo;
    private TextView mSecondaryCallProviderLabel;
    private View mSecondaryCallConferenceCallIcon;
    private View mSecondaryCallVideoCallIcon;
    private View mProgressSpinner;

    // Call card content
    private View mCallCardContent;
    private ImageView mPhotoLarge;
    private View mContactContext;
    private TextView mContactContextTitle;
    private ListView mContactContextListView;
    private LinearLayout mContactContextListHeaders;

    private View mManageConferenceCallButton;

    // Dark number info bar
    private TextView mInCallMessageLabel;

    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private ImageButton mFloatingActionButton;
    private int mFloatingActionButtonVerticalOffset;

    private float mTranslationOffset;
    private Animation mPulseAnimation;

    private int mVideoAnimationDuration;
    // Whether or not the call card is currently in the process of an animation
    private boolean mIsAnimating;

    private MaterialPalette mCurrentThemeColors;

    /**
     * Call state label to set when an auto-dismissing call state label is dismissed.
     */
    private CharSequence mPostResetCallStateLabel;
    private boolean mCallStateLabelResetPending = false;
    private Handler mHandler;

    /**
     * Determines if secondary call info is populated in the secondary call info UI.
     */
    private boolean mHasSecondaryCallInfo = false;


    /**
     * M: A tag for the secondcallinfo previous visibility, to identify if the view need reset
     * state or not.
     */
    private boolean mSecondCallInforLatestVisibility = false;

    @Override
    public CallCardPresenter.CallCardUi getUi() {
        return this;
    }

    @Override
    public CallCardPresenter createPresenter() {
        return new CallCardPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /// M: log enhancement
        Log.d(this, "onCreate...");
        mHandler = new Handler(Looper.getMainLooper());
        mShrinkAnimationDuration = getResources().getInteger(R.integer.shrink_animation_duration);
        mVideoAnimationDuration = getResources().getInteger(R.integer.video_animation_duration);
        mFloatingActionButtonVerticalOffset = getResources().getDimensionPixelOffset(
                R.dimen.floating_action_button_vertical_offset);
        mFabNormalDiameter = getResources().getDimensionPixelOffset(
                R.dimen.end_call_floating_action_button_diameter);
        mFabSmallDiameter = getResources().getDimensionPixelOffset(
                R.dimen.end_call_floating_action_button_small_diameter);

        if (savedInstanceState != null) {
            mIsDialpadShowing = savedInstanceState.getBoolean(IS_DIALPAD_SHOWING_KEY, false);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final CallList calls = CallList.getInstance();
        final Call call = calls.getFirstCall();
        getPresenter().init(getActivity(), call);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(IS_DIALPAD_SHOWING_KEY, mIsDialpadShowing);
        super.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Trace.beginSection(TAG + " onCreate");
        mTranslationOffset =
                getResources().getDimensionPixelSize(R.dimen.call_card_anim_translate_y_offset);
        final View view = inflater.inflate(R.layout.call_card_fragment, container, false);
        Trace.endSection();
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPulseAnimation =
                AnimationUtils.loadAnimation(view.getContext(), R.anim.call_status_pulse);

        mPhoneNumber = (TextView) view.findViewById(R.id.phoneNumber);
        mPrimaryName = (TextView) view.findViewById(R.id.name);
        mNumberLabel = (TextView) view.findViewById(R.id.label);
        mSecondaryCallInfo = view.findViewById(R.id.secondary_call_info);
        mSecondaryCallProviderInfo = view.findViewById(R.id.secondary_call_provider_info);
        mCallCardContent = view.findViewById(R.id.call_card_content);
        mPhotoLarge = (ImageView) view.findViewById(R.id.photoLarge);
        //M:[VideoCall] in MTK solution, when click photo, do nothing
        /*mPhotoLarge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().onContactPhotoClick();
            }
        });*/
        ///@}
        mContactContext = view.findViewById(R.id.contact_context);
        mContactContextTitle = (TextView) view.findViewById(R.id.contactContextTitle);
        mContactContextListView = (ListView) view.findViewById(R.id.contactContextInfo);
        // This layout stores all the list header layouts so they can be easily removed.
        mContactContextListHeaders = new LinearLayout(getView().getContext());
        mContactContextListView.addHeaderView(mContactContextListHeaders);

        mCallStateIcon = (ImageView) view.findViewById(R.id.callStateIcon);
        mCallStateVideoCallIcon = (ImageView) view.findViewById(R.id.videoCallIcon);
        mWorkProfileIcon = (ImageView) view.findViewById(R.id.workProfileIcon);
        mCallStateLabel = (TextView) view.findViewById(R.id.callStateLabel);
        mHdAudioIcon = (ImageView) view.findViewById(R.id.hdAudioIcon);
        mForwardIcon = (ImageView) view.findViewById(R.id.forwardIcon);
        mCallNumberAndLabel = view.findViewById(R.id.labelAndNumber);
        mCallTypeLabel = (TextView) view.findViewById(R.id.callTypeLabel);
        mElapsedTime = (TextView) view.findViewById(R.id.elapsedTime);
        mPrimaryCallCardContainer = view.findViewById(R.id.primary_call_info_container);
        mPrimaryCallInfo = (ViewGroup) view.findViewById(R.id.primary_call_banner);
        mCallButtonsContainer = view.findViewById(R.id.callButtonFragment);
        mPhotoSmall = (ImageView) view.findViewById(R.id.photoSmall);
        mPhotoSmall.setVisibility(View.GONE);
        mInCallMessageLabel = (TextView) view.findViewById(R.id.connectionServiceMessage);
        mProgressSpinner = view.findViewById(R.id.progressSpinner);

        mFloatingActionButtonContainer = view.findViewById(
                R.id.floating_end_call_action_button_container);
        mFloatingActionButton = (ImageButton) view.findViewById(
                R.id.floating_end_call_action_button);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().endCallClicked();
            }
        });
        mFloatingActionButtonController = new FloatingActionButtonController(getActivity(),
                mFloatingActionButtonContainer, mFloatingActionButton);

        mSecondaryCallInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().secondaryInfoClicked();
                updateFabPositionForSecondaryCallInfo();
            }
        });

        mCallStateButton = view.findViewById(R.id.callStateButton);
        mCallStateButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                getPresenter().onCallStateButtonTouched();
                return false;
            }
        });

        mManageConferenceCallButton = view.findViewById(R.id.manage_conference_call_button);
        mManageConferenceCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InCallActivity activity = (InCallActivity) getActivity();
                /// M: Activity maybe not resumed in Multi-Window case. @{
                if (activity.isResumed()) {
                    activity.showConferenceFragment(true);
                }
                /// @}
            }
        });

        mPrimaryName.setElegantTextHeight(false);
        mCallStateLabel.setElegantTextHeight(false);

        /// M: Add for recording. @{
        initVoiceRecorderIcon(view);
        /// @}

        //add for plug in. @{
        ExtensionManager.getCallCardExt()
                .onViewCreated(InCallPresenter.getInstance().getContext(), view);
        ExtensionManager.getRCSeCallCardExt()
                .onViewCreated(InCallPresenter.getInstance().getContext(), view);
        //add for plug in. @}
        mCallSubject = (TextView) view.findViewById(R.id.callSubject);
    }

  public void onDestroyView() {
        Log.d(this, "onDestroyView");
        /// M: add for OP09 plugin.@{
        ExtensionManager.getCallCardExt().onDestroyView();
        /// @}
        super.onDestroyView();
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Hides or shows the progress spinner.
     *
     * @param visible {@code True} if the progress spinner should be visible.
     */
    @Override
    public void setProgressSpinnerVisible(boolean visible) {
        mProgressSpinner.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setContactContextTitle(View headerView) {
        mContactContextListHeaders.removeAllViews();
        mContactContextListHeaders.addView(headerView);
    }

    @Override
    public void setContactContextContent(ListAdapter listAdapter) {
        mContactContextListView.setAdapter(listAdapter);
    }

    @Override
    public void showContactContext(boolean show) {
        showImageView(mPhotoLarge, !show);
        showImageView(mPhotoSmall, show);
        mPrimaryCallCardContainer.setElevation(
                show ? 0 : getResources().getDimension(R.dimen.primary_call_elevation));
        mContactContext.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets the visibility of the primary call card.
     * Ensures that when the primary call card is hidden, the video surface slides over to fill the
     * entire screen.
     *
     * @param visible {@code True} if the primary call card should be visible.
     */
    @Override
    public void setCallCardVisible(final boolean visible) {
        Log.v(this, "setCallCardVisible : isVisible = " + visible);
        // When animating the hide/show of the views in a landscape layout, we need to take into
        // account whether we are in a left-to-right locale or a right-to-left locale and adjust
        // the animations accordingly.
        final boolean isLayoutRtl = InCallPresenter.isRtl();

        // Retrieve here since at fragment creation time the incoming video view is not inflated.
        final View videoView = getView().findViewById(R.id.incomingVideo);
        if (videoView == null) {
            return;
        }

        // Determine how much space there is below or to the side of the call card.
        final float spaceBesideCallCard = getSpaceBesideCallCard();

        ///M: when (videoView.getHeight() / 2)- (spaceBesideCallCard / 2) < 0 means
        // peer rotation 90, when local video is vertical we use
        //mPrimaryCallCardContainer.getHeight() / 2 to translate @{
        final float realVideoViewTranslation = ((videoView.getHeight() / 2)
                - (spaceBesideCallCard / 2)) > 0 ?
                ((videoView.getHeight() / 2) - (spaceBesideCallCard / 2))
                : mPrimaryCallCardContainer.getHeight() / 2;

        // We need to translate the video surface, but we need to know its position after the layout
        // has occurred so use a {@code ViewTreeObserver}.
        final ViewTreeObserver observer = getView().getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // We don't want to continue getting called.
                getView().getViewTreeObserver().removeOnPreDrawListener(this);

                ///M:[Video call] changed google default , add switch ,
                // control video display view translation.@{
                if (FeatureOptionWrapper.isSupportVideoDisplayTrans()) {
                    float videoViewTranslation = 0f;

                // Translate the call card to its pre-animation state.
                if (!mIsLandscape) {
                    mPrimaryCallCardContainer.setTranslationY(visible ?
                            -mPrimaryCallCardContainer.getHeight() : 0);

                    ViewGroup.LayoutParams p = videoView.getLayoutParams();
                    videoViewTranslation = p.height / 2 - spaceBesideCallCard / 2;
                }

                // Perform animation of video view.
                ViewPropertyAnimator videoViewAnimator = videoView.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration);
              /*M: [Video call]
              if (mIsLandscape) {
                    videoViewAnimator
                            .translationX(visible ? videoViewTranslation : 0);
                } else {
                    videoViewAnimator
                            .translationY(visible ? videoViewTranslation : 0);
                }*/

               if (!mIsLandscape) {
                        videoViewAnimator
                                .translationY(videoViewTranslation)
                                .start();
                    }
                videoViewAnimator.start();
                ///@}
            }
            ///end FeatureOptionWrapper.isSupportVideoDisplayTrans() @}

                // Animate the call card sliding.
                ViewPropertyAnimator callCardAnimator = mPrimaryCallCardContainer.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                if (!visible) {
                                    mPrimaryCallCardContainer.setVisibility(View.GONE);
                                }
                            }

                            @Override
                            public void onAnimationStart(Animator animation) {
                                super.onAnimationStart(animation);
                                if (visible) {
                                    mPrimaryCallCardContainer.setVisibility(View.VISIBLE);
                                }
                            }
                        });

                if (mIsLandscape) {
                    float translationX = mPrimaryCallCardContainer.getWidth();
                    translationX *= isLayoutRtl ? 1 : -1;
                    callCardAnimator
                            .translationX(visible ? 0 : translationX)
                            .start();
                } else {
                    //M:[VIDEOCALL] just for test ,will delete in future
                    Log.d(this, "translationY in vertical --->" + visible);

                    callCardAnimator
                            .translationY(visible ? 0 : -mPrimaryCallCardContainer.getHeight())
                            .start();
                }

                return true;
            }
        });
        /// M: [ALPS02673351] [Video Call] If in fullscreen mode and the whole view has
        /// no changes, this onPreDraw() would never be called. Such as held video call. @{
        Log.v(TAG, "[setCallCardVisible]invalidate to force refresh");
        getView().invalidate();
        /// @}
    }

    /**
     * Determines the amount of space below the call card for portrait layouts), or beside the
     * call card for landscape layouts.
     *
     * @return The amount of space below or beside the call card.
     */
    public float getSpaceBesideCallCard() {
        if (mIsLandscape) {
            return getView().getWidth() - mPrimaryCallCardContainer.getWidth();
        } else {
            final int callCardHeight;
            // Retrieve the actual height of the call card, independent of whether or not the
            // outgoing call animation is in progress. The animation does not run in landscape mode
            // so this only needs to be done for portrait.
            if (mPrimaryCallCardContainer.getTag(R.id.view_tag_callcard_actual_height) != null) {
                callCardHeight = (int) mPrimaryCallCardContainer.getTag(
                        R.id.view_tag_callcard_actual_height);
            } else {
                callCardHeight = mPrimaryCallCardContainer.getHeight();
            }
            return getView().getHeight() - callCardHeight;
        }
    }

    @Override
    public void setPrimaryName(String name, boolean nameIsNumber) {
        if (TextUtils.isEmpty(name)) {
            mPrimaryName.setText(null);
        } else {
            mPrimaryName.setText(nameIsNumber
                    ? PhoneNumberUtilsCompat.createTtsSpannable(name)
                    : name);

            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mPrimaryName.setTextDirection(nameDirection);
        }
    }

    /**
     * Sets the primary image for the contact photo.
     *
     * @param image The drawable to set.
     * @param isVisible Whether the contact photo should be visible after being set.
     */
    @Override
    public void setPrimaryImage(Drawable image, boolean isVisible) {
        if (image != null) {
            setDrawableToImageViews(image);
            showImageView(mPhotoLarge, isVisible);
        }
    }

    @Override
    public void setPrimaryPhoneNumber(String number) {
        // Set the number
        if (TextUtils.isEmpty(number)) {
            mPhoneNumber.setText(null);
            mPhoneNumber.setVisibility(View.GONE);
        } else {
            mPhoneNumber.setText(PhoneNumberUtilsCompat.createTtsSpannable(number));
            mPhoneNumber.setVisibility(View.VISIBLE);
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
    }

    @Override
    public void setPrimaryLabel(String label) {
        if (!TextUtils.isEmpty(label)) {
            mNumberLabel.setText(label);
            mNumberLabel.setVisibility(View.VISIBLE);
        } else {
            mNumberLabel.setVisibility(View.GONE);
        }

    }

    /**
     * Sets the primary caller information.
     *
     * @param number The caller phone number.
     * @param name The caller name.
     * @param nameIsNumber {@code true} if the name should be shown in place of the phone number.
     * @param label The label.
     * @param photo The contact photo drawable.
     * @param isSipCall {@code true} if this is a SIP call.
     * @param isContactPhotoShown {@code true} if the contact photo should be shown (it will be
     *      updated even if it is not shown).
     * @param isWorkCall Whether the call is placed through a work phone account or caller is a work
              contact.
     */
    @Override
    public void setPrimary(String number, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isSipCall, boolean isContactPhotoShown, boolean isWorkCall) {
        Log.d(this, "Setting primary call");
        // set the name field.
        setPrimaryName(name, nameIsNumber);

        if (TextUtils.isEmpty(number) && TextUtils.isEmpty(label)) {
            mCallNumberAndLabel.setVisibility(View.GONE);
            mElapsedTime.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        } else {
            mCallNumberAndLabel.setVisibility(View.VISIBLE);
            mElapsedTime.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        }

        setPrimaryPhoneNumber(number);

        // Set the label (Mobile, Work, etc)
        setPrimaryLabel(label);

        showInternetCallLabel(isSipCall);

        setDrawableToImageViews(photo);
        showImageView(mPhotoLarge, isContactPhotoShown);
        showImageView(mWorkProfileIcon, isWorkCall);
    }

    @Override
    public void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
            String providerLabel, boolean isConference, boolean isVideoCall, boolean isFullscreen) {

        if (show) {
            // M: FIXME: this plugin usage is not correct.
            // M: add for OP09 plug in @{
            if (ExtensionManager.getCallCardExt().shouldShowCallAccountIcon()) {
                if (null == providerLabel) {
                    providerLabel = ExtensionManager.getCallCardExt().getSecondCallProviderLabel();
                }
                ImageView icon = (ImageView) getView().findViewById(R.id.callProviderIcon);
                icon.setVisibility(View.VISIBLE);
                icon.setImageBitmap(
                        ExtensionManager.getCallCardExt().getSecondCallPhoneAccountBitmap());
            }
            // add for OP09 plug in @}
            mHasSecondaryCallInfo = true;
            boolean hasProvider = !TextUtils.isEmpty(providerLabel);
            initializeSecondaryCallInfo(hasProvider);

            // Do not show the secondary caller info in fullscreen mode, but ensure it is populated
            // in case fullscreen mode is exited in the future.
            setSecondaryInfoVisible(!isFullscreen);

            mSecondaryCallConferenceCallIcon.setVisibility(isConference ? View.VISIBLE : View.GONE);
            mSecondaryCallVideoCallIcon.setVisibility(isVideoCall ? View.VISIBLE : View.GONE);

            mSecondaryCallName.setText(nameIsNumber
                    ? PhoneNumberUtilsCompat.createTtsSpannable(name)
                    : name);
            if (hasProvider) {
                mSecondaryCallProviderLabel.setText(providerLabel);
                mCurrentSecondCallColor = getPresenter().getSecondCallColor();
                mSecondaryCallProviderLabel.setTextColor(mCurrentSecondCallColor);
            }

            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mSecondaryCallName.setTextDirection(nameDirection);
            /// M: [CTA] CTA need special "on hold" string in Chinese. @{
            int resId = InCallUtils.isTwoIncomingCalls() ? R.string.notification_incoming_call
                    : (FeatureOptionWrapper.isCta()
                            ? getCtaSpecificOnHoldResId() : R.string.onHold);
            TextView secondaryCallStatus =
                    (TextView) getView().findViewById(R.id.secondaryCallStatus);
            secondaryCallStatus.setText(getView().getResources().getString(resId));
            /// @}
       } else {
            mHasSecondaryCallInfo = false;
            setSecondaryInfoVisible(false);
        }
    }

    /**
     * Sets the visibility of the secondary caller info box.  Note, if the {@code visible} parameter
     * is passed in {@code true}, and there is no secondary caller info populated (as determined by
     * {@code mHasSecondaryCallInfo}, the secondary caller info box will not be shown.
     *
     * @param visible {@code true} if the secondary caller info should be shown, {@code false}
     *      otherwise.
     */
    @Override
    public void setSecondaryInfoVisible(final boolean visible) {
        /**
         * M: In some case, the View.isShown() wouldn't return value we expected here,
         * once the fragment was paused or stopped, but the view of fragment hadn't been destroyed.
         * For example,
         * 1. Establish 1A1H.
         * 2. Press home key back to home-screen.
         * 3. Launch the dialer, input "12", and then dial
         * 4. Return to in-call screen from notification.
         * 5. Finally, the secondary info about holding call wouldn't disappear.
         * @{
         */
        boolean wasVisible = mSecondaryCallInfo.getVisibility() == View.VISIBLE;
        /** @} */
        final boolean isVisible = visible && mHasSecondaryCallInfo;
        Log.v(this, "setSecondaryInfoVisible: wasVisible = " + wasVisible + " isVisible = "
                + isVisible);

        // If visibility didn't change, nothing to do.
        if (wasVisible == isVisible
                /**
                 * M:There is a timing issue under 1A1H2W.
                 * The view's visibility change processed in the animination for
                 * the first incomming call while the second visibility change will skiped,
                 * because before the animination stared the visibility status will be reverse.
                 *
                 * Need refresh since not same with the previous visibility too.
                 * @{
                 */
                && mSecondCallInforLatestVisibility == isVisible) {
            Log.v(this, "skip setSecondaryInfoVisible: LatestVisibility "
                    + mSecondCallInforLatestVisibility);
            return;
        }
        mSecondCallInforLatestVisibility = isVisible;
        /** @}*/

        // If we are showing the secondary info, we need to show it before animating so that its
        // height will be determined on layout.
        if (isVisible) {
            mSecondaryCallInfo.setVisibility(View.VISIBLE);
        } else {
            mSecondaryCallInfo.setVisibility(View.GONE);
        }

        updateFabPositionForSecondaryCallInfo();
        // We need to translate the secondary caller info, but we need to know its position after
        // the layout has occurred so use a {@code ViewTreeObserver}.
        final ViewTreeObserver observer = getView().getViewTreeObserver();

        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // We don't want to continue getting called.
                getView().getViewTreeObserver().removeOnPreDrawListener(this);

                // Get the height of the secondary call info now, and then re-hide the view prior
                // to doing the actual animation.
                int secondaryHeight = mSecondaryCallInfo.getHeight();
                if (isVisible) {
                    mSecondaryCallInfo.setVisibility(View.GONE);
                } else {
                    mSecondaryCallInfo.setVisibility(View.VISIBLE);
                }
                Log.v(this, "setSecondaryInfoVisible: secondaryHeight = " + secondaryHeight);

                // Set the position of the secondary call info card to its starting location.
                mSecondaryCallInfo.setTranslationY(visible ? secondaryHeight : 0);

                // Animate the secondary card info slide up/down as it appears and disappears.
                ViewPropertyAnimator secondaryInfoAnimator = mSecondaryCallInfo.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration)
                        .translationY(isVisible ? 0 : secondaryHeight)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                /// M: add for debug FAB.
                                Log.v(this, "onAnimationEnd isVisible= " + isVisible);
                                if (!isVisible) {
                                    mSecondaryCallInfo.setVisibility(View.GONE);
                                }
                                /**
                                 * M: Need reset FabPosion since secondary call visibility
                                 * changed, because FAB position relay on the view's height and
                                 * aligned incorrectly.
                                 */
                                updateFabPosition();
                                /// M: [1A1H2W] update answer view position to show secondary info
                                updateAnswerViewPosition();
                            }

                            @Override
                            public void onAnimationStart(Animator animation) {
                                /// M: add for debug FAB.
                                Log.v(this, "onAnimationStart isVisible= " + isVisible);
                                if (isVisible) {
                                    mSecondaryCallInfo.setVisibility(View.VISIBLE);
                                }
                            }
                        });
                secondaryInfoAnimator.start();

                // Notify listeners of a change in the visibility of the secondary info. This is
                // important when in a video call so that the video call presenter can shift the
                // video preview up or down to accommodate the secondary caller info.
                InCallPresenter.getInstance().notifySecondaryCallerInfoVisibilityChanged(visible,
                        secondaryHeight);

                return true;
            }
        });
    }

    @Override
    public void setCallState(
            int state,
            int videoState,
            int sessionModificationState,
            DisconnectCause disconnectCause,
            String connectionLabel,
            Drawable callStateIcon,
            String gatewayNumber,
            boolean isWifi,
            boolean isConference,
            boolean isWorkCall) {
        boolean isGatewayCall = !TextUtils.isEmpty(gatewayNumber);
        CallStateLabel callStateLabel = getCallStateLabelFromState(state, videoState,
                sessionModificationState, disconnectCause, connectionLabel, isGatewayCall, isWifi,
                isConference, isWorkCall);

        Log.v(this, "setCallState " + callStateLabel.getCallStateLabel());
        Log.v(this, "AutoDismiss " + callStateLabel.isAutoDismissing());
        Log.v(this, "DisconnectCause " + disconnectCause.toString());
        Log.v(this, "gateway " + connectionLabel + gatewayNumber);

        /// M: fix CR:ALPS02583825,after SRVCC,display VT icon. @{
        /// M: for support cmcc video ringtone.InCallUI will receive RX video state when play video
        ///ringtone from network on voice call with dialing state.But this call is still a voice
        ///call,so the video call icon shouldn't show during play video ringtone. @{
        if (VideoProfile.isTransmissionEnabled(videoState)
                ||(VideoProfile.isReceptionEnabled(videoState) && state != Call.State.DIALING)
        /// @}
                || (state == Call.State.ACTIVE && sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_UPGRADE_RESPONSE)) {
            mCallStateVideoCallIcon.setVisibility(View.VISIBLE);
        } else {
            mCallStateVideoCallIcon.setVisibility(View.GONE);
        }
        /// @}

        // Check for video state change and update the visibility of the contact photo.  The contact
        // photo is hidden when the incoming video surface is shown.
        // The contact photo visibility can also change in setPrimary().
        boolean showContactPhoto = !VideoCallPresenter.showIncomingVideo(videoState, state);
        mPhotoLarge.setVisibility(showContactPhoto ? View.VISIBLE : View.GONE);

        // Check if the call subject is showing -- if it is, we want to bypass showing the call
        // state.
        boolean isSubjectShowing = mCallSubject.getVisibility() == View.VISIBLE;

        if (TextUtils.equals(callStateLabel.getCallStateLabel(), mCallStateLabel.getText())
                /// M: For ALPS02036232, add this filter then can update
                // callstateIcon if icon changed. @{
                && !isCallStateIconChanged(callStateIcon)
                && !isSubjectShowing) {
                /// @}
            // Nothing to do if the labels are the same
            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED) {
                mCallStateLabel.clearAnimation();
                mCallStateIcon.clearAnimation();
            }
            return;
        }

        if (isSubjectShowing) {
            changeCallStateLabel(null);
            callStateIcon = null;
        } else {
            // Update the call state label and icon.
            setCallStateLabel(callStateLabel);
        }

        if (!TextUtils.isEmpty(callStateLabel.getCallStateLabel())) {
            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED) {
                mCallStateLabel.clearAnimation();
            } else {
                mCallStateLabel.startAnimation(mPulseAnimation);
            }
        } else {
            mCallStateLabel.clearAnimation();
        }

        if (callStateIcon != null) {
            mCallStateIcon.setVisibility(View.VISIBLE);
            // Invoke setAlpha(float) instead of setAlpha(int) to set the view's alpha. This is
            // needed because the pulse animation operates on the view alpha.
            mCallStateIcon.setAlpha(1.0f);
            mCallStateIcon.setImageDrawable(callStateIcon);

            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED
                    || TextUtils.isEmpty(callStateLabel.getCallStateLabel())) {
                mCallStateIcon.clearAnimation();
            } else {
                mCallStateIcon.startAnimation(mPulseAnimation);
            }

            if (callStateIcon instanceof AnimationDrawable) {
                ((AnimationDrawable) callStateIcon).start();
            }
        } else {
            mCallStateIcon.clearAnimation();

            // Invoke setAlpha(float) instead of setAlpha(int) to set the view's alpha. This is
            // needed because the pulse animation operates on the view alpha.
            mCallStateIcon.setAlpha(0.0f);
            mCallStateIcon.setVisibility(View.GONE);
            /**
             * M: [ALPS01841247]Once the ImageView was shown, it would show again even when
             * setVisibility(GONE). This is caused by View system, when complex interaction
             * combined by Visibility/Animation/Alpha. This root cause need further discussion.
             * As a solution, set the drawable to null can fix this specific problem of
             * ALPS01841247 directly.
             */
            mCallStateIcon.setImageDrawable(null);
        }

        /// M: fix CR:ALPS02583825,after SRVCC,display VT icon,move logic to front@{
        /*if (VideoUtils.isVideoCall(videoState)
                || (state == Call.State.ACTIVE && sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_UPGRADE_RESPONSE)) {
            mCallStateVideoCallIcon.setVisibility(View.VISIBLE);
        } else {
            mCallStateVideoCallIcon.setVisibility(View.GONE);
        }*/
        ///@}
    }

    private void setCallStateLabel(CallStateLabel callStateLabel) {
        Log.v(this, "setCallStateLabel : label = " + callStateLabel.getCallStateLabel());

        if (callStateLabel.isAutoDismissing()) {
            mCallStateLabelResetPending = true;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.v(this, "restoringCallStateLabel : label = " +
                            mPostResetCallStateLabel);
                    changeCallStateLabel(mPostResetCallStateLabel);
                    mCallStateLabelResetPending = false;
                }
            }, CALL_STATE_LABEL_RESET_DELAY_MS);

            changeCallStateLabel(callStateLabel.getCallStateLabel());
        } else {
            // Keep track of the current call state label; used when resetting auto dismissing
            // call state labels.
            mPostResetCallStateLabel = callStateLabel.getCallStateLabel();

            if (!mCallStateLabelResetPending) {
                changeCallStateLabel(callStateLabel.getCallStateLabel());
            }
        }
    }

    private void changeCallStateLabel(CharSequence callStateLabel) {
        Log.v(this, "changeCallStateLabel : label = " + callStateLabel);
        if (!TextUtils.isEmpty(callStateLabel)) {
            mCallStateLabel.setText(callStateLabel);
            mCallStateLabel.setAlpha(1);
            mCallStateLabel.setVisibility(View.VISIBLE);
        } else {
            Animation callStateLabelAnimation = mCallStateLabel.getAnimation();
            if (callStateLabelAnimation != null) {
                callStateLabelAnimation.cancel();
            }
            mCallStateLabel.setText(null);
            mCallStateLabel.setAlpha(0);
            mCallStateLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setCallbackNumber(String callbackNumber, boolean isEmergencyCall) {
        if (mInCallMessageLabel == null) {
            return;
        }

        if (TextUtils.isEmpty(callbackNumber)) {
            mInCallMessageLabel.setVisibility(View.GONE);
            return;
        }

        // TODO: The new Locale-specific methods don't seem to be working. Revisit this.
        callbackNumber = PhoneNumberUtils.formatNumber(callbackNumber);

        int stringResourceId = isEmergencyCall ? R.string.card_title_callback_number_emergency
                : R.string.card_title_callback_number;

        String text = getString(stringResourceId, callbackNumber);
        mInCallMessageLabel.setText(text);

        mInCallMessageLabel.setVisibility(View.VISIBLE);
    }

    /**
     * Sets and shows the call subject if it is not empty.  Hides the call subject otherwise.
     *
     * @param callSubject The call subject.
     */
    @Override
    public void setCallSubject(String callSubject) {
        boolean showSubject = !TextUtils.isEmpty(callSubject);

        mCallSubject.setVisibility(showSubject ? View.VISIBLE : View.GONE);
        if (showSubject) {
            mCallSubject.setText(callSubject);
        } else {
            mCallSubject.setText(null);
        }
    }

    public boolean isAnimating() {
        return mIsAnimating;
    }

    private void showInternetCallLabel(boolean show) {
        if (show) {
            final String label = getView().getContext().getString(
                    R.string.incall_call_type_label_sip);
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(label);
        } else {
            mCallTypeLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setPrimaryCallElapsedTime(boolean show, long duration) {
        if (show) {
            if (mElapsedTime.getVisibility() != View.VISIBLE) {
                AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
            }
            String callTimeElapsed = DateUtils.formatElapsedTime(duration / 1000);
            mElapsedTime.setText(callTimeElapsed);

            String durationDescription =
                    InCallDateUtils.formatDuration(getView().getContext(), duration);
            mElapsedTime.setContentDescription(
                    !TextUtils.isEmpty(durationDescription) ? durationDescription : null);
        } else {
            // hide() animation has no effect if it is already hidden.
            AnimUtils.fadeOut(mElapsedTime, AnimUtils.DEFAULT_DURATION);
        }
    }

    /**
     * Set all the ImageViews to the same photo. Currently there are 2 photo views: the large one
     * (which fills about the bottom half of the screen) and the small one, which displays as a
     * circle next to the primary contact info. This method does not handle whether the ImageView
     * is shown or not.
     *
     * @param photo The photo to set for the image views.
     */
    private void setDrawableToImageViews(Drawable photo) {
        if (photo == null) {
            photo = ContactInfoCache.getInstance(getView().getContext())
                            .getDefaultContactPhotoDrawable();
        }

        if (mPrimaryPhotoDrawable == photo){
            return;
        }
        mPrimaryPhotoDrawable = photo;

        mPhotoLarge.setImageDrawable(photo);

        // Modify the drawable to be round for the smaller ImageView.
        Bitmap bitmap = drawableToBitmap(photo);
        if (bitmap != null) {
            final RoundedBitmapDrawable drawable =
                    RoundedBitmapDrawableFactory.create(getResources(), bitmap);
            drawable.setAntiAlias(true);
            drawable.setCornerRadius(bitmap.getHeight() / 2);
            photo = drawable;
        }
        mPhotoSmall.setImageDrawable(photo);
    }

    /**
     * Helper method for image view to handle animations.
     *
     * @param view The image view to show or hide.
     * @param isVisible {@code true} if we want to show the image, {@code false} to hide it.
     */
    private void showImageView(ImageView view, boolean isVisible) {
        if (view.getDrawable() == null) {
            if (isVisible) {
                AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
            }
        } else {
            // Cross fading is buggy and not noticeable due to the multiple calls to this method
            // that switch drawables in the middle of the cross-fade animations. Just show the
            // photo directly instead.
            view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Converts a drawable into a bitmap.
     *
     * @param drawable the drawable to be converted.
     */
    public static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                // Needed for drawables that are just a colour.
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            }

            Log.i(TAG, "Created bitmap with width " + bitmap.getWidth() + ", height "
                    + bitmap.getHeight());

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return bitmap;
    }

    /**
     * Gets the call state label based on the state of the call or cause of disconnect.
     *
     * Additional labels are applied as follows:
     *         1. All outgoing calls with display "Calling via [Provider]".
     *         2. Ongoing calls will display the name of the provider.
     *         3. Incoming calls will only display "Incoming via..." for accounts.
     *         4. Video calls, and session modification states (eg. requesting video).
     *         5. Incoming and active Wi-Fi calls will show label provided by hint.
     *
     * TODO: Move this to the CallCardPresenter.
     */
    private CallStateLabel getCallStateLabelFromState(int state, int videoState,
            int sessionModificationState, DisconnectCause disconnectCause, String label,
            boolean isGatewayCall, boolean isWifi, boolean isConference, boolean isWorkCall) {
        final Context context = getView().getContext();
        CharSequence callStateLabel = null;  // Label to display as part of the call banner

        boolean hasSuggestedLabel = label != null;
        boolean isAccount = hasSuggestedLabel && !isGatewayCall;
        boolean isAutoDismissing = false;

        switch  (state) {
            case Call.State.IDLE:
                // "Call state" is meaningless in this state.
                break;
            case Call.State.ACTIVE:
                // We normally don't show a "call state label" at all in this state
                // (but we can use the call state label to display the provider name).
                /// M:fix ALPS02503808, no need to show connection label if any video request. @{
                /*
                Google code:
                if ((isAccount || isWifi || isConference) && hasSuggestedLabel) {
                 */
                if ((isAccount || isWifi || isConference) && hasSuggestedLabel
                        && sessionModificationState == Call.SessionModificationState.NO_REQUEST) {
                /// @}
                    callStateLabel = label;
                } else if (sessionModificationState
                        == Call.SessionModificationState.REQUEST_REJECTED) {
                    callStateLabel = context.getString(R.string.card_title_video_call_rejected);
                    isAutoDismissing = true;
                } else if (sessionModificationState
                        == Call.SessionModificationState.REQUEST_FAILED) {
                    callStateLabel = context.getString(R.string.card_title_video_call_error);
                    isAutoDismissing = true;
                } else if (sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_UPGRADE_RESPONSE) {
                    callStateLabel = context.getString(R.string.card_title_video_call_requesting);
                } else if (sessionModificationState
                        == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
                    /// M: fix ALPS02493295, modify incoming video call request state label,
                    // Google String:card_title_video_call_requesting. @{
                    callStateLabel = context.getString(R.string
                            .notification_requesting_video_call);
                    // @}
                    callStateLabel = appendCountdown(callStateLabel);
                } else if (VideoUtils.isVideoCall(videoState)) {
                    callStateLabel = context.getString(R.string.card_title_video_call);
                }
                break;
            case Call.State.ONHOLD:
                callStateLabel = context.getString(R.string.card_title_on_hold);
                break;
            case Call.State.CONNECTING:
            case Call.State.DIALING:
                if (hasSuggestedLabel && !isWifi) {
                    callStateLabel = context.getString(R.string.calling_via_template, label);
                } else {
                    callStateLabel = context.getString(R.string.card_title_dialing);
                }
                break;
            case Call.State.REDIALING:
                callStateLabel = context.getString(R.string.card_title_redialing);
                break;
            case Call.State.INCOMING:
            case Call.State.CALL_WAITING:
                /// M: [VoLTE conference]incoming volte conference @{
                if (isIncomingVolteConferenceCall()) {
                    callStateLabel = context.getString(R.string.card_title_incoming_conference);
                    break;
                }
               /// @}

                if (isWifi && hasSuggestedLabel) {
                    callStateLabel = label;
                } else if (isAccount) {
                    callStateLabel = context.getString(R.string.incoming_via_template, label);
                } else if (VideoUtils.isVideoCall(videoState)) {
                    callStateLabel = context.getString(R.string.notification_incoming_video_call);
                } else {
                    callStateLabel =
                            context.getString(isWorkCall ? R.string.card_title_incoming_work_call
                                    : R.string.card_title_incoming_call);
                }
                break;
            case Call.State.DISCONNECTING:
                // While in the DISCONNECTING state we display a "Hanging up"
                // message in order to make the UI feel more responsive.  (In
                // GSM it's normal to see a delay of a couple of seconds while
                // negotiating the disconnect with the network, so the "Hanging
                // up" state at least lets the user know that we're doing
                // something.  This state is currently not used with CDMA.)
                callStateLabel = context.getString(R.string.card_title_hanging_up);
                break;
            case Call.State.DISCONNECTED:
                callStateLabel = disconnectCause.getLabel();
                // M:fix CR:ALPS02584915,UI show error when merge conference call.
                if (TextUtils.isEmpty(callStateLabel) && !IMS_MERGED_SUCCESSFULLY.equals
                        (disconnectCause.getReason())) {
                    Log.d(this," disconnect reason is not ims merged successfully");
                    callStateLabel = context.getString(R.string.card_title_call_ended);
                }
                break;
            case Call.State.CONFERENCED:
                callStateLabel = context.getString(R.string.card_title_conf_call);
                break;
            default:
                Log.wtf(this, "updateCallStateWidgets: unexpected call: " + state);
        }
        return new CallStateLabel(callStateLabel, isAutoDismissing);
    }

    private void initializeSecondaryCallInfo(boolean hasProvider) {
        // mSecondaryCallName is initialized here (vs. onViewCreated) because it is inaccessible
        // until mSecondaryCallInfo is inflated in the call above.
        if (mSecondaryCallName == null) {
            mSecondaryCallName = (TextView) getView().findViewById(R.id.secondaryCallName);
            mSecondaryCallConferenceCallIcon =
                    getView().findViewById(R.id.secondaryCallConferenceCallIcon);
            mSecondaryCallVideoCallIcon =
                    getView().findViewById(R.id.secondaryCallVideoCallIcon);
        }

        if (mSecondaryCallProviderLabel == null && hasProvider) {
            mSecondaryCallProviderInfo.setVisibility(View.VISIBLE);
            mSecondaryCallProviderLabel = (TextView) getView()
                    .findViewById(R.id.secondaryCallProviderLabel);
        }
    }

    public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT) {
            // Indicate this call is in active if no label is provided. The label is empty when
            // the call is in active, not in other status such as onhold or dialing etc.
            if (!mCallStateLabel.isShown() || TextUtils.isEmpty(mCallStateLabel.getText())) {
                event.getText().add(
                        TextUtils.expandTemplate(
                                getResources().getText(R.string.accessibility_call_is_active),
                                mPrimaryName.getText()));
            } else {
                dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
                dispatchPopulateAccessibilityEvent(event, mPrimaryName);
                dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
                dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
            }
            return;
        }
        dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
        dispatchPopulateAccessibilityEvent(event, mPrimaryName);
        dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
        dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallName);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallProviderLabel);

        return;
    }

    @Override
    public void sendAccessibilityAnnouncement() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getView() != null && getView().getParent() != null &&
                        isAccessibilityEnabled(getContext())) {
                    AccessibilityEvent event = AccessibilityEvent.obtain(
                            AccessibilityEvent.TYPE_ANNOUNCEMENT);
                    dispatchPopulateAccessibilityEvent(event);
                    getView().getParent().requestSendAccessibilityEvent(getView(), event);
                }
            }

            private boolean isAccessibilityEnabled(Context context) {
                AccessibilityManager accessibilityManager =
                        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
                return accessibilityManager != null && accessibilityManager.isEnabled();

            }
        }, ACCESSIBILITY_ANNOUNCEMENT_DELAY_MS);
    }

    @Override
    public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
        /// MTK add this log. @{
        Log.d(this, "setEndCallButtonEnabled enabled = " + enabled
                + ", animate = " + animate + "; old state = "
                + mFloatingActionButton.isEnabled() + ", vs =",
                mFloatingActionButtonContainer.getVisibility());
        /// @}
        /// M: not show endcall btn when animation not end for ALPS02159995 @{
        if(enabled == true && mFloatingActionButton.isEnabled()
                && mFloatingActionButtonContainer.getVisibility() == View.GONE){
            mFloatingActionButton.setEnabled(false);
        }
        /// @}
        if (enabled != mFloatingActionButton.isEnabled()) {
            if (animate) {
                if (enabled) {
                    mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
                } else {
                    mFloatingActionButtonController.scaleOut();
                }
            } else {
                if (enabled) {
                    mFloatingActionButtonContainer.setScaleX(1);
                    mFloatingActionButtonContainer.setScaleY(1);
                    mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                } else {
                    mFloatingActionButtonContainer.setVisibility(View.GONE);
                }
            }
            mFloatingActionButton.setEnabled(enabled);
            updateFabPosition();
        }
    }

    /**
     * Changes the visibility of the HD audio icon.
     *
     * @param visible {@code true} if the UI should show the HD audio icon.
     */
    @Override
    public void showHdAudioIndicator(boolean visible) {
        mHdAudioIcon.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Changes the visibility of the forward icon.
     *
     * @param visible {@code true} if the UI should show the forward icon.
     */
    @Override
    public void showForwardIndicator(boolean visible) {
        mForwardIcon.setVisibility(visible ? View.VISIBLE : View.GONE);
    }


    /**
     * Changes the visibility of the "manage conference call" button.
     *
     * @param visible Whether to set the button to be visible or not.
     */
    @Override
    public void showManageConferenceCallButton(boolean visible) {
        mManageConferenceCallButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Determines the current visibility of the manage conference button.
     *
     * @return {@code true} if the button is visible.
     */
    @Override
    public boolean isManageConferenceVisible() {
        return mManageConferenceCallButton.getVisibility() == View.VISIBLE;
    }

    /**
     * Determines the current visibility of the call subject.
     *
     * @return {@code true} if the subject is visible.
     */
    @Override
    public boolean isCallSubjectVisible() {
        return mCallSubject.getVisibility() == View.VISIBLE;
    }

    /**
     * Get the overall InCallUI background colors and apply to call card.
     */
    @Override
    public void updateColors() {
        MaterialPalette themeColors = InCallPresenter.getInstance().getThemeColors();

        if (mCurrentThemeColors != null && mCurrentThemeColors.equals(themeColors)) {
            return;
        }
        if (themeColors == null) {
            return;
        }
        /// M:fix CR:ALPS02321720, JE about ColorDrawable can not be cast
        /// to GradientDrawable. @{
        if (getResources().getBoolean(R.bool.is_layout_landscape)
            && mPrimaryCallCardContainer.getBackground() instanceof GradientDrawable) {
        /// @}
            final GradientDrawable drawable =
                    (GradientDrawable) mPrimaryCallCardContainer.getBackground();
            drawable.setColor(themeColors.mPrimaryColor);
        } else {
            mPrimaryCallCardContainer.setBackgroundColor(themeColors.mPrimaryColor);
        }
        mCallButtonsContainer.setBackgroundColor(themeColors.mPrimaryColor);
        mCallSubject.setTextColor(themeColors.mPrimaryColor);
        mContactContext.setBackgroundColor(themeColors.mPrimaryColor);
        //TODO: set color of message text in call context "recent messages" to be the theme color.

        mCurrentThemeColors = themeColors;
    }

    private void dispatchPopulateAccessibilityEvent(AccessibilityEvent event, View view) {
        if (view == null) return;
        final List<CharSequence> eventText = event.getText();
        int size = eventText.size();
        view.dispatchPopulateAccessibilityEvent(event);
        // if no text added write null to keep relative position
        if (size == eventText.size()) {
            eventText.add(null);
        }
    }

    @Override
    public void animateForNewOutgoingCall() {
        Log.d(this, "[animateForNewOutgoingCall]");
        final ViewGroup parent = (ViewGroup) mPrimaryCallCardContainer.getParent();

        final ViewTreeObserver observer = getView().getViewTreeObserver();

        /**
         * M: [ALPS02494688] Seldom, the onGlobalLayout might not be called. As a result,
         * the CallCardFragment would stay in animating state forever.
         * Ref. InCallPresenter.onCallListChange(), it would stop responding to any call
         * state change if CallCardFragment keep animating. To avoid this seldom issue,
         * we move this line to where the animation.start() was called.
         * google default code:
         * mIsAnimating = true;
         */

        observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final ViewTreeObserver observer = getView().getViewTreeObserver();
                if (!observer.isAlive()) {
                    return;
                }
                observer.removeOnGlobalLayoutListener(this);

                final LayoutIgnoringListener listener = new LayoutIgnoringListener();
                mPrimaryCallCardContainer.addOnLayoutChangeListener(listener);

                // Prepare the state of views before the slide animation
                final int originalHeight = mPrimaryCallCardContainer.getHeight();
                mPrimaryCallCardContainer.setTag(R.id.view_tag_callcard_actual_height,
                        originalHeight);
                mPrimaryCallCardContainer.setBottom(parent.getHeight());

                // Set up FAB.
                mFloatingActionButtonContainer.setVisibility(View.GONE);
                mFloatingActionButtonController.setScreenWidth(parent.getWidth());

                mCallButtonsContainer.setAlpha(0);
                mCallStateLabel.setAlpha(0);
                mPrimaryName.setAlpha(0);
                mCallTypeLabel.setAlpha(0);
                mCallNumberAndLabel.setAlpha(0);

                assignTranslateAnimation(mCallStateLabel, 1);
                assignTranslateAnimation(mCallStateIcon, 1);
                assignTranslateAnimation(mPrimaryName, 2);
                assignTranslateAnimation(mCallNumberAndLabel, 3);
                assignTranslateAnimation(mCallTypeLabel, 4);
                assignTranslateAnimation(mCallButtonsContainer, 5);

                final Animator animator = getShrinkAnimator(parent.getHeight(), originalHeight);

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        /// M: add for monitor call card animation process
                        Log.d(this, "[onAnimationEnd] end of shrink animation.");
                        mPrimaryCallCardContainer.setTag(R.id.view_tag_callcard_actual_height,
                                null);
                        setViewStatePostAnimation(listener);
                        mIsAnimating = false;
                        InCallPresenter.getInstance().onShrinkAnimationComplete();

                        /// M: fix ALPS02302284. update floating end button to animate after
                        /// the primary call card exit shrink animation from bottom to top. @{
                        updateFabPosition();
                        /// @}
                    }
                });
                /**
                 * M: [ALPS02494688] Marking the CallCardFragment in animating state at where
                 * the animation really happened.
                 */
                Log.v(this, "[animateForNewOutgoingCall]start ShrinkAnimation");
                mIsAnimating = true;

                animator.start();
            }
        });
    }

    @Override
    public void showNoteSentToast() {
        Toast.makeText(getContext(), R.string.note_sent, Toast.LENGTH_LONG).show();
    }

    public void onDialpadVisibilityChange(boolean isShown) {
        mIsDialpadShowing = isShown;
        updateFabPosition();
    }

    private void updateFabPosition() {
        /**
         * M: skip update Fab position with animation when FAB is not visible and size is 0X0,
         * hwui will throw exception when draw view size is 0 and hardware layertype. @{
         */
        Log.d(this, "[updateFabPosition] Dialpad:" + mIsDialpadShowing
                + ",FAB dump: " + mFloatingActionButtonContainer);
        if (!mFloatingActionButtonController.isVisible()
                && (mFloatingActionButtonContainer.getWidth() == 0 &&
                mFloatingActionButtonContainer.getHeight() == 0)) {
            return;
        }
        /** @} */

        int offsetY = 0;
        if (!mIsDialpadShowing) {
            offsetY = mFloatingActionButtonVerticalOffset;
            /// M: Unnecessarily offset in Landscape and change the isShown to
            // getVisibility check for some case Visibility is change but the
            // view has not attached to the rootView so will cause the fab
            // position can not be set correct after it attached.
            if (mSecondaryCallInfo.getVisibility() == View.VISIBLE
                    && mHasLargePhoto && !mIsLandscape) {
                offsetY -= mSecondaryCallInfo.getHeight();
            }
            Log.d(this,
                    "offsetY:"
                            + offsetY
                            + ",mSecondaryHeight: "
                            + (mSecondaryCallInfo != null ? mSecondaryCallInfo
                                    .getHeight() : null));
        }
        mFloatingActionButtonController.align(
                FloatingActionButtonController.ALIGN_MIDDLE /* align base */,
                0 /* offsetX */,
                offsetY,
                true);
        mFloatingActionButtonController.resize(
                mIsDialpadShowing ? mFabSmallDiameter : mFabNormalDiameter, true);
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onResume() {
        super.onResume();
        /// M: log enhancement
        Log.d(this, "onResume...");
        // If the previous launch animation is still running, cancel it so that we don't get
        // stuck in an intermediate animation state.
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            mAnimatorSet.cancel();
        }

        mIsLandscape = getResources().getBoolean(R.bool.is_layout_landscape);
        mHasLargePhoto = getResources().getBoolean(R.bool.has_large_photo);

        final ViewGroup parent = ((ViewGroup) mPrimaryCallCardContainer.getParent());
        final ViewTreeObserver observer = parent.getViewTreeObserver();
        parent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ViewTreeObserver viewTreeObserver = observer;
                if (!viewTreeObserver.isAlive()) {
                    viewTreeObserver = parent.getViewTreeObserver();
                }
                viewTreeObserver.removeOnGlobalLayoutListener(this);
                mFloatingActionButtonController.setScreenWidth(parent.getWidth());
                updateFabPosition();
            }
        });

        updateColors();
    }

    /**
     * Adds a global layout listener to update the FAB's positioning on the next layout. This allows
     * us to position the FAB after the secondary call info's height has been calculated.
     */
    private void updateFabPositionForSecondaryCallInfo() {
        mSecondaryCallInfo.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer = mSecondaryCallInfo.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return;
                        }
                        observer.removeOnGlobalLayoutListener(this);

                        onDialpadVisibilityChange(mIsDialpadShowing);
                    }
                });
    }

    /**
     * Animator that performs the upwards shrinking animation of the blue call card scrim.
     * At the start of the animation, each child view is moved downwards by a pre-specified amount
     * and then translated upwards together with the scrim.
     */
    private Animator getShrinkAnimator(int startHeight, int endHeight) {
        final ObjectAnimator shrinkAnimator =
                ObjectAnimator.ofInt(mPrimaryCallCardContainer, "bottom", startHeight, endHeight);
        shrinkAnimator.setDuration(mShrinkAnimationDuration);
        shrinkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mFloatingActionButton.setEnabled(true);
            }
        });
        shrinkAnimator.setInterpolator(AnimUtils.EASE_IN);
        return shrinkAnimator;
    }

    private void assignTranslateAnimation(View view, int offset) {
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.buildLayer();
        view.setTranslationY(mTranslationOffset * offset);
        view.animate().translationY(0).alpha(1).withLayer()
                .setDuration(mShrinkAnimationDuration).setInterpolator(AnimUtils.EASE_IN);
    }

    private void setViewStatePostAnimation(View view) {
        view.setTranslationY(0);
        view.setAlpha(1);
    }

    private void setViewStatePostAnimation(OnLayoutChangeListener layoutChangeListener) {
        setViewStatePostAnimation(mCallButtonsContainer);
        setViewStatePostAnimation(mCallStateLabel);
        setViewStatePostAnimation(mPrimaryName);
        setViewStatePostAnimation(mCallTypeLabel);
        setViewStatePostAnimation(mCallNumberAndLabel);
        setViewStatePostAnimation(mCallStateIcon);

        mPrimaryCallCardContainer.removeOnLayoutChangeListener(layoutChangeListener);

        /// M: For ALPS01761179 & ALPS01794859, don't show end button if state
        // is incoming or disconnected. @{
        final Call call = CallList.getInstance().getFirstCall();
        if (call != null) {
            int state = call.getState();
            if (!Call.State.isIncoming(state) && Call.State.isConnectingOrConnected(state)) {
                mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
                Log.d(this, "setViewStatePostAnimation end.");
            /// M: For ALPS01828090 disable end call button when end button do not show under
            // call state is disconnected.
            // in order to setEndCallButtonEnabled() can get right mFloatingActionButton state
            // to show end button to other connecting or connected calls @{
            } else if (mFloatingActionButton.isEnabled()) {
                Log.i(this, "mFloatingActionButton.setEnabled(false) when end button do not show");
                mFloatingActionButton.setEnabled(false);
            }
            /// @}
        }
        /// @}
    }

    private final class LayoutIgnoringListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v,
                int left,
                int top,
                int right,
                int bottom,
                int oldLeft,
                int oldTop,
                int oldRight,
                int oldBottom) {
            v.setLeft(oldLeft);
            v.setRight(oldRight);
            v.setTop(oldTop);
            v.setBottom(oldBottom);
        }
    }

    /// M: For second call color @{
    private int mCurrentSecondCallColor;
    /// @}

    // Fix ALPS01759672. @{
    @Override
    public void setSecondaryEnabled(boolean enabled) {
        if (mSecondaryCallInfo != null) {
            mSecondaryCallInfo.setEnabled(enabled);
        }
    }

    // @}

    /// M: For second call color @{
    /**
     * Get the second call color and apply to second call provider label.
     */
    public void updateSecondCallColor() {
        int secondCallColor = getPresenter().getSecondCallColor();
        if (mCurrentSecondCallColor == secondCallColor) {
            return;
        }
        if (mSecondaryCallProviderLabel != null) {
            mSecondaryCallProviderLabel.setTextColor(secondCallColor);
        }
        mCurrentSecondCallColor = secondCallColor;
    }

    /**
     * M: check whether the callStateIcon has no change.
     * @param callStateIcon call state icon
     * @return true if no change
     */
    private boolean isCallStateIconChanged(Drawable callStateIcon) {
        return (mCallStateIcon.getDrawable() != null && callStateIcon == null)
                || (mCallStateIcon.getDrawable() == null && callStateIcon != null);
    }
    /// @}

    /**
     * M: check incoming call conference call or not.
     * @return
     */
    private boolean isIncomingVolteConferenceCall() {
        Call call = CallList.getInstance().getIncomingCall();
        return InCallUIVolteUtils.isIncomingVolteConferenceCall(call);
    }

    /// M: [Voice Record]recording indication icon @{
    private ImageView mVoiceRecorderIcon;

    private void initVoiceRecorderIcon(View view) {
        mVoiceRecorderIcon = (ImageView) view.findViewById(R.id.voiceRecorderIcon);
        mVoiceRecorderIcon.setImageResource(R.drawable.voice_record);
        mVoiceRecorderIcon.setVisibility(View.INVISIBLE);
    }

    @Override
    public void updateVoiceRecordIcon(boolean show) {
        mVoiceRecorderIcon.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        AnimationDrawable ad = (AnimationDrawable) mVoiceRecorderIcon.getDrawable();
        if (ad != null) {
            if (show && !ad.isRunning()) {
                ad.start();
            } else if (!show && ad.isRunning()) {
                ad.stop();
            }
        }
        /// M:[RCS] plugin API @{
        ExtensionManager.getRCSeCallCardExt().updateVoiceRecordIcon(show);
        /// @}
    }
    /// @}

    /**
     * M: [CTA]CTA required that in Simplified Chinese, the text label of the secondary/tertiary
     * call should be changed to another string rather than google default.
     * @return the right resId CTS required.
     */
    private int getCtaSpecificOnHoldResId() {
        Locale currentLocale = getActivity().getResources().getConfiguration().locale;
        if (Locale.SIMPLIFIED_CHINESE.getCountry().equals(currentLocale.getCountry())
                && Locale.SIMPLIFIED_CHINESE.getLanguage().equals(currentLocale.getLanguage())) {
            return R.string.onHold_cta;
        }
        return R.string.onHold;
    }

    private CharSequence appendCountdown(CharSequence originalText) {
        long countdown = getPresenter().getCountdown();
        if (countdown < 0) {
            return originalText;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(originalText).append(" (").append(countdown).append(")");
        return sb.toString();
    }

    /**
     * M: Determines the height of the call card.
     *
     * @return The height of the call card.
     */
    public float getCallCardViewHeight() {
        return getView().getHeight();
    }

    /**
     * M: Determines the width of the call card.
     *
     * @return The width of the call card.
     */
    public float getCallCardViewWidth() {
        return getView().getWidth();
    }

    /**
     * M: get whether VideoDisplayView is visible .
     *
     * @return false means can't visible.
     */
    @Override
    public boolean isVideoDisplayViewVisible() {
        if(getView() == null) {
            return false;
        }
        final View videoView = getView().findViewById(R.id.incomingVideo);
        if(videoView == null) {
            return false;
        }
        return videoView.getVisibility() == View.VISIBLE ;
    }

    /**
     * M: set photo visible or not .
     */
    @Override
    public void setPhotoVisible(boolean visible) {
        if(mPhotoLarge == null) {
            Log.d(this, "[setPhotoVisible]mPhotoLarge is null return");
            return ;
        }
        mPhotoLarge.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * M: [Video call]In landscape mode, the End button should placed to somewhere
     * no covering CallCard and Preview.
     * @return the offset from middle.
     */
    private int getEndButtonOffsetXFromMiddle() {
        // For port, the end button would be in the middle of the screen.
        if (!mIsLandscape) {
            return 0;
        }
        // For land, the end button would be placed a little right to the middle to
        // avoid covering neither CallCard nor Preview.
        // We decided to place the left edge of the end button to the middle.
        // refer to the updateFabPosition() method.
        int endButtonSize = mIsDialpadShowing ? mFabSmallDiameter : mFabNormalDiameter;
        return endButtonSize / 2;
    }

    /**
     * M: [1A1H2W]when enter or leave 2W, update the mSecondaryCallInfo view position.
     */
    private void updateAnswerViewPosition() {
        int bottomPadding = 0;
        if (CallList.getInstance().getSecondaryIncomingCall() != null) {
            bottomPadding = mSecondaryCallInfo.getHeight();
        }

        View answerView = getView() != null ?
                getView().findViewById(R.id.answer_and_dialpad_container) : null;
        if (answerView == null) {
            return;
        }

        int oldBottomPadding = answerView.getPaddingBottom();
        if (bottomPadding != oldBottomPadding) {
            answerView.setPadding(answerView.getPaddingLeft(), answerView.getPaddingTop(),
                    answerView.getPaddingRight(), bottomPadding);
            Log.d(this, "updateSecondaryCallInfoPosition, bottomPadding = " + bottomPadding);
            answerView.invalidate();
        }
    }

    //*/ freeme.zhaozehong, 26/07/17. for freemeOS
    @Override
    public void setSimIdAndLabel(int simid, CharSequence simlabel) {
        // ignore
    }

    @Override
    public void onDeviceOrientationChanged(int orientation) {
        // ignore
    }

    @Override
    public void setFirst(String name, boolean nameIsNumber, String label, String providerLabel,
                         boolean isConference) {
        // ignore
    }
    //*/
}
