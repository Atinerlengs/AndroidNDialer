/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2006 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.incallui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnCancelListener;
/// M: For Recording @{
import android.content.DialogInterface.OnDismissListener;
/// @}
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Trace;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.animation.AnimationListenerAdapter;
import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.R;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.incallui.Call.State;
import com.android.incallui.util.AccessibilityUtil;
/// M: DMLock, PPL @}
import com.mediatek.incallui.DMLockBroadcastReceiver;
import com.mediatek.incallui.InCallUtils;
/// @}

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// M: add for plug in. @{
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.ext.IInCallExt;
/// @}
/// M: For Recording @{
import com.mediatek.incallui.recorder.PhoneRecorderUtils;
import com.mediatek.incallui.wfc.InCallUiWfcUtils;
/// @}
//*/ freeme.zhaozehong, 27/07/17. for freemeOS
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import com.freeme.incallui.FreemeCallButtonFragment;
import com.freeme.incallui.FreemeCallCardFragment;
import com.freeme.incallui.FreemeDialpadFragment;
import com.freeme.incallui.FreemeGlowPadAnswerFragment;
//*/

/**
 * Main activity that the user interacts with while in a live call.
 */
/*/ freeme.zhaozehong, 25/07/17. for freemeOS
public class InCallActivity extends TransactionSafeActivity implements FragmentDisplayManager {
/*/
public class InCallActivity extends TransactionSafeActivity implements FragmentDisplayManager,
        FreemeGlowPadAnswerFragment.InComingUIShowListener {
//*/

    public static final String TAG = InCallActivity.class.getSimpleName();

    public static final String SHOW_DIALPAD_EXTRA = "InCallActivity.show_dialpad";
    public static final String DIALPAD_TEXT_EXTRA = "InCallActivity.dialpad_text";
    public static final String NEW_OUTGOING_CALL_EXTRA = "InCallActivity.new_outgoing_call";
    public static final String ENABLE_SCREEN_TIMEOUT = "InCallActivity.enable_screen_timeout";

    private static final String TAG_DIALPAD_FRAGMENT = "tag_dialpad_fragment";
    private static final String TAG_CONFERENCE_FRAGMENT = "tag_conference_manager_fragment";
    private static final String TAG_CALLCARD_FRAGMENT = "tag_callcard_fragment";
    private static final String TAG_ANSWER_FRAGMENT = "tag_answer_fragment";
    private static final String TAG_SELECT_ACCT_FRAGMENT = "tag_select_acct_fragment";

    private static final int DIALPAD_REQUEST_NONE = 1;
    private static final int DIALPAD_REQUEST_SHOW = 2;
    private static final int DIALPAD_REQUEST_HIDE = 3;

    /**
     * This is used to relaunch the activity if resizing beyond which it needs to load different
     * layout file.
     * M: change 400 to adapter more devices, AOSP default 500.
     */
    private static final int SCREEN_HEIGHT_RESIZE_THRESHOLD = 400;

    /*/ freeme.zhaozehong, 24/07/17. for freemeOS
    private CallButtonFragment mCallButtonFragment;
    private CallCardFragment mCallCardFragment;
    private AnswerFragment mAnswerFragment;
    private DialpadFragment mDialpadFragment;
    /*/
    private FreemeCallButtonFragment mCallButtonFragment;
    private FreemeCallCardFragment mCallCardFragment;
    private AnswerFragment mAnswerFragment;
    private FreemeDialpadFragment mDialpadFragment;
    //*/
    private ConferenceManagerFragment mConferenceManagerFragment;
    private FragmentManager mChildFragmentManager;

    private boolean mIsVisible;
    private AlertDialog mDialog;
    private InCallOrientationEventListener mInCallOrientationEventListener;

    /**
     * Used to indicate whether the dialpad should be hidden or shown {@link #onResume}.
     * {@code #DIALPAD_REQUEST_SHOW} indicates that the dialpad should be shown.
     * {@code #DIALPAD_REQUEST_HIDE} indicates that the dialpad should be hidden.
     * {@code #DIALPAD_REQUEST_NONE} indicates no change should be made to dialpad visibility.
     */
    private int mShowDialpadRequest = DIALPAD_REQUEST_NONE;

    /**
     * Use to determine if the dialpad should be animated on show.
     */
    private boolean mAnimateDialpadOnShow;

    /**
     * Use to determine the DTMF Text which should be pre-populated in the dialpad.
     */
    private String mDtmfText;

    /**
     * Use to pass parameters for showing the PostCharDialog to {@link #onResume}
     */
    private boolean mShowPostCharWaitDialogOnResume;
    private String mShowPostCharWaitDialogCallId;
    private String mShowPostCharWaitDialogChars;

    private boolean mIsLandscape;
    private Animation mSlideIn;
    private Animation mSlideOut;
    private boolean mDismissKeyguard = false;
    /// M: current configuration
    private Configuration mCurrentConfig;
    /// M: flag cancel account selection
    private boolean mNeedCancelSelection = true;

    AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            showFragment(TAG_DIALPAD_FRAGMENT, false, true);
        }
    };

    private OnTouchListener mDispatchTouchEventListener;

    private SelectPhoneAccountListener mSelectAcctListener = new SelectPhoneAccountListener() {
        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                boolean setDefault) {
            InCallPresenter.getInstance().handleAccountSelection(selectedAccountHandle,
                    setDefault);
        }

        @Override
        public void onDialogDismissed() {
            /// M: if configuration change need recreate activity, don't cancel account selection @{
            if (mNeedCancelSelection) {
                InCallPresenter.getInstance().cancelAccountSelection();
            }
            mNeedCancelSelection = true;
            /// @}
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        Log.d(this, "onCreate()...  this = " + this);

        super.onCreate(icicle);

        /// M: set the window flags @{
        /// Original code:
        /*
        // set this flag so this activity will stay in front of the keyguard
        // Have the WindowManager filter out touch events that are "too fat".
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;

        getWindow().addFlags(flags);
        */
        setWindowFlag();
        /// @}

        //*/ freeme.zhaozehong, 20171013. set the transparent statusBar
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        //*/

        // Setup action bar for the conference call manager.
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.hide();
        }

        // TODO(klp): Do we need to add this back when prox sensor is not available?
        // lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;

        /// M: for plugin @{
        ExtensionManager.getRCSeInCallExt().onCreate(icicle, this, CallList.getInstance());
        /// @}

        setContentView(R.layout.incall_screen);

        /// M: DM lock Feature @{
        mDMLockReceiver = DMLockBroadcastReceiver.getInstance(this);
        mDMLockReceiver.register(this);
        /// @}

        internalResolveIntent(getIntent());

        mIsLandscape = getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;

        final boolean isRtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                View.LAYOUT_DIRECTION_RTL;

        /*/ freeme.zhaozehong, 29/07/17. for freemeOS
        if (mIsLandscape) {
            mSlideIn = AnimationUtils.loadAnimation(this,
                    isRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
            mSlideOut = AnimationUtils.loadAnimation(this,
                    isRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
        } else {
            mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
            mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
        }
        /*/
        mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
        mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
        //*/

        mSlideIn.setInterpolator(AnimUtils.EASE_IN);
        mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

        mSlideOut.setAnimationListener(mSlideOutListener);

        // If the dialpad fragment already exists, retrieve it.  This is important when rotating as
        // we will not be able to hide or show the dialpad after the rotation otherwise.
        /*/ freeme.zhaozehong, 26/07/17. for freemeOS
        Fragment existingFragment =
                getFragmentManager().findFragmentByTag(DialpadFragment.class.getName());
        if (existingFragment != null) {
            mDialpadFragment = (DialpadFragment) existingFragment;
        }
        /*/
        Fragment existingFragment =
                getFragmentManager().findFragmentByTag(FreemeDialpadFragment.class.getName());
        if (existingFragment != null) {
            mDialpadFragment = (FreemeDialpadFragment) existingFragment;
        }
        //*/

        /// M: move up to use it when a fresh new InCall is creating
        mInCallOrientationEventListener = new InCallOrientationEventListener(this);

        if (icicle != null) {
            // If the dialpad was shown before, set variables indicating it should be shown and
            // populated with the previous DTMF text.  The dialpad is actually shown and populated
            // in onResume() to ensure the hosting CallCardFragment has been inflated and is ready
            // to receive it.
            if (icicle.containsKey(SHOW_DIALPAD_EXTRA)) {
                boolean showDialpad = icicle.getBoolean(SHOW_DIALPAD_EXTRA);
                mShowDialpadRequest = showDialpad ? DIALPAD_REQUEST_SHOW : DIALPAD_REQUEST_HIDE;
                mAnimateDialpadOnShow = false;
            }
            mDtmfText = icicle.getString(DIALPAD_TEXT_EXTRA);
            SelectPhoneAccountDialogFragment dialogFragment = (SelectPhoneAccountDialogFragment)
                    getFragmentManager().findFragmentByTag(TAG_SELECT_ACCT_FRAGMENT);
            if (dialogFragment != null) {
                dialogFragment.setListener(mSelectAcctListener);
            }
        } else {
            /// M: force reset Device Orientation on new Call
            mInCallOrientationEventListener.resetDeviceOrientation();
        }
        // mInCallOrientationEventListener = new InCallOrientationEventListener(this);

        /// M: Set current configuration @{
        mCurrentConfig = new Configuration(getResources().getConfiguration());
        /// @}
        /// M: Enhance multi-window log for debugging.
        Log.d(this, "onCreate(): exit, multi-window is "
                + getResources().getBoolean(R.bool.enable_multi_window));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        // TODO: The dialpad fragment should handle this as part of its own state
        out.putBoolean(SHOW_DIALPAD_EXTRA,
                mCallButtonFragment != null && mCallButtonFragment.isDialpadVisible());
        ///M: ALPS01855248 @{
        // override SHOW_DIALPAD_EXTRA
        // because sometimes activity is killed, the activity will be created twice
        // if the first time DialpadFragment has not enough time to show
        // this extra will be set false, the finally dialpad will not show in Phone
        if (mShowDialpadRequest == DIALPAD_REQUEST_SHOW) {
            out.putBoolean(SHOW_DIALPAD_EXTRA, true);
            mShowDialpadRequest = DIALPAD_REQUEST_NONE;
        }
        /// @}
        if (mDialpadFragment != null) {
            out.putString(DIALPAD_TEXT_EXTRA, mDialpadFragment.getDtmfText());
            ///M: ALPS01855248 @{
            // override DIALPAD_TEXT_EXTRA
            // because sometimes activity is killed, the activity will be created twice
            // if the first time DialpadFragment has not enough time to show
            // this extra will be set null, the finally dialpad will not show in Phone
            if (mDtmfText != null) {
                out.putString(DIALPAD_TEXT_EXTRA, mDtmfText);
                mDtmfText = null;
            }
            /// @}
        }
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onStart() {
        Log.d(this, "onStart()...");
        super.onStart();

        // setting activity should be last thing in setup process
        InCallPresenter.getInstance().setActivity(this);
        enableInCallOrientationEventListener(getRequestedOrientation() ==
                InCallOrientationEventListener.FULL_SENSOR_SCREEN_ORIENTATION);

        InCallPresenter.getInstance().onActivityStarted();
    }

    @Override
    protected void onResume() {
        Log.i(this, "onResume()...");
        super.onResume();

        /// M: if there is an incoming call, we need cancel the pending outgoing call and
        // show the CallCardFragment immediately, otherwise AnswerFragment wouldn't be shown @{.
        if (CallList.getInstance().getIncomingCall() != null &&
                CallList.getInstance().getWaitingForAccountCall() != null) {
            Log.d(this, "dismiss call waiting for account and for CallCardFragment to show.");
            dismissSelectAccountDialog();
            showCallCardFragment(true);
        }
        /// @}
        InCallPresenter.getInstance().setThemeColors();
        InCallPresenter.getInstance().onUiShowing(true);
        ExtensionManager.getRCSeInCallExt().onResume(this);

        // Clear fullscreen state onResume; the stored value may not match reality.
        /** M: Exit full screen too, make sure the UI in the same state. @} */
        // InCallPresenter.getInstance().clearFullscreen();
        InCallPresenter.getInstance().setFullScreen(false);
        /** @} */

        /// M: Adjust screen time out mode, which is maintained by {@link InCallPresenter},
        // InCallActivity dosen't need to know details about this @{
        InCallPresenter.getInstance().adjustScreenTimeOutMode();
        /// @}
        // If there is a pending request to show or hide the dialpad, handle that now.
        if (mShowDialpadRequest != DIALPAD_REQUEST_NONE) {
            if (mShowDialpadRequest == DIALPAD_REQUEST_SHOW) {
                // Exit fullscreen so that the user has access to the dialpad hide/show button and
                // can hide the dialpad.  Important when showing the dialpad from within dialer.
                InCallPresenter.getInstance().setFullScreen(false, true /* force */);

                /// M: [ALPS02852244] check CallButtonFragment in case of NPE
                if (mCallButtonFragment != null) {
                    mCallButtonFragment.displayDialpad(true /* show */,
                            mAnimateDialpadOnShow /* animate */);
                    mAnimateDialpadOnShow = false;
                }

                if (mDialpadFragment != null) {
                    mDialpadFragment.setDtmfText(mDtmfText);
                    mDtmfText = null;
                }
            } else {
                Log.v(this, "onResume : force hide dialpad");
                if (mDialpadFragment != null) {
                    mCallButtonFragment.displayDialpad(false /* show */, false /* animate */);
                }
            }
            mShowDialpadRequest = DIALPAD_REQUEST_NONE;
        }

        if (mShowPostCharWaitDialogOnResume) {
            showPostCharWaitDialog(mShowPostCharWaitDialogCallId, mShowPostCharWaitDialogChars);
        }

        /// M: fix ALPS01935061,Show error dialog after activity resume @{
        if (mDelayShowErrorDialogRequest) {
            showErrorDialog(mDisconnectCauseDescription);
            mDelayShowErrorDialogRequest = false;
        }
        /// @}

        /// M: fix conference manager UI issue ALPS02469553/ALPS02471277, need to
        // hide conference manager UI and show call card UI if InCallUI resume again. @{
        if (mConferenceManagerFragment != null && !mConferenceManagerFragment.isHidden()) {
            showConferenceFragment(false);
        }
        /// @}
    }

    // onPause is guaranteed to be called when the InCallActivity goes
    // in the background.
    @Override
    protected void onPause() {
        Log.d(this, "onPause()...");
        if (mDialpadFragment != null) {
            mDialpadFragment.onDialerKeyUp(null);
        }
        ExtensionManager.getRCSeInCallExt().onPause(this);
        /**
         *  M: not suitable for multi-window scenario, if there was an incoming call or
         *  waiting call, and in multi-window mode, remote canceled the call, and then
         *  the AnswerFragment would still show on screen.
         *  Move following to onStop, if onStop had been invoked, it means the activity
         *   wouldn't be seen by user anymore.
         *  @{
         */
//        InCallPresenter.getInstance().onUiShowing(false);
//        if (isFinishing()) {
//            InCallPresenter.getInstance().unsetActivity(this);
//        }
        InCallPresenter.getInstance().updateIsChangingConfigurations();
        /** @} */
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(this, "onStop()...");
        /// M: Move following from onPause @{
        InCallPresenter.getInstance().onUiShowing(false);
        if (isFinishing()) {
            InCallPresenter.getInstance().unsetActivity(this);
        }
        /// @}
        /// M: ALPS01786201. @{
        // Dismiss any dialogs we may have brought up, just to be 100%
        // sure they won't still be around when we get back here.
        dismissPendingDialogs();
        /// @}

        /// M: ALPS01855248 @{
        // postpone reset these three variables values from onResume to onStop
        mShowDialpadRequest = DIALPAD_REQUEST_NONE;
        mAnimateDialpadOnShow = false;
        mDtmfText = null;
        /// @}
        enableInCallOrientationEventListener(false);
        InCallPresenter.getInstance().updateIsChangingConfigurations();
        InCallPresenter.getInstance().onActivityStopped();
        //M: when activty stop, we should make video call full screen disbale,
        //so that we can avoid the videocall rotation full screen error.
        InCallPresenter.getInstance().notifyDisableVideoCallFullScreen();
        /// @}
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(this, "onDestroy()...  this = " + this);
        InCallPresenter.getInstance().unsetActivity(this);
        InCallPresenter.getInstance().updateIsChangingConfigurations();

        /// M: DM lock Feature. @{
        mDMLockReceiver.unregister(this);
        /// @}

        super.onDestroy();

        /// M: for plugin @{
        ExtensionManager.getRCSeInCallExt().onDestroy(this);
        /// @}
    }

    /**
     * When fragments have a parent fragment, onAttachFragment is not called on the parent
     * activity. To fix this, register our own callback instead that is always called for
     * all fragments.
     *
     * @see {@link BaseFragment#onAttach(Activity)}
     */
    @Override
    public void onFragmentAttached(Fragment fragment) {
        /*/ freeme.zhaozehong, 20170905. for freemeOS
        if (fragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) fragment;
        } else if (fragment instanceof AnswerFragment) {
            mAnswerFragment = (AnswerFragment) fragment;
        } else if (fragment instanceof CallCardFragment) {
            mCallCardFragment = (CallCardFragment) fragment;
            mChildFragmentManager = mCallCardFragment.getChildFragmentManager();
        } else if (fragment instanceof ConferenceManagerFragment) {
            mConferenceManagerFragment = (ConferenceManagerFragment) fragment;
        } else if (fragment instanceof CallButtonFragment) {
            mCallButtonFragment = (CallButtonFragment) fragment;
        }
        /*/
        if (fragment instanceof FreemeDialpadFragment) {
            mDialpadFragment = (FreemeDialpadFragment) fragment;
        } else if (fragment instanceof AnswerFragment) {
            mAnswerFragment = (AnswerFragment) fragment;
        } else if (fragment instanceof FreemeCallCardFragment) {
            mCallCardFragment = (FreemeCallCardFragment) fragment;
            mChildFragmentManager = mCallCardFragment.getChildFragmentManager();
        } else if (fragment instanceof ConferenceManagerFragment) {
            mConferenceManagerFragment = (ConferenceManagerFragment) fragment;
        } else if (fragment instanceof FreemeCallButtonFragment) {
            mCallButtonFragment = (FreemeCallButtonFragment) fragment;
        }
        //*/
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        /// M: Configuration already changed, so get it here will always results in 2 same
        // configuration @{
        //Configuration oldConfig = getResources().getConfiguration();
        Configuration oldConfig = mCurrentConfig;
        mCurrentConfig = new Configuration(newConfig);
        /// }
        Log.v(this, String.format(
                "incallui config changed, screen size: w%ddp x h%ddp old:w%ddp x h%ddp",
                newConfig.screenWidthDp, newConfig.screenHeightDp,
                oldConfig.screenWidthDp, oldConfig.screenHeightDp));
        // Recreate this activity if height is changing beyond the threshold to load different
        // layout file.
        if (oldConfig.screenHeightDp < SCREEN_HEIGHT_RESIZE_THRESHOLD &&
                newConfig.screenHeightDp > SCREEN_HEIGHT_RESIZE_THRESHOLD ||
                oldConfig.screenHeightDp > SCREEN_HEIGHT_RESIZE_THRESHOLD &&
                        newConfig.screenHeightDp < SCREEN_HEIGHT_RESIZE_THRESHOLD) {
            Log.i(this, String.format(
                    "Recreate activity due to resize beyond threshold: %d dp",
                    SCREEN_HEIGHT_RESIZE_THRESHOLD));
            /// M: configuration change need recreate activity, don't cancel account selection
            mNeedCancelSelection = false;
            recreate();
        }
    }

    /**
     * Returns true when the Activity is currently visible.
     */
    /* package */ boolean isVisible() {
        return isSafeToCommitTransactions();
    }

    /// M: make private method to public. @{
    /*
     * Google code:
    private boolean hasPendingDialogs() {
     */
    public boolean hasPendingDialogs() {
    /// @}
        return mDialog != null || (mAnswerFragment != null && mAnswerFragment.hasPendingDialogs());
    }

    @Override
    public void finish() {
        Log.i(this, "finish().  Dialog showing: " + (mDialog != null));

        // skip finish if we are still showing a dialog.
        if (!hasPendingDialogs()) {
            /// M: sometimes it will call finish() from onResume() and the finish will delay too
            // long time
            // to disturb the new call to process, so just put the activity to back instead of
            // finish it.
            // when new call need to show it only need to restore the instance.

            // rollback to google default solution since too many side effects.
            //TODO still need to find a solution to avoid destroy activity take too long time @{
            super.finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(this, "onNewIntent: intent = " + intent);

        // We're being re-launched with a new Intent.  Since it's possible for a
        // single InCallActivity instance to persist indefinitely (even if we
        // finish() ourselves), this sequence can potentially happen any time
        // the InCallActivity needs to be displayed.

        // Stash away the new intent so that we can get it in the future
        // by calling getIntent().  (Otherwise getIntent() will return the
        // original Intent from when we first got created!)
        setIntent(intent);

        // Activities are always paused before receiving a new intent, so
        // we can count on our onResume() method being called next.

        // Just like in onCreate(), handle the intent.
        internalResolveIntent(intent);

        /// M:[RCS] plugin API @{
        ExtensionManager.getRCSeInCallExt().onNewIntent(intent);
        /// @}
    }

    @Override
    public void onBackPressed() {
        Log.i(this, "onBackPressed");

        // BACK is also used to exit out of any "special modes" of the
        // in-call UI:
        if (!isVisible()) {
            return;
        }

        if ((mConferenceManagerFragment == null || !mConferenceManagerFragment.isVisible())
                && (mCallCardFragment == null || !mCallCardFragment.isVisible())) {
            return;
        }

        /**
         * M: Try to dismiss mConferenceManagerFragment firstly, to avoid mDialpadFragment in
         *  background and can't dimiss successfully. @{
         **/
        if (mConferenceManagerFragment != null && mConferenceManagerFragment.isVisible()) {
            showConferenceFragment(false);
            return;
        } else if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            //*/ freeme.zhaozehong, 20171108. close Dialpad
            if (mCallCardFragment != null && mShowDialpadRequest != DIALPAD_REQUEST_NONE) {
                mCallCardFragment.closeDialpadFragment();
            }
            /*/
            mCallButtonFragment.displayDialpad(false /* show * /, true /* animate * /);
            //*/
            //fix bug for ALPS02515875, we should clear mShowDialpadRequested when backpressed
            mShowDialpadRequest = DIALPAD_REQUEST_NONE;
            return;
        }
        /** @} */

        // Always disable the Back key while an incoming call is ringing
        final Call call = CallList.getInstance().getIncomingCall();
        if (call != null) {
            Log.i(this, "Consume Back press for an incoming call");
            return;
        }

        /*/ freeme.zhaozehong, 20171108. refer to Samsung effect, block the back key
        // Nothing special to do.  Fall back to the default behavior.
        super.onBackPressed();
        //*/
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // push input to the dialer.
        if (mDialpadFragment != null && (mDialpadFragment.isVisible()) &&
                (mDialpadFragment.onDialerKeyUp(event))) {
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CALL) {
            // Always consume CALL to be sure the PhoneWindow won't do anything with it
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mDispatchTouchEventListener != null) {
            boolean handled = mDispatchTouchEventListener.onTouch(null, ev);
            if (handled) {
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                boolean handled = InCallPresenter.getInstance().handleCallKey();
                if (!handled) {
                    Log.w(this, "InCallActivity should always handle KEYCODE_CALL in onKeyDown");
                }
                // Always consume CALL to be sure the PhoneWindow won't do anything with it
                return true;

            // Note there's no KeyEvent.KEYCODE_ENDCALL case here.
            // The standard system-wide handling of the ENDCALL key
            // (see PhoneWindowManager's handling of KEYCODE_ENDCALL)
            // already implements exactly what the UI spec wants,
            // namely (1) "hang up" if there's a current active call,
            // or (2) "don't answer" if there's a current ringing call.

            case KeyEvent.KEYCODE_CAMERA:
                // Disable the CAMERA button while in-call since it's too
                // easy to press accidentally.
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                // Ringer silencing handled by PhoneWindowManager.
                break;

            case KeyEvent.KEYCODE_MUTE:
                // toggle mute
                TelecomAdapter.getInstance().mute(!AudioModeProvider.getInstance().getMute());
                return true;

            // Various testing/debugging features, enabled ONLY when VERBOSE == true.
            case KeyEvent.KEYCODE_SLASH:
                if (Log.VERBOSE) {
                    Log.v(this, "----------- InCallActivity View dump --------------");
                    // Dump starting from the top-level view of the entire activity:
                    Window w = this.getWindow();
                    View decorView = w.getDecorView();
                    Log.d(this, "View dump:" + decorView);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_EQUALS:
                // TODO: Dump phone state?
                break;
        }

        if (event.getRepeatCount() == 0 && handleDialerKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleDialerKeyDown(int keyCode, KeyEvent event) {
        Log.v(this, "handleDialerKeyDown: keyCode " + keyCode + ", event " + event + "...");

        // As soon as the user starts typing valid dialable keys on the
        // keyboard (presumably to type DTMF tones) we start passing the
        // key events to the DTMFDialer's onDialerKeyDown.
        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            return mDialpadFragment.onDialerKeyDown(event);
        }

        return false;
    }

    /*/ freeme.zhaozehong, 25/07/17. for freemeOS
    public CallButtonFragment getCallButtonFragment() {
        return mCallButtonFragment;
    }

    public CallCardFragment getCallCardFragment() {
        return mCallCardFragment;
    }
    /*/
    public FreemeCallButtonFragment getCallButtonFragment() {
        return mCallButtonFragment;
    }

    public FreemeCallCardFragment getCallCardFragment() {
        return mCallCardFragment;
    }
    //*/

    public AnswerFragment getAnswerFragment() {
        return mAnswerFragment;
    }

    private void internalResolveIntent(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_MAIN)) {
            // This action is the normal way to bring up the in-call UI.
            //
            // But we do check here for one extra that can come along with the
            // ACTION_MAIN intent:

            /// M: fix ALPS02195860, need to clear any pending dialog before bringing up
            /// InCallUI. @{
            if (mAnswerFragment != null) {
                mAnswerFragment.dismissPendingDialogs();
            }
            /// @}

            if (intent.hasExtra(SHOW_DIALPAD_EXTRA)) {
                // SHOW_DIALPAD_EXTRA can be used here to specify whether the DTMF
                // dialpad should be initially visible.  If the extra isn't
                // present at all, we just leave the dialpad in its previous state.

                final boolean showDialpad = intent.getBooleanExtra(SHOW_DIALPAD_EXTRA, false);
                Log.d(this, "- internalResolveIntent: SHOW_DIALPAD_EXTRA: " + showDialpad);

                relaunchedFromDialer(showDialpad);
            }

            boolean newOutgoingCall = false;
            if (intent.getBooleanExtra(NEW_OUTGOING_CALL_EXTRA, false)) {
                intent.removeExtra(NEW_OUTGOING_CALL_EXTRA);
                Call call = CallList.getInstance().getOutgoingCall();
                if (call == null) {
                    call = CallList.getInstance().getPendingOutgoingCall();
                }

                Bundle extras = null;
                if (call != null) {
                    extras = call.getTelecomCall().getDetails().getIntentExtras();
                }
                if (extras == null) {
                    // Initialize the extras bundle to avoid NPE
                    extras = new Bundle();
                }

                Point touchPoint = null;
                if (TouchPointManager.getInstance().hasValidPoint()) {
                    // Use the most immediate touch point in the InCallUi if available
                    touchPoint = TouchPointManager.getInstance().getPoint();
                } else {
                    // Otherwise retrieve the touch point from the call intent
                    if (call != null) {
                        touchPoint = (Point) extras.getParcelable(TouchPointManager.TOUCH_POINT);
                    }
                }

                // Start animation for new outgoing call
                CircularRevealFragment.startCircularReveal(getFragmentManager(), touchPoint,
                        InCallPresenter.getInstance());
                /// M: fix ALPS02273012, Move the check logic to InCallPresenter's onCallListChange
                // method, sure to check valid account after the Call added. @{
                // InCallActivity is responsible for disconnecting a new outgoing call if there
                // is no way of making it (i.e. no valid call capable accounts)
                /*
                // InCallActivity is responsible for disconnecting a new outgoing call if there
                // is no way of making it (i.e. no valid call capable accounts).
                // If the version is not MSIM compatible, then ignore this code.
                if (CompatUtils.isMSIMCompatible()
                        && InCallPresenter.isCallWithNoValidAccounts(call)) {
                    TelecomAdapter.getInstance().disconnectCall(call.getId());
                }
                */
                // @}
                dismissKeyguard(true);
                newOutgoingCall = true;
            }

            Call pendingAccountSelectionCall = CallList.getInstance().getWaitingForAccountCall();
            if (pendingAccountSelectionCall != null) {
                /// M: [@Modification for finishing Transparent InCall Screen if necessary]
                /// add for resolve finish incall screen issue. @{
                mIsLunchedAccountSelectDlg = true;
                /// @}
                /// M:Fix ALPS02759272, if select account dialog already exist, do not show again.@{
                DialogFragment selectAccountDialog = (DialogFragment) getFragmentManager().
                        findFragmentByTag(TAG_SELECT_ACCT_FRAGMENT);
                if(selectAccountDialog != null) {
                    return;
                }
                /// @}
                showCallCardFragment(false);
                Bundle extras =
                        pendingAccountSelectionCall.getTelecomCall().getDetails().getIntentExtras();

                final List<PhoneAccountHandle> phoneAccountHandles;
                if (extras != null) {
                    phoneAccountHandles = extras.getParcelableArrayList(
                            android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);
                } else {
                    phoneAccountHandles = new ArrayList<>();
                }

                DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                        R.string.select_phone_account_for_calls, true, phoneAccountHandles,
                        mSelectAcctListener);
                /// M: add for OP09 plugin. @{
                ExtensionManager.getInCallExt().customizeSelectPhoneAccountDialog(dialogFragment);
                ///@}
                /// M: add for suggested account feature. @{
                ((SelectPhoneAccountDialogFragment)dialogFragment).setSuggestedPhoneAccount
                        (InCallUtils.getSuggestedPhoneAccountHandle(pendingAccountSelectionCall));
                /// @}
                dialogFragment.show(getFragmentManager(), TAG_SELECT_ACCT_FRAGMENT);
            } else if (!newOutgoingCall) {
                showCallCardFragment(true);

                /// M: Fix ALPS01922620. @{
                // After pressed home key when showing Account dialog, the activity will not been
                // finished and when start activity with new intent, the account dialog will show
                // again but there has no pending call, so need dismiss accout dialog at here.
                dismissSelectAccountDialog();
                /// @}

                /// M: [@Modification for finishing Transparent InCall Screen if necessary]
                /// add for resolve finish incall screen issue. @{
                mIsLunchedAccountSelectDlg = false;
                /// @}
            // M:fix CR:ALPS02316060,
            // When SIMC exist 1H,SIMG exist 1A1H,switch SIMC call to active,
            // mIsLunchedAccountSelectDlg value is true,not be updated,cause
            // InCallactivity finish,occur the whole call will turn background.
            } else {
                dismissSelectAccountDialog();
                mIsLunchedAccountSelectDlg = false;
            }
            /// @}
            return;
        }
    }

    /**
     * When relaunching from the dialer app, {@code showDialpad} indicates whether the dialpad
     * should be shown on launch.
     *
     * @param showDialpad {@code true} to indicate the dialpad should be shown on launch, and
     *                                {@code false} to indicate no change should be made to the
     *                                dialpad visibility.
     */
    private void relaunchedFromDialer(boolean showDialpad) {
        mShowDialpadRequest = showDialpad ? DIALPAD_REQUEST_SHOW : DIALPAD_REQUEST_NONE;
        mAnimateDialpadOnShow = true;

        if (mShowDialpadRequest == DIALPAD_REQUEST_SHOW) {
            // If there's only one line in use, AND it's on hold, then we're sure the user
            // wants to use the dialpad toward the exact line, so un-hold the holding line.
            final Call call = CallList.getInstance().getActiveOrBackgroundCall();
            if (call != null && call.getState() == State.ONHOLD
                    /// M: If has PendingOutgoing, Outgoing or Incoming call, don't unhold call @{
                    && CallList.getInstance().getPendingOutgoingCall() == null
                    && CallList.getInstance().getOutgoingCall() == null
                    && CallList.getInstance().getIncomingCall() == null) {
                    /// @}
                /// M: [log optimize]
                Log.op(call, Log.CcOpAction.UNHOLD, "relaunch from dialer showing dialpad.");
                TelecomAdapter.getInstance().unholdCall(call.getId());
            }
        }
    }

    public void dismissKeyguard(boolean dismiss) {
        if (mDismissKeyguard == dismiss) {
            return;
        }
        mDismissKeyguard = dismiss;
        if (dismiss) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }

    private void showFragment(String tag, boolean show, boolean executeImmediately) {
        Trace.beginSection("showFragment - " + tag);
        final FragmentManager fm = getFragmentManagerForTag(tag);

        if (fm == null) {
            Log.w(TAG, "Fragment manager is null for : " + tag);
            return;
        }

        Fragment fragment = fm.findFragmentByTag(tag);
        if (!show && fragment == null) {
            // Nothing to show, so bail early.
            return;
        }

        final FragmentTransaction transaction = fm.beginTransaction();
        if (show) {
            if (fragment == null) {
                fragment = createNewFragmentForTag(tag);
                transaction.add(getContainerIdForFragment(tag), fragment, tag);
            } else {
                transaction.show(fragment);
            }
            Logger.logScreenView(getScreenTypeForTag(tag), this);
        } else {
            transaction.hide(fragment);
            //*/ freeme.zhaozehong, 26/07/17. for freemeOS
            if(tag.equals(TAG_DIALPAD_FRAGMENT)){
                mCallButtonFragment.showCallButton(true, true);
            }
            //*/
        }

        transaction.commitAllowingStateLoss();
        if (executeImmediately) {
            fm.executePendingTransactions();
        }
        Trace.endSection();
    }

    private Fragment createNewFragmentForTag(String tag) {
        if (TAG_DIALPAD_FRAGMENT.equals(tag)) {
            /*/ freeme.zhaozehong, 26/07/17. for freemeOS
            mDialpadFragment = new DialpadFragment();
            /*/
            mDialpadFragment = new FreemeDialpadFragment();
            //*/
            return mDialpadFragment;
        } else if (TAG_ANSWER_FRAGMENT.equals(tag)) {
            if (AccessibilityUtil.isTalkBackEnabled(this)) {
                mAnswerFragment = new AccessibleAnswerFragment();
            } else {
                /*/ freeme.zhaozehong, 19/07/17. for freemeOS
                mAnswerFragment = new GlowPadAnswerFragment();
                /*/
                mAnswerFragment = new FreemeGlowPadAnswerFragment();
                //*/
            }
            return mAnswerFragment;
        } else if (TAG_CONFERENCE_FRAGMENT.equals(tag)) {
            mConferenceManagerFragment = new ConferenceManagerFragment();
            return mConferenceManagerFragment;
        } else if (TAG_CALLCARD_FRAGMENT.equals(tag)) {
            /*/ freeme.zhaozehong, 24/07/17. for freemeOS
            mCallCardFragment = new CallCardFragment();
            /*/
            mCallCardFragment = new FreemeCallCardFragment();
            //*/
            return mCallCardFragment;
        }
        throw new IllegalStateException("Unexpected fragment: " + tag);
    }

    private FragmentManager getFragmentManagerForTag(String tag) {
        if (TAG_DIALPAD_FRAGMENT.equals(tag)) {
            return mChildFragmentManager;
        } else if (TAG_ANSWER_FRAGMENT.equals(tag)) {
            return mChildFragmentManager;
        } else if (TAG_CONFERENCE_FRAGMENT.equals(tag)) {
            return getFragmentManager();
        } else if (TAG_CALLCARD_FRAGMENT.equals(tag)) {
            return getFragmentManager();
        }
        throw new IllegalStateException("Unexpected fragment: " + tag);
    }

    private int getScreenTypeForTag(String tag) {
        switch (tag) {
            case TAG_DIALPAD_FRAGMENT:
                return ScreenEvent.INCALL_DIALPAD;
            case TAG_CALLCARD_FRAGMENT:
                return ScreenEvent.INCALL;
            case TAG_CONFERENCE_FRAGMENT:
                return ScreenEvent.CONFERENCE_MANAGEMENT;
            case TAG_ANSWER_FRAGMENT:
                return ScreenEvent.INCOMING_CALL;
            default:
                return ScreenEvent.UNKNOWN;
        }
    }

    private int getContainerIdForFragment(String tag) {
        if (TAG_DIALPAD_FRAGMENT.equals(tag)) {
            /*/ freeme.zhaozehong, 26/07/17. for freemeOS
            return R.id.answer_and_dialpad_container;
            /*/
            return R.id.dialpad_container;
            //*/
        } else if (TAG_ANSWER_FRAGMENT.equals(tag)) {
            return R.id.answer_and_dialpad_container;
        } else if (TAG_CONFERENCE_FRAGMENT.equals(tag)) {
            return R.id.main;
        } else if (TAG_CALLCARD_FRAGMENT.equals(tag)) {
            return R.id.main;
        }
        throw new IllegalStateException("Unexpected fragment: " + tag);
    }

    /**
     * @return {@code true} while the visibility of the dialpad has actually changed.
     */
    public boolean showDialpadFragment(boolean show, boolean animate) {
        // If the dialpad is already visible, don't animate in. If it's gone, don't animate out.
        if ((show && isDialpadVisible()) || (!show && !isDialpadVisible())) {
            return false;
        }
        // We don't do a FragmentTransaction on the hide case because it will be dealt with when
        // the listener is fired after an animation finishes.
        if (!animate) {
            showFragment(TAG_DIALPAD_FRAGMENT, show, true);
            ///M: ALPS01855248 @{
            // resize end button size when dialpad shows
            // to avoid the overlap between dialpad and end button
            mCallCardFragment.onDialpadVisibilityChange(show);
            /// @}
        } else {
            if (show) {
                showFragment(TAG_DIALPAD_FRAGMENT, true, true);
                mDialpadFragment.animateShowDialpad();
            }
            mCallCardFragment.onDialpadVisibilityChange(show);
            mDialpadFragment.getView().startAnimation(show ? mSlideIn : mSlideOut);
        }
        // Note:  onDialpadVisibilityChange is called here to ensure that the dialpad FAB
        // repositions itself.
        mCallCardFragment.onDialpadVisibilityChange(show);

        final ProximitySensor sensor = InCallPresenter.getInstance().getProximitySensor();
        if (sensor != null) {
            sensor.onDialpadVisible(show);
        }
        //*/ freeme.zhaozehong, 20171108, reset dialpad status flag
        setShowDialpadRequested(show ? DIALPAD_REQUEST_SHOW : DIALPAD_REQUEST_NONE);
        //*/
        return true;
    }

    public boolean isDialpadVisible() {
        return mDialpadFragment != null && mDialpadFragment.isVisible();
    }

    public void showCallCardFragment(boolean show) {
        showFragment(TAG_CALLCARD_FRAGMENT, show, true);
    }

    /**
     * Hides or shows the conference manager fragment.
     *
     * @param show {@code true} if the conference manager should be shown, {@code false} if it
     * should be hidden.
     */
    public void showConferenceFragment(boolean show) {
        showFragment(TAG_CONFERENCE_FRAGMENT, show, true);
        //*/ freeme.zhaozehong, 20171028. for freemeOS, reset StatusBar and NavigationBar color
        int color = show ? getColor(R.color.freeme_color_primary)
                : android.graphics.Color.TRANSPARENT;
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        //*/
        mConferenceManagerFragment.onVisibilityChanged(show);

        // Need to hide the call card fragment to ensure that accessibility service does not try to
        // give focus to the call card when the conference manager is visible.
        mCallCardFragment.getView().setVisibility(show ? View.GONE : View.VISIBLE);
    }

    public void showAnswerFragment(boolean show) {
        showFragment(TAG_ANSWER_FRAGMENT, show, true);
        //*/ freeme.zhaozehong, 18/07/17. for smart answer or no-touch
        if (mAnswerFragment != null) {
            mAnswerFragment.onVisibilityChanged(show);
        }
        //*/
    }

    public void showPostCharWaitDialog(String callId, String chars) {
        if (isVisible()) {
            /// M:for ALPS01825589, need to dismiss post dialog when add another call. @{
            /*
             * Google code:
            final PostCharDialogFragment fragment = new PostCharDialogFragment(callId,  chars);
            fragment.show(getFragmentManager(), "postCharWait");
             */
            mPostCharDialogfragment = new PostCharDialogFragment(callId,  chars);
            mPostCharDialogfragment.show(getFragmentManager(), "postCharWait");
            /// @}

            mShowPostCharWaitDialogOnResume = false;
            mShowPostCharWaitDialogCallId = null;
            mShowPostCharWaitDialogChars = null;
        } else {
            mShowPostCharWaitDialogOnResume = true;
            mShowPostCharWaitDialogCallId = callId;
            mShowPostCharWaitDialogChars = chars;
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mCallCardFragment != null) {
            mCallCardFragment.dispatchPopulateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    public void maybeShowErrorDialogOnDisconnect(DisconnectCause disconnectCause) {
        Log.d(this, "maybeShowErrorDialogOnDisconnect");

        // M: fix ALPS02606753,when have a new call,not show error dialog.
        if (isVisible() && !isFinishing() && !TextUtils.isEmpty(disconnectCause.getDescription())
                && (disconnectCause.getCode() == DisconnectCause.ERROR ||
                        disconnectCause.getCode() == DisconnectCause.RESTRICTED)) {
            showErrorDialog(disconnectCause.getDescription());
        }
        /// M: fix ALPS01935061,if InCallActivity has not resumed already, show error
        // dialog later @{
        else if (!isResumed()
                && !TextUtils.isEmpty(disconnectCause.getDescription())
                && (disconnectCause.getCode() == DisconnectCause.ERROR || disconnectCause
                .getCode() == DisconnectCause.RESTRICTED)) {
            Log.d(this, "maybeShowErrorDialogOnDisconnect, activity not resumed");
            mDelayShowErrorDialogRequest = true;
            mDisconnectCauseDescription = disconnectCause.getDescription();
            return;
        /// @}
        /// M: [@Modification for finishing Transparent InCall Screen if necessary] @{
        }
        ///M: WFC <handle first wifi call ends popup> @{
        else if ( !isFinishing() && InCallUiWfcUtils.maybeShowWfcError(this, disconnectCause)){
        }
        /// M: show congrats popup, handled through plugin
        else if (!isFinishing() && ExtensionManager.getInCallExt()
                .showCongratsPopup(disconnectCause)) {
        } else {
            dismissInCallActivityIfNecessary();
        }
        /// @}
    }

    public void dismissPendingDialogs() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        if (mAnswerFragment != null) {
            mAnswerFragment.dismissPendingDialogs();
        }
    }

    /**
     * Utility function to bring up a generic "error" dialog.
     */
    private void showErrorDialog(CharSequence msg) {
        Log.i(this, "Show Dialog: " + msg);

        dismissPendingDialogs();

        mDialog = new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onDialogDismissed();
                    }
                })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        onDialogDismissed();
                    }
                })
                /// M: Add dissmiss listener because will dismiss it when onStop.@{
                .setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        onDialogDismissed();
                    }})
                /// @}
                .create();

        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mDialog.show();
    }

    private void onDialogDismissed() {
        mDialog = null;
        /// M: [@Modification for finishing Transparent InCall Screen if necessary]
        /// Fix ALPS02012202. Finish activity and no need show transition animation.@{
        dismissInCallActivityIfNecessary();
        /// @}
        CallList.getInstance().onErrorDialogDismissed();
        InCallPresenter.getInstance().onDismissDialog();
    }

    public void setExcludeFromRecents(boolean exclude) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.AppTask> tasks = am.getAppTasks();
        int taskId = getTaskId();
        for (int i = 0; i < tasks.size(); i++) {
            ActivityManager.AppTask task = tasks.get(i);
            /// M: catch excption for TaskInfo unsync JE.@{
            try {
                if (task.getTaskInfo().id == taskId) {
                    task.setExcludeFromRecents(exclude);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "RuntimeException when excluding task from recents.", e);
            }
            /// @}
        }
    }


    public OnTouchListener getDispatchTouchEventListener() {
        return mDispatchTouchEventListener;
    }

    public void setDispatchTouchEventListener(OnTouchListener mDispatchTouchEventListener) {
        this.mDispatchTouchEventListener = mDispatchTouchEventListener;
    }

    /**
     * Enables the OrientationEventListener if enable flag is true. Disables it if enable is
     * false
     * @param enable true or false.
     */
    public void enableInCallOrientationEventListener(boolean enable) {
        if (enable) {
            mInCallOrientationEventListener.enable(enable);
        } else {
            mInCallOrientationEventListener.disable();
        }
    }
/// --------------------------------Mediatek----------------------------------------------
    /// M: DMLock, PPL
    private DMLockBroadcastReceiver mDMLockReceiver;

    /// M:for ALPS01825589, need to dismiss post dialog when add another call. @{
    private PostCharDialogFragment mPostCharDialogfragment;
    /// @}

    /// M: fix ALPS01935061,record error dialog info when need Show error dialog after activity
    // resume @{
    private boolean mDelayShowErrorDialogRequest = false;
    private CharSequence mDisconnectCauseDescription;
    /// @}

    /// M: For Recording @{
    public void showStorageFullDialog(final int resid, final boolean isSDCardExist) {
        Log.d(this, "showStorageDialog... ");
        dismissPendingDialogs();

        CharSequence msg = getResources().getText(resid);

        // create the clicklistener and cancel listener as needed.
        OnCancelListener cancelListener = new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
            }
        };

        DialogInterface.OnClickListener cancelClickListener = new DialogInterface
                .OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Log.d(this, "showStorageDialog... , on click, which=" + which);
                if (null != mDialog) {
                    mDialog.dismiss();
                }
            }
        };

        CharSequence cancelButtonText = isSDCardExist ? getResources().getText(
                R.string.alert_dialog_dismiss) : getResources().getText(android.R.string.ok);
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this).setMessage(msg)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getResources().getText(android.R.string.dialog_alert_title))
                .setNegativeButton(cancelButtonText, cancelClickListener)
                .setOnCancelListener(cancelListener)
                .setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        onDialogDismissed();
                    }
                });

        if (isSDCardExist) {
            DialogInterface.OnClickListener oKClickListener = new DialogInterface
                    .OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Log.d(this, "showStorageDialog... , on click, which=" + which);
                    if (null != mDialog) {
                        mDialog.dismiss();
                    }
                    // To Setting Storage
                    Intent intent = new Intent(PhoneRecorderUtils.STORAGE_SETTING_INTENT_NAME);
                    startActivity(intent);
                }
            };
            dialogBuilder.setPositiveButton(
                    getResources().getText(R.string.change_my_pic), oKClickListener);
        }

        mDialog = dialogBuilder.create();
        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mDialog.show();
    }
    /// @}

    /**
     * M: set the window flags.
     */
    private void setWindowFlag() {
        // set this flag so this activity will stay in front of the keyguard
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

        Call call = CallList.getInstance().getActiveOrBackgroundCall();
        if (call != null && Call.State.isConnectingOrConnected(call.getState())) {
            // While we are in call, the in-call screen should dismiss the keyguard.
            // This allows the user to press Home to go directly home without going through
            // an insecure lock screen.
            // But we do not want to do this if there is no active call so we do not
            // bypass the keyguard if the call is not answered or declined.

            /// M: DM lock@{
            if (!InCallUtils.isDMLocked()) {
                flags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
                Log.d(this, "set window FLAG_DISMISS_KEYGUARD flag ");
            }
            /// @}
        }

        final WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.flags |= flags;
        getWindow().setAttributes(lp);
    }

    /**
     * M: Dismiss select account dialog when there has incoming call.
     */
    public void dismissSelectAccountDialog() {
        DialogFragment fragment = (DialogFragment) getFragmentManager().findFragmentByTag(
                TAG_SELECT_ACCT_FRAGMENT);
        Log.d(this, "dismissSelectAccountDialog(), fragment is " + fragment);
        if (fragment != null) {
            /// M:  For ALPS02347397 sometimes will called after onSavedInstance
            // called that will cause JE@{
            //fragment.dismiss();
            fragment.dismissAllowingStateLoss();
            //@}
        }
    }

    /**
     * M: [ALPS02025119]The InCallActivity is transparent, so that when the VoLTE
     * conference invitation dialog Activity appears, the AMS will change its
     * background to Launcher. We should change the InCallActivity to non-transparent
     * when the conference manager appears.
     */
    void changeToTransparent(boolean transparent) {
        Log.v(this, "latest transparent: " + transparent);
        if (transparent) {
            convertToTranslucent(null, null);
        } else {
            convertFromTranslucent();
        }
    }

    /***
     * M: [@Modification for finishing Transparent InCall Screen if necessary]
     * add for resolving finish incall activity issue. @{
     */
    private boolean mIsLunchedAccountSelectDlg = false;
    /***
     * @}
     */

    /**
     * M: [@Modification for finishing Transparent InCall Screen if necessary]
     * Finish Incall activity if the current incall-activity is transparent after phone account
     * dialog exit:
     * 1. After select card, Telecom cancel this call due to call amount is full;
     * 2. After select card, Telephony cancel this call due to checking CellConnMgr failure.
     * 3. Dial a Ipcall after select some account but without IP Prefix;
     * 4. After dialing number, but back from account selection dialog, call out without account;
     * 5. MMI execution fail or succeed after select account.
     * 6. ECC Call[ALPS02063322] will cancel ACTIVE Call, but not to finish incall screen.
     * 7. Call error[ALPS02029221] will cancel the current call, but not to finish incall screen.
     */
    private void dismissInCallActivityIfNecessary() {
        // / Fix ALPS01992679.
        // Sometimes, second call can not select account because activity will
        // been finished
        // when first call disconnected. So in this case, no need finish
        // InCallActivity.
        boolean hasPreDialWaitCall = CallList.getInstance().getWaitingForAccountCall() != null;
        Log.d(this, "[dismissInCallActivityIfNecessary] mIsLunchedAccountSelectDlg:"
                + mIsLunchedAccountSelectDlg
                + " hasPreDialWaitCall:" + hasPreDialWaitCall);
        if (mIsLunchedAccountSelectDlg && !isFinishing() && !hasPreDialWaitCall
                && (CallList.getInstance().getIncomingCall() == null)) {
            Log.d(this, "[dismissInCallActivityIfNecessary], finish activity if necessary" +
                    " for transparent"
                    + " account incallactivity.");
            finish();
            overridePendingTransition(0, 0);
            return;
        }

    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String permissions[], final int[] grantResults) {
        Log.d(this, "onRequestPermissionsResult(), for RCSe plugin ");
        ExtensionManager.getRCSeInCallExt().
                onRCSeRequestPermissionsResult(requestCode,permissions,grantResults);
    }
    ///M: set mShowDialpadRequested value @{
    public void setShowDialpadRequested(int showDialpadRequest) {
        this.mShowDialpadRequest = showDialpadRequest;
    }
    ///@}

    /*/ freeme.zhaozehong, 26/07/17. for freemeOS
    /// M: get mDialpadFragment @{
    public DialpadFragment getDialpadFragment() {
        return mDialpadFragment;
    }
    /// @}
    /*/
    public FreemeDialpadFragment getDialpadFragment() {
        return mDialpadFragment;
    }
    //*/

    //*/ freeme.zhaozehong, 20/07/17. for freemeOS, set contact photo
    public void showContactPhoto(Drawable photo) {
        ContactInfoCache.getInstance(this).setContactPhotoDrawable(photo);
        if (mAnswerFragment != null && mAnswerFragment instanceof FreemeGlowPadAnswerFragment) {
            ((FreemeGlowPadAnswerFragment)mAnswerFragment).showContactPhoto(photo);
        }
    }
    //*/

    //*/ freeme.zhaozehong, 24/07/17. for freemeOS
    public void hideCallButton(boolean dialpad) {
        mCallButtonFragment.showCallButton(false, dialpad);
        if (mCallCardFragment.isConferenceCall()) {
            mCallCardFragment.showManageConferenceCallButton(false);
        }
    }

    public String getRecordNumberForDisconnect() {
        if (mDialpadFragment == null) {
            return null;
        }
        return mDialpadFragment.getRecordNumber();
    }

    public void updateCallCardBtnStatus(boolean isDialpadShow, boolean isFromDialpadBtn) {
        if (mCallCardFragment != null) {
            mCallCardFragment.updateButtonStatus(isDialpadShow, isFromDialpadBtn);
        }
        if (isDialpadShow && mDialpadFragment != null) {
            mDialpadFragment.setupDeleteButton(isFromDialpadBtn);
        }
    }

    public void recordNumerToDialpad() {
        String mRecordNumber = getRecordNumberForDisconnect();
        if (TextUtils.isEmpty(mRecordNumber)) return;
        Intent recordIntent = new Intent(Intent.ACTION_DIAL, getCallUri(mRecordNumber));
        recordIntent.putExtra("record_number_key", true);
        recordIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        startActivity(recordIntent);
    }

    public static Uri getCallUri(String number) {
        if (PhoneNumberUtils.isUriNumber(number)) {
            return Uri.fromParts("sip", number, null);
        }
        return Uri.fromParts("tel", number, null);
    }

    @Override
    public void onCallButtonShow(boolean show) {
        if (show && CallList.getInstance().getActiveOrBackgroundCall() == null) {
            return;
        }
        mCallCardFragment.showEndButtonContainer(show);
    }

    @Override
    public void onCallCardShowPhoto(boolean show) {
        mCallCardFragment.setFreemePhotoVisible(!show);
    }

    public void updateDialpadSize(boolean isDeviceLandscape) {
        if (mDialpadFragment != null)
            mDialpadFragment.updateDialpadSize(isDeviceLandscape);
    }
    //*/

    /**
     * M: Support query orientation mode.
     */
    public boolean isLandscape() {
        return mIsLandscape;
    }

}
