package com.freeme.incallui;

import android.app.Fragment;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.nfc.INfcTag;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.PopupMenu.OnDismissListener;
import android.widget.PopupMenu.OnMenuItemClickListener;

import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.dialer.R;
import com.android.incallui.BaseFragment;
import com.android.incallui.Call;
import com.android.incallui.CallButtonPresenter;
import com.android.incallui.CallList;
import com.android.incallui.InCallActivity;
import com.android.incallui.InCallPresenter;
import com.android.incallui.Log;
import com.android.incallui.VideoUtils;
import com.freeme.incallui.widgets.FreemeImageButtonWithText;
import com.mediatek.incallui.ext.ExtensionManager;
import com.mediatek.incallui.ext.IRCSeCallButtonExt;
import com.mediatek.incallui.recorder.PhoneRecorderUtils;

import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_AUDIO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_MUTE;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_HOLD;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_SWAP;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_UPGRADE_TO_VIDEO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_SWITCH_CAMERA;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_DOWNGRADE_TO_AUDIO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_HIDE_LOCAL_VIDEO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_ADD_CALL;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_MERGE;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_PAUSE_VIDEO;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_MANAGE_VIDEO_CONFERENCE;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_SWITCH_VOICE_RECORD;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_SET_ECT;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_HANGUP_ALL_CALLS;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_HANGUP_ALL_HOLD_CALLS;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_HANGUP_ACTIVE_AND_ANSWER_WAITING;
import static com.android.incallui.CallButtonFragment.Buttons.BUTTON_COUNT;

/**
 * Created by zhaozehong on 25/07/17.
 */

public class FreemeCallButtonFragment
        extends BaseFragment<CallButtonPresenter, CallButtonPresenter.CallButtonUi>
        implements CallButtonPresenter.CallButtonUi, OnMenuItemClickListener, OnDismissListener,
        View.OnClickListener {

    private int mButtonMaxVisible;
    // The button is currently visible in the UI
    private static final int BUTTON_VISIBLE = 1;
    // The button is hidden in the UI
    private static final int BUTTON_HIDDEN = 2;
    // The button has been collapsed into the overflow menu
    private static final int BUTTON_MENU = 3;

    private SparseIntArray mButtonVisibilityMap = new SparseIntArray(BUTTON_COUNT);

    private FreemeImageButtonWithText mMuteButton;
    private FreemeImageButtonWithText mHoldButton;
    private FreemeImageButtonWithText mSwapButton;
    private FreemeImageButtonWithText mAudioButton;
    private FreemeImageButtonWithText mHideOrShowLocalVideoButton;

    private FreemeImageButtonWithText mChangeToVideoButton;
    private FreemeImageButtonWithText mSwitchCameraButton;
    private FreemeImageButtonWithText mAddCallButton;
    private FreemeImageButtonWithText mPauseVideoButton;
    private FreemeImageButtonWithText mMergeButton;
    private View mRecordVoiceLayout;
    private FreemeImageButtonWithText mRecordVoiceButton;
    private ImageView mVoiceRecorderIcon;
    private FreemeImageButtonWithText mChangeToVoiceButton;

    private ImageButton mManageVideoCallConferenceButton;
    private PopupMenu mAudioModePopup;
    private boolean mAudioModePopupVisible;

    private IRCSeCallButtonExt mRCSeExt;

    private ImageButton mSetEctButton;
    private ImageButton mHangupAllCallsButton;
    private ImageButton mHangupAllHoldCallsButton;
    private ImageButton mHangupActiveAndAnswerWaitingButton;

    private View mCallButtonContainer;

    private int mPrevAudioMode = 0;

    // Constants for Drawable.setAlpha()
    private static final int HIDDEN = 0;
    private static final int VISIBLE = 255;

    private boolean mIsEnabled;
    private MaterialColorMapUtils.MaterialPalette mCurrentThemeColors;
    private Context mContext;

    @Override
    public CallButtonPresenter createPresenter() {
        // TODO: find a cleaner way to include audio mode provider than having a singleton instance.
        return new CallButtonPresenter();
    }

    @Override
    public CallButtonPresenter.CallButtonUi getUi() {
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        for (int i = 0; i < BUTTON_COUNT; i++) {
            mButtonVisibilityMap.put(i, BUTTON_HIDDEN);
        }

        mButtonMaxVisible = getResources().getInteger(R.integer.call_card_max_buttons);
        /// M: add for plug in. @{
        mRCSeExt = ExtensionManager.getRCSeCallButtonExt();
        /// @}
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View parent = inflater.inflate(R.layout.freeme_call_button_fragment, container, false);

        mMuteButton = (FreemeImageButtonWithText) parent.findViewById(R.id.muteButton);
        mMuteButton.setOnClickListener(this);
        mHoldButton = (FreemeImageButtonWithText) parent.findViewById(R.id.holdButton);
        mHoldButton.setOnClickListener(this);
        mSwapButton = (FreemeImageButtonWithText) parent.findViewById(R.id.swapButton);
        mSwapButton.setOnClickListener(this);
        mHideOrShowLocalVideoButton = (FreemeImageButtonWithText) parent.findViewById(R.id.hideOrShowLocalVideo);
        mHideOrShowLocalVideoButton.setOnClickListener(this);
        mAudioButton = (FreemeImageButtonWithText) parent.findViewById(R.id.audioButton);
        mAudioButton.setOnClickListener(this);

        mChangeToVideoButton = (FreemeImageButtonWithText) parent.findViewById(R.id.changeToVideoButton);
        mChangeToVideoButton.setOnClickListener(this);
        mSwitchCameraButton = (FreemeImageButtonWithText) parent.findViewById(R.id.switchCameraButton);
        mSwitchCameraButton.setOnClickListener(this);
        mAddCallButton = (FreemeImageButtonWithText) parent.findViewById(R.id.addButton);
        mAddCallButton.setOnClickListener(this);
        mPauseVideoButton = (FreemeImageButtonWithText) parent.findViewById(R.id.pauseVideoButton);
        mPauseVideoButton.setOnClickListener(this);
        mMergeButton = (FreemeImageButtonWithText) parent.findViewById(R.id.mergeButton);
        mMergeButton.setOnClickListener(this);
        mRecordVoiceLayout = parent.findViewById(R.id.switch_voice_record_layout);
        mRecordVoiceButton = (FreemeImageButtonWithText) parent.findViewById(R.id.switch_voice_record);
        mRecordVoiceButton.setOnClickListener(this);
        mVoiceRecorderIcon = (ImageView) parent.findViewById(R.id.voiceRecorderIcon);
        mChangeToVoiceButton = (FreemeImageButtonWithText) parent.findViewById(R.id.changeToVoiceButton);
        mChangeToVoiceButton.setOnClickListener(this);

        mManageVideoCallConferenceButton = (ImageButton) parent.findViewById(
                R.id.manageVideoCallConferenceButton);
        mManageVideoCallConferenceButton.setOnClickListener(this);

        mSetEctButton = (ImageButton) parent.findViewById(R.id.setEctButton);
        mSetEctButton.setOnClickListener(this);
        mHangupAllCallsButton = (ImageButton) parent.findViewById(R.id.hangupAllCallsButton);
        mHangupAllCallsButton.setOnClickListener(this);
        mHangupAllHoldCallsButton = (ImageButton) parent.findViewById(
                R.id.hangupAllHoldCallsButton);
        mHangupAllHoldCallsButton.setOnClickListener(this);
        mHangupActiveAndAnswerWaitingButton = (ImageButton) parent.findViewById(
                R.id.hangupActiveAndAnswerWaitingButton);
        mHangupActiveAndAnswerWaitingButton.setOnClickListener(this);

        mCallButtonContainer = parent.findViewById(R.id.callButtonContainer);

        boolean isVideo = false, isConference = false;
        CallList callList = InCallPresenter.getInstance().getCallList();
        if (callList != null) {
            Call outgongCall = callList.getOutgoingCall();
            if (outgongCall != null) {
                isVideo = VideoUtils.isVideoCall(outgongCall);
                if (isVideo) {
                    showButton(BUTTON_HIDE_LOCAL_VIDEO, true);
                    showButton(BUTTON_SWITCH_CAMERA, true);
                    showButton(BUTTON_DOWNGRADE_TO_AUDIO, true);
                }
                isConference = outgongCall.hasProperty(
                        android.telecom.Call.Details.PROPERTY_GENERIC_CONFERENCE);
            }
        }
        updateButtonStates(isVideo, isConference);

        return parent;
    }

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
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // set the buttons
        updateAudioButtons();
        /// M: [Voice Record] @{
        mContext = getActivity();
        /// @}
        ExtensionManager.getVilteAutoTestHelperExt().registerReceiverForUpgradeAndDowngrade(
                mContext,getPresenter());
    }

    @Override
    public void onResume() {
        if (getPresenter() != null) {
            getPresenter().refreshMuteState();
        }
        super.onResume();

        updateColors();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        Log.d(this, "onClick(View " + view + ", id " + id + ")...");
        ///when current call is video call, click callbutton we should
        //disable VideoCallFullScreen.
        InCallPresenter.getInstance().notifyDisableVideoCallFullScreen();
        if (id == R.id.audioButton) {
            onAudioButtonClicked();
        } else if (id == R.id.addButton) {
            getPresenter().addCallClicked();
        } else if (id == R.id.muteButton) {
            getPresenter().muteClicked(!mMuteButton.isSelected());
        } else if (id == R.id.mergeButton) {
            getPresenter().mergeClicked();
            mMergeButton.setEnabled(false);
        } else if (id == R.id.holdButton) {
            getPresenter().holdClicked(!mHoldButton.isSelected());
        } else if (id == R.id.swapButton) {
            getPresenter().swapClicked();
        } else if (id == R.id.changeToVideoButton) {
            getPresenter().changeToVideoClicked();
        } else if (id == R.id.changeToVoiceButton) {
            getPresenter().changeToVoiceClicked();
        } else if (id == R.id.hideOrShowLocalVideo) {
            onHideVideoCallPreviewClick(!mHideOrShowLocalVideoButton.isSelected());
        } else if (id == R.id.switchCameraButton) {
            getPresenter().switchCameraClicked(mSwitchCameraButton.isSelected() /* useFrontFacingCamera */);
        } else if (id == R.id.pauseVideoButton) {
            getPresenter().pauseVideoClicked(!mPauseVideoButton.isSelected() /* pause */);
        } else if (id == R.id.manageVideoCallConferenceButton) {
            onManageVideoCallConferenceClicked();
        } else if (id == R.id.setEctButton) {
            getPresenter().onEctMenuSelected();
        } else if (id == R.id.hangupAllCallsButton) {
            getPresenter().hangupAllClicked();
        } else if (id == R.id.hangupAllHoldCallsButton) {
            getPresenter().hangupAllHoldCallsClicked();
        } else if (id == R.id.hangupActiveAndAnswerWaitingButton) {
            getPresenter().hangupActiveAndAnswerWaitingClicked();
        } else if (id == R.id.switch_voice_record) {
            onVoiceRecordClick((FreemeImageButtonWithText)view);
        } else {
            Log.wtf(this, "onClick: unexpected");
            return;
        }

        view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    @Override
    public void updateColors() {
        MaterialColorMapUtils.MaterialPalette themeColors = InCallPresenter.getInstance().getThemeColors();

        if (mCurrentThemeColors != null && mCurrentThemeColors.equals(themeColors)) {
            return;
        }
        if (themeColors == null) {
            return;
        }

        View[] normalButtons = {
                mSetEctButton,
                mHangupAllCallsButton,
                mHangupAllHoldCallsButton,
                mHangupActiveAndAnswerWaitingButton
        };

        for (View button : normalButtons) {
            final LayerDrawable layers = (LayerDrawable) button.getBackground();
            if (layers == null) {
                return;
            }
            final RippleDrawable drawable = backgroundDrawable(themeColors);
            layers.setDrawableByLayerId(R.id.backgroundItem, drawable);
            drawable.setState(layers.getState());
            layers.invalidateSelf();
        }

        mRCSeExt.updateNormalBgDrawable(backgroundDrawable(themeColors));
        mCurrentThemeColors = themeColors;
    }

    /**
     * Generate a RippleDrawable which will be the background for a compound button, i.e.
     * a button with pressed and unpressed states. The unpressed state will be the same color
     * as the rest of the call card, the pressed state will be the dark version of that color.
     */
    private RippleDrawable compoundBackgroundDrawable(MaterialColorMapUtils.MaterialPalette palette) {
        Resources res = getResources();
        ColorStateList rippleColor =
                ColorStateList.valueOf(res.getColor(R.color.incall_accent_color));

        StateListDrawable stateListDrawable = new StateListDrawable();
        addSelectedAndFocused(res, stateListDrawable);
        addFocused(res, stateListDrawable);
        addSelected(res, stateListDrawable, palette);
        addUnselected(res, stateListDrawable, palette);

        return new RippleDrawable(rippleColor, stateListDrawable, null);
    }

    /**
     * Generate a RippleDrawable which will be the background of a button to ensure it
     * is the same color as the rest of the call card.
     */
    private RippleDrawable backgroundDrawable(MaterialColorMapUtils.MaterialPalette palette) {
        Resources res = getResources();
        ColorStateList rippleColor =
                ColorStateList.valueOf(res.getColor(R.color.incall_accent_color));

        StateListDrawable stateListDrawable = new StateListDrawable();
        addFocused(res, stateListDrawable);
        addUnselected(res, stateListDrawable, palette);

        return new RippleDrawable(rippleColor, stateListDrawable, null);
    }

    // state_selected and state_focused
    private void addSelectedAndFocused(Resources res, StateListDrawable drawable) {
        int[] selectedAndFocused = {android.R.attr.state_selected, android.R.attr.state_focused};
        Drawable selectedAndFocusedDrawable = res.getDrawable(R.drawable.btn_selected_focused);
        drawable.addState(selectedAndFocused, selectedAndFocusedDrawable);
    }

    // state_focused
    private void addFocused(Resources res, StateListDrawable drawable) {
        int[] focused = {android.R.attr.state_focused};
        Drawable focusedDrawable = res.getDrawable(R.drawable.btn_unselected_focused);
        drawable.addState(focused, focusedDrawable);
    }

    // state_selected
    private void addSelected(Resources res, StateListDrawable drawable, MaterialColorMapUtils.MaterialPalette palette) {
        int[] selected = {android.R.attr.state_selected};
        LayerDrawable selectedDrawable = (LayerDrawable) res.getDrawable(R.drawable.btn_selected);
        ((GradientDrawable) selectedDrawable.getDrawable(0)).setColor(palette.mSecondaryColor);
        drawable.addState(selected, selectedDrawable);
    }

    // default
    private void addUnselected(Resources res, StateListDrawable drawable, MaterialColorMapUtils.MaterialPalette palette) {
        LayerDrawable unselectedDrawable =
                (LayerDrawable) res.getDrawable(R.drawable.btn_unselected);
        ((GradientDrawable) unselectedDrawable.getDrawable(0)).setColor(palette.mPrimaryColor);
        drawable.addState(new int[0], unselectedDrawable);
    }

    @Override
    public void setEnabled(boolean isEnabled) {
        mIsEnabled = isEnabled;

        mAudioButton.setEnabled(isEnabled);
        mMuteButton.setEnabled(isEnabled);
        mHoldButton.setEnabled(isEnabled);
        mSwapButton.setEnabled(isEnabled);
        mChangeToVideoButton.setEnabled(isEnabled);
        mChangeToVoiceButton.setEnabled(isEnabled);
        /// M: [Hide button] @{
        mHideOrShowLocalVideoButton.setEnabled(isEnabled);
        /// @}
        mSwitchCameraButton.setEnabled(isEnabled);
        mAddCallButton.setEnabled(isEnabled);
        mMergeButton.setEnabled(isEnabled);
        mPauseVideoButton.setEnabled(isEnabled);
        mManageVideoCallConferenceButton.setEnabled(isEnabled);

        /// M: for call button feature. @{
        mSetEctButton.setEnabled(isEnabled);
        mHangupAllCallsButton.setEnabled(isEnabled);
        mHangupAllHoldCallsButton.setEnabled(isEnabled);
        mHangupActiveAndAnswerWaitingButton.setEnabled(isEnabled);
        //[Voice Record]
        mRecordVoiceButton.setEnabled(isEnabled);
        /// @}
    }

    @Override
    public void showButton(int buttonId, boolean show) {
        switch (buttonId) {
            case BUTTON_HANGUP_ALL_CALLS:
            case BUTTON_HANGUP_ALL_HOLD_CALLS:
            case BUTTON_HANGUP_ACTIVE_AND_ANSWER_WAITING:
                show = false;
                break;
            default:
                break;
        }
        mButtonVisibilityMap.put(buttonId, show ? BUTTON_VISIBLE : BUTTON_HIDDEN);
    }

    @Override
    public void enableButton(int buttonId, boolean enable) {
        final View button = getButtonById(buttonId);
        if (button != null) {
            button.setEnabled(enable);
        }
    }

    private View getButtonById(int id) {
        if (id == BUTTON_AUDIO) {
            return mAudioButton;
        } else if (id == BUTTON_MUTE) {
            return mMuteButton;
        } else if (id == BUTTON_HOLD) {
            return mHoldButton;
        } else if (id == BUTTON_SWAP) {
            return mSwapButton;
        } else if (id == BUTTON_UPGRADE_TO_VIDEO) {
            return mChangeToVideoButton;
        } else if (id == BUTTON_DOWNGRADE_TO_AUDIO) {
            return mChangeToVoiceButton;
            /// M: [Hide button] @{
        } else if (id == BUTTON_HIDE_LOCAL_VIDEO) {
            return mHideOrShowLocalVideoButton;
            /// @}
        } else if (id == BUTTON_SWITCH_CAMERA) {
            return mSwitchCameraButton;
        } else if (id == BUTTON_ADD_CALL) {
            return mAddCallButton;
        } else if (id == BUTTON_MERGE) {
            return mMergeButton;
        } else if (id == BUTTON_PAUSE_VIDEO) {
            return mPauseVideoButton;
        } else if (id == BUTTON_MANAGE_VIDEO_CONFERENCE) {
            return mManageVideoCallConferenceButton;
        } else if (id == BUTTON_SET_ECT) {
            return mSetEctButton;
        } else if (id == BUTTON_HANGUP_ALL_CALLS) {
            return mHangupAllCallsButton;
        } else if (id == BUTTON_HANGUP_ALL_HOLD_CALLS) {
            return mHangupAllHoldCallsButton;
        } else if (id == BUTTON_HANGUP_ACTIVE_AND_ANSWER_WAITING) {
            return mHangupActiveAndAnswerWaitingButton;
        } else if (id == BUTTON_SWITCH_VOICE_RECORD) {
            return mRecordVoiceButton;
        } else {
            Log.w(this, "Invalid button id, " + id);
            return null;
        }
    }

    @Override
    public void setHold(boolean value) {
        if (mHoldButton.isSelected() != value) {
            mHoldButton.setSelected(value);
            mHoldButton.setContentDescription(getContext().getString(
                    value ? R.string.onscreenHoldText_selected
                            : R.string.onscreenHoldText_unselected));
        }
    }

    @Override
    public void setCameraSwitched(boolean isBackFacingCamera) {
        mSwitchCameraButton.setSelected(isBackFacingCamera);
    }

    @Override
    public void setVideoPaused(boolean isVideoPaused) {
        mPauseVideoButton.setSelected(isVideoPaused);
        ///M : @{
        String titleName = isVideoPaused ?
                getResources().getString(R.string.onscreenTurnOnCameraText)
                : getResources().getString(R.string.onscreenTurnOffCameraText);
        mPauseVideoButton.setContentDescription(titleName);
        updateButtonItemText(BUTTON_PAUSE_VIDEO, titleName);
        ///  @}
    }

    @Override
    public void setMute(boolean value) {
        if (mMuteButton.isSelected() != value) {
            mMuteButton.setSelected(value);
            mMuteButton.setContentDescription(getContext().getString(
                    value ? R.string.onscreenMuteText_selected
                            : R.string.onscreenMuteText_unselected));
        }
    }

    private void addToOverflowMenu(int id, View button, PopupMenu menu) {
        button.setVisibility(View.GONE);
        menu.getMenu().add(Menu.NONE, id, Menu.NONE, button.getContentDescription());
        mButtonVisibilityMap.put(id, BUTTON_MENU);
    }

    /**
     * Iterates through the list of buttons and toggles their visibility depending on the
     * setting configured by the CallButtonPresenter. If there are more visible buttons than
     * the allowed maximum, the excess buttons are collapsed into a single overflow menu.
     */
    @Override
    public void updateButtonStates() {
        // ignore
    }

    @Override
    public void updateButtonStates(boolean isVideo, boolean isConference) {
        if (mAudioModePopup != null) {
            mAudioModePopup.dismiss();
            Log.d(this, "updateButtonStates(), dissmiss mAudioModePopup before start a new one..."
                    + " older: " + mAudioModePopup);
        }
        for (int i = 0; i < BUTTON_COUNT; i++) {
            final int visibility = mButtonVisibilityMap.get(i);
            final View button = getButtonById(i);
            if (button == null) {
                continue;
            }
            if (visibility == BUTTON_VISIBLE) {
                if (i == BUTTON_SWITCH_VOICE_RECORD) {
                    mRecordVoiceLayout.setVisibility(View.VISIBLE);
                    button.setEnabled(true);
                } else if (i == BUTTON_ADD_CALL && isButtonShow(BUTTON_MERGE)) {
                    button.setVisibility(View.GONE);
                } else {
                    button.setVisibility(View.VISIBLE);
                    button.setEnabled(true);
                }
            } else if (visibility == BUTTON_HIDDEN) {
                if (i == BUTTON_MUTE
                        || i == BUTTON_AUDIO
                        || (i == BUTTON_ADD_CALL
                            && !(isButtonShow(BUTTON_MERGE) || isButtonShow(BUTTON_PAUSE_VIDEO)))) {
                    button.setVisibility(View.VISIBLE);
                    button.setEnabled(false);
                } else if (isVideo){
                    if (i == BUTTON_HIDE_LOCAL_VIDEO
                            || i == BUTTON_SWITCH_CAMERA
                            || i == BUTTON_DOWNGRADE_TO_AUDIO) {
                        button.setVisibility(View.VISIBLE);
                        button.setEnabled(i != BUTTON_DOWNGRADE_TO_AUDIO);
                    } else {
                        if (i == BUTTON_SWITCH_VOICE_RECORD) {
                            mRecordVoiceLayout.setVisibility(View.GONE);
                        } else {
                            button.setVisibility(View.GONE);
                        }
                    }
                } else {
                    if ((i == BUTTON_HOLD && !isButtonShow(BUTTON_SWAP))
                            || (i == BUTTON_UPGRADE_TO_VIDEO)
                            || (i == BUTTON_SWITCH_VOICE_RECORD)) {
                        if (i == BUTTON_SWITCH_VOICE_RECORD) {
                            mRecordVoiceLayout.setVisibility(View.VISIBLE);
                        } else {
                            button.setVisibility(View.VISIBLE);
                        }
                        button.setEnabled(false);
                    } else {
                        if (i == BUTTON_SWITCH_VOICE_RECORD) {
                            mRecordVoiceLayout.setVisibility(View.GONE);
                        } else {
                            button.setVisibility(View.GONE);
                        }
                    }
                }
            }
        }
    }

    private boolean isButtonShow(int id){
        return mButtonVisibilityMap.get(id) == BUTTON_VISIBLE;
    }

    private boolean hideButtonToShowSwap(int currentId, int hideId, int showId){
        return currentId == hideId && mButtonVisibilityMap.get(showId) == BUTTON_VISIBLE;
    }

    @Override
    public void setAudio(int mode) {
        updateAudioButtons();
        /// M: For ALPS01825524 @{
        // Telecomm will trigger AudioMode popup refresh when supported Audio
        // has been changed. Here we only update Audio Button.
        // Original Code:
        // refreshAudioModePopup();
        /// @}

        if (mPrevAudioMode != mode) {
            updateAudioButtonContentDescription(mode);
            mPrevAudioMode = mode;
        }
    }

    @Override
    public void setSupportedAudio(int modeMask) {
        updateAudioButtons();
        refreshAudioModePopup();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        Log.d(this, "- onMenuItemClick: " + item);
        Log.d(this, "  id: " + item.getItemId());
        Log.d(this, "  title: '" + item.getTitle() + "'");

        // add for plug in. @{
        if (mRCSeExt.handleMenuItemClick(item)) {
            return true;
        }
        // add for plug in. @}
        int mode = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
        int resId = item.getItemId();

        if (resId == R.id.audio_mode_speaker) {
            mode = CallAudioState.ROUTE_SPEAKER;
        } else if (resId == R.id.audio_mode_earpiece || resId == R.id.audio_mode_wired_headset) {
            // InCallCallAudioState.ROUTE_EARPIECE means either the handset earpiece,
            // or the wired headset (if connected.)
            mode = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
        } else if (resId == R.id.audio_mode_bluetooth) {
            mode = CallAudioState.ROUTE_BLUETOOTH;
        } else {
            Log.e(this, "onMenuItemClick:  unexpected View ID " + item.getItemId()
                    + " (MenuItem = '" + item + "')");
        }

        getPresenter().setAudioMode(mode);

        return true;
    }

    // PopupMenu.OnDismissListener implementation; see showAudioModePopup().
    // This gets called when the PopupMenu gets dismissed for *any* reason, like
    // the user tapping outside its bounds, or pressing Back, or selecting one
    // of the menu items.
    @Override
    public void onDismiss(PopupMenu menu) {
        Log.d(this, "- onDismiss: " + menu);
        mAudioModePopupVisible = false;
        updateAudioButtons();
    }

    /**
     * Checks for supporting modes.  If bluetooth is supported, it uses the audio
     * pop up menu.  Otherwise, it toggles the speakerphone.
     */
    private void onAudioButtonClicked() {
        Log.d(this, "onAudioButtonClicked: " +
                CallAudioState.audioRouteToString(getPresenter().getSupportedAudio()));

        if (isSupported(CallAudioState.ROUTE_BLUETOOTH)) {
            showAudioModePopup();
        } else {
            getPresenter().toggleSpeakerphone();
        }
    }

    private void onManageVideoCallConferenceClicked() {
        Log.d(this, "onManageVideoCallConferenceClicked");
        InCallPresenter.getInstance().showConferenceCallManager(true);
    }

    /**
     * Refreshes the "Audio mode" popup if it's visible.  This is useful
     * (for example) when a wired headset is plugged or unplugged,
     * since we need to switch back and forth between the "earpiece"
     * and "wired headset" items.
     *
     * This is safe to call even if the popup is already dismissed, or even if
     * you never called showAudioModePopup() in the first place.
     */
    public void refreshAudioModePopup() {
        if (mAudioModePopup != null && mAudioModePopupVisible) {
            // Dismiss the previous one
            mAudioModePopup.dismiss();  // safe even if already dismissed
            // And bring up a fresh PopupMenu
            showAudioModePopup();
        }
    }

    /**
     * Updates the audio button so that the appriopriate visual layers
     * are visible based on the supported audio formats.
     */
    private void updateAudioButtons() {
        final boolean bluetoothSupported = isSupported(CallAudioState.ROUTE_BLUETOOTH);
        final boolean speakerSupported = isSupported(CallAudioState.ROUTE_SPEAKER);

        boolean audioButtonEnabled = false;
        boolean audioButtonChecked = false;
        boolean showMoreIndicator = false;

        boolean showBluetoothIcon = false;
        boolean showSpeakerphoneIcon = false;
        boolean showHandsetIcon = false;

        boolean showToggleIndicator = false;

        if (bluetoothSupported) {
            Log.d(this, "updateAudioButtons - popup menu mode");

            audioButtonEnabled = true;
            audioButtonChecked = true;
            showMoreIndicator = true;

            // Update desired layers:
            if (isAudio(CallAudioState.ROUTE_BLUETOOTH)) {
                showBluetoothIcon = true;
            } else if (isAudio(CallAudioState.ROUTE_SPEAKER)) {
                showSpeakerphoneIcon = true;
            } else {
                showHandsetIcon = true;
                // TODO: if a wired headset is plugged in, that takes precedence
                // over the handset earpiece.  If so, maybe we should show some
                // sort of "wired headset" icon here instead of the "handset
                // earpiece" icon.  (Still need an asset for that, though.)
            }

            // The audio button is NOT a toggle in this state, so set selected to false.
            mAudioButton.setSelected(false);
        } else if (speakerSupported) {
            Log.d(this, "updateAudioButtons - speaker toggle mode");

            audioButtonEnabled = true;

            // The audio button *is* a toggle in this state, and indicated the
            // current state of the speakerphone.
            audioButtonChecked = isAudio(CallAudioState.ROUTE_SPEAKER);
            mAudioButton.setSelected(audioButtonChecked);

            // update desired layers:
            showToggleIndicator = true;
            showSpeakerphoneIcon = true;
        } else {
            Log.d(this, "updateAudioButtons - disabled...");

            // The audio button is a toggle in this state, but that's mostly
            // irrelevant since it's always disabled and unchecked.
            audioButtonEnabled = false;
            audioButtonChecked = false;
            mAudioButton.setSelected(false);

            // update desired layers:
            showToggleIndicator = true;
            showSpeakerphoneIcon = true;
        }

        // Finally, update it all!
        /** M: log reduce, AOSP Logs
         Log.v(this, "audioButtonEnabled: " + audioButtonEnabled);
         Log.v(this, "audioButtonChecked: " + audioButtonChecked);
         Log.v(this, "showMoreIndicator: " + showMoreIndicator);
         Log.v(this, "showBluetoothIcon: " + showBluetoothIcon);
         Log.v(this, "showSpeakerphoneIcon: " + showSpeakerphoneIcon);
         Log.v(this, "showHandsetIcon: " + showHandsetIcon);
         @{ */
        Log.v(this, "audioButton[" + audioButtonEnabled + "/"
                + audioButtonChecked + "], More:" + showMoreIndicator
                + ", BtIcon:" + showBluetoothIcon + ", Speaker:"
                + showSpeakerphoneIcon + ", Handset:" + showHandsetIcon);
        /** @} */

        // Only enable the audio button if the fragment is enabled.
        mAudioButton.setEnabled(audioButtonEnabled && mIsEnabled);
        mAudioButton.setSelected(audioButtonChecked || bluetoothSupported);

        if (showBluetoothIcon){
            mAudioButton.setImage(R.drawable.freeme_call_card_button_bluetooth);
            mAudioButton.setText(R.string.audio_mode_bluetooth);
        }
        if (showSpeakerphoneIcon) {
            mAudioButton.setImage(R.drawable.freeme_call_card_button_speaker);
            mAudioButton.setText(R.string.audio_mode_speaker);
        }
        if (showHandsetIcon) {
            mAudioButton.setImage(R.drawable.freeme_call_card_button_handset);
            mAudioButton.setText(R.string.audio_mode_earpiece);
        }
        if (isAudio(CallAudioState.ROUTE_WIRED_HEADSET)) {
            mAudioButton.setSelected(true);
            mAudioButton.setImage(R.drawable.freeme_call_card_button_wired_handset);
            mAudioButton.setText(R.string.audio_mode_wired_headset);
        }
    }

    /**
     * Update the content description of the audio button.
     */
    private void updateAudioButtonContentDescription(int mode) {
        int stringId = 0;

        // If bluetooth is not supported, the audio buttion will toggle, so use the label "speaker".
        // Otherwise, use the label of the currently selected audio mode.
        if (!isSupported(CallAudioState.ROUTE_BLUETOOTH)) {
            stringId = R.string.audio_mode_speaker;
        } else {
            switch (mode) {
                case CallAudioState.ROUTE_EARPIECE:
                    stringId = R.string.audio_mode_earpiece;
                    break;
                case CallAudioState.ROUTE_BLUETOOTH:
                    stringId = R.string.audio_mode_bluetooth;
                    break;
                case CallAudioState.ROUTE_WIRED_HEADSET:
                    stringId = R.string.audio_mode_wired_headset;
                    break;
                case CallAudioState.ROUTE_SPEAKER:
                    stringId = R.string.audio_mode_speaker;
                    break;
            }
        }

        if (stringId != 0) {
            mAudioButton.setContentDescription(getResources().getString(stringId));
        }
    }

    private void showAudioModePopup() {
        Log.d(this, "showAudioPopup()...");

        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(getActivity(),
                R.style.InCallPopupMenuStyle);
        mAudioModePopup = new PopupMenu(contextWrapper, mAudioButton /* anchorView */);
        mAudioModePopup.getMenuInflater().inflate(R.menu.incall_audio_mode_menu,
                mAudioModePopup.getMenu());
        mAudioModePopup.setOnMenuItemClickListener(this);
        mAudioModePopup.setOnDismissListener(this);

        final Menu menu = mAudioModePopup.getMenu();

        // TODO: Still need to have the "currently active" audio mode come
        // up pre-selected (or focused?) with a blue highlight.  Still
        // need exact visual design, and possibly framework support for this.
        // See comments below for the exact logic.

        final MenuItem speakerItem = menu.findItem(R.id.audio_mode_speaker);
        speakerItem.setEnabled(isSupported(CallAudioState.ROUTE_SPEAKER));
        // TODO: Show speakerItem as initially "selected" if
        // speaker is on.

        // We display *either* "earpiece" or "wired headset", never both,
        // depending on whether a wired headset is physically plugged in.
        final MenuItem earpieceItem = menu.findItem(R.id.audio_mode_earpiece);
        final MenuItem wiredHeadsetItem = menu.findItem(R.id.audio_mode_wired_headset);

        final boolean usingHeadset = isSupported(CallAudioState.ROUTE_WIRED_HEADSET);
        earpieceItem.setVisible(!usingHeadset);
        earpieceItem.setEnabled(!usingHeadset);
        wiredHeadsetItem.setVisible(usingHeadset);
        wiredHeadsetItem.setEnabled(usingHeadset);
        // TODO: Show the above item (either earpieceItem or wiredHeadsetItem)
        // as initially "selected" if speakerOn and
        // bluetoothIndicatorOn are both false.

        final MenuItem bluetoothItem = menu.findItem(R.id.audio_mode_bluetooth);
        bluetoothItem.setEnabled(isSupported(CallAudioState.ROUTE_BLUETOOTH));
        // TODO: Show bluetoothItem as initially "selected" if
        // bluetoothIndicatorOn is true.

        mAudioModePopup.show();

        // Unfortunately we need to manually keep track of the popup menu's
        // visiblity, since PopupMenu doesn't have an isShowing() method like
        // Dialogs do.
        mAudioModePopupVisible = true;
    }

    private boolean isSupported(int mode) {
        return (mode == (getPresenter().getSupportedAudio() & mode));
    }

    private boolean isAudio(int mode) {
        return (mode == getPresenter().getAudioMode());
    }

    @Override
    public void displayDialpad(boolean value, boolean animate) {
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            ((InCallActivity) getActivity()).updateCallCardBtnStatus(value, true);
            // when displayDialpad should also update dialpad request.
            // 1 = DIALPAD_REQUEST_NONE, 2 = DIALPAD_REQUEST_SHOW;
            ((InCallActivity) getActivity()).setShowDialpadRequested(value ? 2 : 1);
        }
    }

    @Override
    public boolean isDialpadVisible() {
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            return ((InCallActivity) getActivity()).isDialpadVisible();
        }
        return false;
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    // ---------------------------------------Mediatek-------------------------------------

    /// M: for plugin.
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        /// M: Add for plugin.
        mRCSeExt.onViewCreated(getActivity(), view);
    }

    /** M: [Voice Record] @{ */
    private void onVoiceRecordClick(FreemeImageButtonWithText bt) {
        String desc = bt.getContentDescription().toString();
        Log.d(this, "onVoiceRecordClick " + desc);
        if (desc == null) {
            return;
        }
        if (!PhoneRecorderUtils.isExternalStorageMounted(mContext)) {
            Toast.makeText(
                    mContext,
                    mContext.getResources().getString(
                            R.string.error_sdcard_access), Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (!PhoneRecorderUtils
                .diskSpaceAvailable(PhoneRecorderUtils.PHONE_RECORD_LOW_STORAGE_THRESHOLD)) {
            InCallPresenter.getInstance().handleStorageFull(true); // true for
            // checking
            // case
            return;
        }

        if (desc.equals(getString(R.string.start_record))) {
            getPresenter().voiceRecordClicked();
        } else if (desc.equals(getString(R.string.stop_record))) {
            getPresenter().stopRecordClicked();
        }
    }

    /**
     * M: configure recording button.
     */
    @Override
    public void configRecordingButton() {
        boolean isRecording = InCallPresenter.getInstance().isRecording();
        //update for tablet and CT require.
        mRecordVoiceButton.setSelected(isRecording);

        mRecordVoiceButton
                .setContentDescription(getString(isRecording ? R.string.stop_record
                        : R.string.start_record));

        String recordTitle = isRecording ? getString(R.string.stop_record)
                : getString(R.string.start_record);
        updateButtonItemText(BUTTON_SWITCH_VOICE_RECORD, recordTitle);

    }
    /** @} */

    @Override
    public void enableOverflowButton() {
        //ignore
    }

    /**
     * updateButtonItemText according to the param title
     * @param itemId related with the button which you want to operate.
     * @param title  popmenu will show
     */
    private void updateButtonItemText(int itemId, String title) {
        View view = getButtonById(itemId);
        if (view instanceof FreemeImageButtonWithText) {
            ((FreemeImageButtonWithText) view).setText(title);
        }
    }

    /// M: click event for hide local preview
    private void onHideVideoCallPreviewClick(boolean hide) {
        Log.d(this, "onHideVideoCallPreviewClick hide: " + hide);
        InCallPresenter.getInstance().notifyHideLocalVideoChanged(hide);
        updateHideButtonStatus(hide);

    }

    public void updateHideButtonStatus(boolean hide) {
        mHideOrShowLocalVideoButton.setSelected(hide);
        String title = hide ? getString(R.string.freeme_showVideoPreview)
                : getString(R.string.freeme_hideVideoPreview);
        mHideOrShowLocalVideoButton.setContentDescription(title);
        updateButtonItemText(BUTTON_HIDE_LOCAL_VIDEO, title);
    }

    private boolean animationLive = false;
    public void showCallButton(boolean show, final boolean dialpad) {
        if (animationLive) return;
        TranslateAnimation anim;
        if (show) {
            anim = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 1,
                    Animation.RELATIVE_TO_SELF, 0);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationEnd(Animation animation) {
                    Fragment fragment = getParentFragment();
                    if (fragment != null && fragment instanceof FreemeCallCardFragment) {
                        FreemeCallCardFragment mCallCardFragment = (FreemeCallCardFragment) fragment;
                        if (mCallCardFragment.isConferenceCall()) {
                            mCallCardFragment.showManageConferenceCallButton(true);
                        }
                    }
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationStart(Animation animation) {
                }
            });
        } else {
            anim = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 1);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationEnd(Animation animation) {
                    animationLive = false;
                    ((InCallActivity) getActivity()).showDialpadFragment(true, true);
                    ((InCallActivity) getActivity()).updateCallCardBtnStatus(true, dialpad);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationStart(Animation animation) {
                }
            });
            animationLive = true;
        }
        anim.setDuration(300);
        anim.setFillAfter(true);
        mCallButtonContainer.startAnimation(anim);
    }

    public void hideCallButtonImmediately() {
        TranslateAnimation anim = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 1);
        anim.setDuration(0);
        anim.setFillAfter(true);
        mCallButtonContainer.startAnimation(anim);
    }
}
