/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.dialpad;

import com.google.common.annotations.VisibleForTesting;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Trace;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.provider.CallLog;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.dialog.CallSubjectDialog;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.util.PhoneNumberFormatter;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.StopWatch;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.NeededForReflection;
import com.android.dialer.R;
import com.android.dialer.SpecialCharSequenceMgr;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.ims.ImsManager;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.IntentUtil.CallIntentBuilder;
import com.android.dialer.util.TelecomUtil;
import com.android.incallui.Call.LogState;
import com.android.phone.common.CallLogAsync;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.dialpad.DialpadKeyButton;
import com.android.phone.common.dialpad.DialpadView;

import com.mediatek.dialer.ext.DialpadExtensionAction;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.DialerFeatureOptions;

import com.mediatek.dialer.util.DialerVolteUtils;
import com.mediatek.ims.WfcReasonInfo;
import com.android.internal.telephony.ITelephony;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.telecom.TelecomManagerEx;

import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
//*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2, dual sim
import android.telecom.VideoProfile;
import com.android.dialer.list.ListsFragment;
import com.mediatek.telephony.TelephonyManagerEx;
//*/

/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class DialpadFragment extends Fragment
        implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener,
        AdapterView.OnItemClickListener, TextWatcher,
        PopupMenu.OnMenuItemClickListener,
        DialpadKeyButton.OnPressedListener,
        /// M: add for plug-in @{
        DialpadExtensionAction {
        /// @}
    private static final String TAG = "DialpadFragment";

    /**
     * LinearLayout with getter and setter methods for the translationY property using floats,
     * for animation purposes.
     */
    public static class DialpadSlidingRelativeLayout extends RelativeLayout {

        public DialpadSlidingRelativeLayout(Context context) {
            super(context);
        }

        public DialpadSlidingRelativeLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public DialpadSlidingRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @NeededForReflection
        public float getYFraction() {
            final int height = getHeight();
            if (height == 0) return 0;
            return getTranslationY() / height;
        }

        @NeededForReflection
        public void setYFraction(float yFraction) {
            setTranslationY(yFraction * getHeight());
        }
    }

    public interface OnDialpadQueryChangedListener {
        void onDialpadQueryChanged(String query);
    }

    public interface HostInterface {
        /**
         * Notifies the parent activity that the space above the dialpad has been tapped with
         * no query in the dialpad present. In most situations this will cause the dialpad to
         * be dismissed, unless there happens to be content showing.
         */
        boolean onDialpadSpacerTouchWithEmptyQuery();
    }

    private static final boolean DEBUG = DialtactsActivity.DEBUG;

    // This is the amount of screen the dialpad fragment takes up when fully displayed
    private static final float DIALPAD_SLIDE_FRACTION = 0.67f;

    private static final String EMPTY_NUMBER = "";
    private static final char PAUSE = ',';
    private static final char WAIT = ';';

    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 150;
    private static final int TONE_LENGTH_INFINITE = -1;

    /** The DTMF tone volume relative to other sounds in the stream */
    private static final int TONE_RELATIVE_VOLUME = 80;

    /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;


    private OnDialpadQueryChangedListener mDialpadQueryListener;

    private DialpadView mDialpadView;
    private EditText mDigits;
    private int mDialpadSlideInDuration;

    /** Remembers if we need to clear digits field when the screen is completely gone. */
    private boolean mClearDigitsOnStop;

    private View mOverflowMenuButton;
    private PopupMenu mOverflowPopupMenu;
    private View mDelete;
    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();
    private View mSpacer;

    private FloatingActionButtonController mFloatingActionButtonController;

    ///M:  WFC @{
    private static final String SCHEME_TEL = PhoneAccount.SCHEME_TEL;
    private static final int DIALPAD_WFC_NOTIFICATION_ID = 2;
    private Context mContext;
    private int mNotificationCount;
    private Timer mNotificationTimer;
    private NotificationManager mNotificationManager;
    /// @}
    /**
     * Set of dialpad keys that are currently being pressed
     */
    private final HashSet<View> mPressedDialpadKeys = new HashSet<View>(12);

    private ListView mDialpadChooser;
    private DialpadChooserAdapter mDialpadChooserAdapter;

    /**
     * Regular expression prohibiting manual phone call. Can be empty, which means "no rule".
     */
    private String mProhibitedPhoneNumberRegexp;

    private PseudoEmergencyAnimator mPseudoEmergencyAnimator;

    // Last number dialed, retrieved asynchronously from the call DB
    // in onCreate. This number is displayed when the user hits the
    // send key and cleared in onPause.
    private final CallLogAsync mCallLog = new CallLogAsync();
    private String mLastNumberDialed = EMPTY_NUMBER;

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    /** Identifier for the "Add Call" intent extra. */
    private static final String ADD_CALL_MODE_KEY = "add_call_mode";

    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Using an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an Telephony/TelecomManager call in the future.
     * TODO: Keep in sync with the string defined in OutgoingCallBroadcaster.java
     * in Phone app until this is replaced with the Telephony/Telecom API.
     */
    private static final String EXTRA_SEND_EMPTY_FLASH
            = "com.android.phone.extra.SEND_EMPTY_FLASH";

    private String mCurrentCountryIso;

    private CallStateReceiver mCallStateReceiver;

    private class CallStateReceiver extends BroadcastReceiver {
        /**
         * Receive call state changes so that we can take down the
         * "dialpad chooser" if the phone becomes idle while the
         * chooser UI is visible.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            // Log.i(TAG, "CallStateReceiver.onReceive");
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if ((TextUtils.equals(state, TelephonyManager.EXTRA_STATE_IDLE) ||
                    TextUtils.equals(state, TelephonyManager.EXTRA_STATE_OFFHOOK))
                    && isDialpadChooserVisible()) {
                // Log.i(TAG, "Call ended with dialpad chooser visible!  Taking it down...");
                // Note there's a race condition in the UI here: the
                // dialpad chooser could conceivably disappear (on its
                // own) at the exact moment the user was trying to select
                // one of the choices, which would be confusing.  (But at
                // least that's better than leaving the dialpad chooser
                // onscreen, but useless...)
                showDialpadChooser(false);
            }
        }
    }

    private boolean mWasEmptyBeforeTextChange;

    /**
     * This field is set to true while processing an incoming DIAL intent, in order to make sure
     * that SpecialCharSequenceMgr actions can be triggered by user input but *not* by a
     * tel: URI passed by some other app.  It will be set to false when all digits are cleared.
     */
    private boolean mDigitsFilledByIntent;

    private boolean mStartedFromNewIntent = false;
    private boolean mFirstLaunch = false;
    private boolean mAnimate = false;

    private static final String PREF_DIGITS_FILLED_BY_INTENT = "pref_digits_filled_by_intent";

    /** M: [VoLTE ConfCall] indicated phone account has volte conference capability. @{ */
    private boolean mVolteConfCallEnabled = false;
    /** @}*/

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public Context getContext() {
        return getActivity();
    }
    private ITelephony getITelephony() {
        return ITelephony.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE));
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
    }

    @Override
    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
            final Activity activity = getActivity();
            if (activity != null) {
                activity.invalidateOptionsMenu();
                updateMenuOverflowButton(mWasEmptyBeforeTextChange);
            }
        }

        // DTMF Tones do not need to be played here any longer -
        // the DTMF dialer handles that functionality now.
    }

    @Override
    public void afterTextChanged(Editable input) {
        /// M: avoid NPE if this callback is called after activity finished @{
        if (getActivity() == null) {
            return;
        }
        /// @}
        // When DTMF dialpad buttons are being pressed, we delay SpecialCharSequenceMgr sequence,
        // since some of SpecialCharSequenceMgr's behavior is too abrupt for the "touch-down"
        // behavior.
        if (!mDigitsFilledByIntent &&
                SpecialCharSequenceMgr.handleChars(getActivity(), input.toString(), mDigits)) {
            // A special sequence was entered, clear the digits
            mDigits.getText().clear();
        }

        if (isDigitsEmpty()) {
            mDigitsFilledByIntent = false;
            mDigits.setCursorVisible(false);
        }

        if (mDialpadQueryListener != null) {
            mDialpadQueryListener.onDialpadQueryChanged(mDigits.getText().toString());
        }

        updateDeleteButtonEnabledState();
    }

    @Override
    public void onCreate(Bundle state) {
        Trace.beginSection(TAG + " onCreate");
        super.onCreate(state);

        mFirstLaunch = state == null;

        mCurrentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());

        mProhibitedPhoneNumberRegexp = getResources().getString(
                R.string.config_prohibited_phone_number_regexp);

        if (state != null) {
            mDigitsFilledByIntent = state.getBoolean(PREF_DIGITS_FILLED_BY_INTENT);
        }

        mDialpadSlideInDuration = getResources().getInteger(R.integer.dialpad_slide_in_duration);

        if (mCallStateReceiver == null) {
            IntentFilter callStateIntentFilter = new IntentFilter(
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            mCallStateReceiver = new CallStateReceiver();
            ((Context) getActivity()).registerReceiver(mCallStateReceiver, callStateIntentFilter);
        }

        /// M: for Plug-in @{
        ExtensionManager.getInstance().getDialPadExtension().onCreate(
                getActivity().getApplicationContext(), this, this);
        /// @}

        Trace.endSection();
    }

    /**
     * M: for plug-in, init customer view.
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Trace.beginSection(TAG + " onViewCreated init plugin");
        ExtensionManager.getInstance().getDialPadExtension().onViewCreated(getActivity(), view);
        Trace.endSection();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        Trace.beginSection(TAG + " onCreateView");
        Trace.beginSection(TAG + " inflate view");
        final View fragmentView = inflater.inflate(R.layout.dialpad_fragment, container,
                false);
        Trace.endSection();
        Trace.beginSection(TAG + " buildLayer");
        fragmentView.buildLayer();
        Trace.endSection();

        ///M: WFC @{
        mContext = getActivity();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED);
        filter.addAction(TelecomManagerEx.ACTION_DEFAULT_ACCOUNT_CHANGED);
        mContext.registerReceiver(mReceiver, filter);

        ///@}
        /// M: for plug-in @{
        Trace.beginSection(TAG + " init plugin view");
        ExtensionManager.getInstance().getDialPadExtension().onCreateView(inflater, container,
                savedState, fragmentView);
        Trace.endSection();
        /// @}

        Trace.beginSection(TAG + " setup views");

        mDialpadView = (DialpadView) fragmentView.findViewById(R.id.dialpad_view);
        mDialpadView.setCanDigitsBeEdited(true);
        mDigits = mDialpadView.getDigits();
        mDigits.setKeyListener(UnicodeDialerKeyListener.INSTANCE);
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        mDigits.setOnLongClickListener(this);
        mDigits.addTextChangedListener(this);
        mDigits.setElegantTextHeight(false);
        PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity(), mDigits);
        // Check for the presence of the keypad
        View oneButton = fragmentView.findViewById(R.id.one);
        if (oneButton != null) {
            configureKeypadListeners(fragmentView);
        }

        mDelete = mDialpadView.getDeleteButton();

        if (mDelete != null) {
            mDelete.setOnClickListener(this);
            mDelete.setOnLongClickListener(this);
        }

        mSpacer = fragmentView.findViewById(R.id.spacer);
        mSpacer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isDigitsEmpty()) {
                    if (getActivity() != null) {
                        return ((HostInterface) getActivity()).onDialpadSpacerTouchWithEmptyQuery();
                    }
                    return true;
                }
                return false;
            }
        });

        mDigits.setCursorVisible(false);

        // Set up the "dialpad chooser" UI; see showDialpadChooser().
        mDialpadChooser = (ListView) fragmentView.findViewById(R.id.dialpadChooser);
        mDialpadChooser.setOnItemClickListener(this);

        /*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2
        final View floatingActionButtonContainer =
                fragmentView.findViewById(R.id.dialpad_floating_action_button_container);
        final ImageButton floatingActionButton =
                (ImageButton) fragmentView.findViewById(R.id.dialpad_floating_action_button);

        /// M: Need to check if floatingActionButton is null. because in CT
        // project, OP09 plugin will modify Dialpad layout and floatingActionButton
        // will be null in that case. @{
        if (null != floatingActionButton) {
            floatingActionButton.setOnClickListener(this);
            mFloatingActionButtonController = new FloatingActionButtonController(getActivity(),
                    floatingActionButtonContainer, floatingActionButton);
        }
        /// @}
        /*/
        mDialsim1Button = (ImageButton) fragmentView.findViewById(R.id.dialsim1Button);
        mDialsim2Button = (ImageButton) fragmentView.findViewById(R.id.dialsim2Button);
        mDeleteButton = (ImageButton) fragmentView.findViewById(R.id.delete_button);
        ImageButton dialpadButton = (ImageButton) fragmentView.findViewById(R.id.dialpad_button);
        mDialsim1Button.setOnClickListener(this);
        mDialsim2Button.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);
        mDeleteButton.setOnLongClickListener(this);
        dialpadButton.setOnClickListener(this);

        showDialButton();
        //*/

        /// M: Fix CR ALPS01863413. Update text field view for ADN query.
        SpecialCharSequenceMgr.updateTextFieldView(mDigits);

        Trace.endSection();
        Trace.endSection();
        return fragmentView;
    }

    ///M: WFC @{
   /* *
      * Update the dialer icon based on WFC is registered or not.
      *
      */
    private void updateWfcUI() {
        /*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2, ui redesign
        final View floatingActionButton =
                (ImageButton) getView().findViewById(R.id.dialpad_floating_action_button);
        if (floatingActionButton != null) {
            ImageView dialIcon = (ImageView) floatingActionButton;
            PhoneAccountHandle defaultAccountHandle =
                    TelecomUtil.getDefaultOutgoingPhoneAccount(getActivity(), SCHEME_TEL);
            if (defaultAccountHandle != null) {
                PhoneAccount phoneAccount = TelecomUtil.getPhoneAccount(getActivity(),
                        defaultAccountHandle);
                if (phoneAccount != null &&
                        phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_WIFI_CALLING)) {
                    dialIcon.setImageDrawable(getResources()
                            .getDrawable(R.drawable.mtk_fab_ic_wfc));
                    Log.i(TAG, "[WFC] Dial Icon is of WFC");
                } else {
                    dialIcon.setImageDrawable(getResources()
                            .getDrawable(R.drawable.fab_ic_call));
                    Log.i(TAG, "[WFC] Dial Icon not replaced");
                }
            } else {
                dialIcon.setImageDrawable(getResources().getDrawable(R.drawable.fab_ic_call));
                Log.i(TAG, "[WFC] Icon replaced");
            }
        }
        //*/
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelecomManagerEx.ACTION_PHONE_ACCOUNT_CHANGED.equals(action)
                    || TelecomManagerEx.ACTION_DEFAULT_ACCOUNT_CHANGED.equals(action)) {
                Log.i(TAG, "[WFC] Intent recived is " + intent.getAction());
                updateWfcUI();
            }
        }
    };
    ///@}
    private boolean isLayoutReady() {
        return mDigits != null;
    }

    @VisibleForTesting
    public EditText getDigitsWidget() {
        return mDigits;
    }

    /**
     * @return true when {@link #mDigits} is actually filled by the Intent.
     */
    private boolean fillDigitsIfNecessary(Intent intent) {
        // Only fills digits from an intent if it is a new intent.
        // Otherwise falls back to the previously used number.
        if (!mFirstLaunch && !mStartedFromNewIntent) {
            return false;
        }

        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                if (PhoneAccount.SCHEME_TEL.equals(uri.getScheme())) {
                    // Put the requested number into the input area
                    String data = uri.getSchemeSpecificPart();
                    // Remember it is filled via Intent.
                    mDigitsFilledByIntent = true;
                    final String converted = PhoneNumberUtils.convertKeypadLettersToDigits(
                            PhoneNumberUtils.replaceUnicodeDigits(data));
                    setFormattedDigits(converted, null);
                    return true;
                } else {
                    if (!PermissionsUtil.hasContactsPermissions(getActivity())) {
                        return false;
                    }
                    String type = intent.getType();
                    if (People.CONTENT_ITEM_TYPE.equals(type)
                            || Phones.CONTENT_ITEM_TYPE.equals(type)) {
                        // Query the phone number
                        Cursor c = getActivity().getContentResolver().query(intent.getData(),
                                new String[] {PhonesColumns.NUMBER, PhonesColumns.NUMBER_KEY},
                                null, null, null);
                        if (c != null) {
                            try {
                                if (c.moveToFirst()) {
                                    // Remember it is filled via Intent.
                                    mDigitsFilledByIntent = true;
                                    // Put the number into the input area
                                    setFormattedDigits(c.getString(0), c.getString(1));
                                    return true;
                                }
                            } finally {
                                c.close();
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determines whether an add call operation is requested.
     *
     * @param intent The intent.
     * @return {@literal true} if add call operation was requested.  {@literal false} otherwise.
     */
    public static boolean isAddCallMode(Intent intent) {
        if (intent == null) {
            return false;
        }
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // see if we are "adding a call" from the InCallScreen; false by default.
            return intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);
        } else {
            return false;
        }
    }

    /**
     * Checks the given Intent and changes dialpad's UI state. For example, if the Intent requires
     * the screen to enter "Add Call" mode, this method will show correct UI for the mode.
     */
    private void configureScreenFromIntent(Activity parent) {
        // If we were not invoked with a DIAL intent,
        if (!(parent instanceof DialtactsActivity)) {
            setStartedFromNewIntent(false);
            return;
        }
        // See if we were invoked with a DIAL intent. If we were, fill in the appropriate
        // digits in the dialer field.
        Intent intent = parent.getIntent();

        if (!isLayoutReady()) {
            // This happens typically when parent's Activity#onNewIntent() is called while
            // Fragment#onCreateView() isn't called yet, and thus we cannot configure Views at
            // this point. onViewCreate() should call this method after preparing layouts, so
            // just ignore this call now.
            Log.i(TAG,
                    "Screen configuration is requested before onCreateView() is called. Ignored");
            return;
        }

        boolean needToShowDialpadChooser = false;

        // Be sure *not* to show the dialpad chooser if this is an
        // explicit "Add call" action, though.
        final boolean isAddCallMode = isAddCallMode(intent);
        if (!isAddCallMode) {

            // Don't show the chooser when called via onNewIntent() and phone number is present.
            // i.e. User clicks a telephone link from gmail for example.
            // In this case, we want to show the dialpad with the phone number.
            final boolean digitsFilled = fillDigitsIfNecessary(intent);
            if (!(mStartedFromNewIntent && digitsFilled)) {

                final String action = intent.getAction();
                if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)
                        || Intent.ACTION_MAIN.equals(action)) {
                    // If there's already an active call, bring up an intermediate UI to
                    // make the user confirm what they really want to do.
                    if (isPhoneInUse()) {
                        /*/ freeme.zhaozehong, 20170913. for freemeOS, do not show dialpad chooser
                        needToShowDialpadChooser = true;
                        /*/
                        needToShowDialpadChooser = false;
                        //*/
                    }
                }

            }
        }
        showDialpadChooser(needToShowDialpadChooser);
        setStartedFromNewIntent(false);
    }

    public void setStartedFromNewIntent(boolean value) {
        mStartedFromNewIntent = value;
    }

    public void clearCallRateInformation() {
        setCallRateInformation(null, null);
    }

    public void setCallRateInformation(String countryName, String displayRate) {
        mDialpadView.setCallRateInformation(countryName, displayRate);
    }

    /**
     * Sets formatted digits to digits field.
     */
    private void setFormattedDigits(String data, String normalizedNumber) {
        final String formatted = getFormattedDigits(data, normalizedNumber, mCurrentCountryIso);
        if (!TextUtils.isEmpty(formatted)) {
            Editable digits = mDigits.getText();
            digits.replace(0, digits.length(), formatted);
            // for some reason this isn't getting called in the digits.replace call above..
            // but in any case, this will make sure the background drawable looks right
            afterTextChanged(digits);
        }
    }

    /**
     * Format the provided string of digits into one that represents a properly formatted phone
     * number.
     *
     * @param dialString String of characters to format
     * @param normalizedNumber the E164 format number whose country code is used if the given
     *         phoneNumber doesn't have the country code.
     * @param countryIso The country code representing the format to use if the provided normalized
     *         number is null or invalid.
     * @return the provided string of digits as a formatted phone number, retaining any
     *         post-dial portion of the string.
     */
    @VisibleForTesting
    static String getFormattedDigits(String dialString, String normalizedNumber, String countryIso) {
        String number = PhoneNumberUtils.extractNetworkPortion(dialString);
        // Also retrieve the post dial portion of the provided data, so that the entire dial
        // string can be reconstituted later.
        final String postDial = PhoneNumberUtils.extractPostDialPortion(dialString);

        if (TextUtils.isEmpty(number)) {
            return postDial;
        }

        number = PhoneNumberUtils.formatNumber(number, normalizedNumber, countryIso);

        if (TextUtils.isEmpty(postDial)) {
            return number;
        }

        return number.concat(postDial);
    }

    private void configureKeypadListeners(View fragmentView) {
        final int[] buttonIds = new int[] {R.id.one, R.id.two, R.id.three, R.id.four, R.id.five,
                R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.star, R.id.zero, R.id.pound};

        DialpadKeyButton dialpadKey;

        for (int i = 0; i < buttonIds.length; i++) {
            dialpadKey = (DialpadKeyButton) fragmentView.findViewById(buttonIds[i]);
            dialpadKey.setOnPressedListener(this);
        }

        // Long-pressing one button will initiate Voicemail.
        final DialpadKeyButton one = (DialpadKeyButton) fragmentView.findViewById(R.id.one);
        one.setOnLongClickListener(this);

        // Long-pressing zero button will enter '+' instead.
        final DialpadKeyButton zero = (DialpadKeyButton) fragmentView.findViewById(R.id.zero);
        zero.setOnLongClickListener(this);

        //*/ freeme.zhaozehong, 05/07/17. Long-pressing star button will enter ',' instead.
        final DialpadKeyButton star = (DialpadKeyButton) fragmentView.findViewById(R.id.star);
        star.setOnLongClickListener(this);
        //*/
    }

    @Override
    public void onStart() {
        Trace.beginSection(TAG + " onStart");
        super.onStart();
        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        final long start = System.currentTimeMillis();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
        final long total = System.currentTimeMillis() - start;
        if (total > 50) {
            Log.i(TAG, "Time for ToneGenerator creation: " + total);
        }
        Trace.endSection();
    };

    //*/ freeme.zhaozehong, 20170908. for reset call back listener
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (getActivity() instanceof DialtactsActivity){
            mDialpadQueryListener = (DialtactsActivity) getActivity();
        }
    }
    //*/

    @Override
    public void onResume() {
        Trace.beginSection(TAG + " onResume");
        super.onResume();

        /// M: [VoLTE ConfCall] initialize value about conference call capability.
        mVolteConfCallEnabled = supportOneKeyConference(getActivity());
        Log.d(TAG, "onResume mVolteConfCallEnabled = " + mVolteConfCallEnabled);

        /*/ freeme.zhaozehong, 20170908. for reset call back listener
        final DialtactsActivity activity = (DialtactsActivity) getActivity();
        mDialpadQueryListener = activity;
        //*/

        final StopWatch stopWatch = StopWatch.start("Dialpad.onResume");

        // Query the last dialed number. Do it first because hitting
        // the DB is 'slow'. This call is asynchronous.
        queryLastOutgoingCall();

        stopWatch.lap("qloc");

        /*/ freeme.zhaozehong, 20170908. for get ContentResolver
        final ContentResolver contentResolver = activity.getContentResolver();
        /*/
        final ContentResolver contentResolver = getActivity().getContentResolver();
        //*/

        /// M: [ALPS01858019] add listener to observer CallLog changes
        contentResolver.registerContentObserver(CallLog.CONTENT_URI, true, mCallLogObserver);

        // retrieve the DTMF tone play back setting.
        mDTMFToneEnabled = Settings.System.getInt(contentResolver,
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

        stopWatch.lap("dtwd");

        stopWatch.lap("hptc");

        mPressedDialpadKeys.clear();

        configureScreenFromIntent(getActivity());

        stopWatch.lap("fdin");

        if (!isPhoneInUse()) {
            // A sanity-check: the "dialpad chooser" UI should not be visible if the phone is idle.
            showDialpadChooser(false);
        }

        ///M: WFC @{
        updateWfcUI();
        ///@}
        stopWatch.lap("hnt");

        updateDeleteButtonEnabledState();

        stopWatch.lap("bes");

        stopWatch.stopAndLog(TAG, 50);

        // Populate the overflow menu in onResume instead of onCreate, so that if the SMS activity
        // is disabled while Dialer is paused, the "Send a text message" option can be correctly
        // removed when resumed.
        mOverflowMenuButton = mDialpadView.getOverflowMenuButton();
        mOverflowPopupMenu = buildOptionsMenu(mOverflowMenuButton);
        mOverflowMenuButton.setOnTouchListener(mOverflowPopupMenu.getDragToOpenListener());
        mOverflowMenuButton.setOnClickListener(this);
        mOverflowMenuButton.setVisibility(isDigitsEmpty() ? View.INVISIBLE : View.VISIBLE);
        /** M: [VoLTE ConfCall] Always show overflow menu button for conf call. @{ */
        if (mVolteConfCallEnabled) {
            mOverflowMenuButton.setVisibility(View.VISIBLE);
            mOverflowMenuButton.setAlpha(1);
        }
        /** @} */

        if (mFirstLaunch) {
            // The onHiddenChanged callback does not get called the first time the fragment is
            // attached, so call it ourselves here.
            onHiddenChanged(false);
        }

        mFirstLaunch = false;
        Trace.endSection();

        //*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2, dual sim
        showDialButton();
        //*/
    }

    @Override
    public void onPause() {
        super.onPause();

        // Make sure we don't leave this activity with a tone still playing.
        stopTone();
        mPressedDialpadKeys.clear();

        // TODO: I wonder if we should not check if the AsyncTask that
        // lookup the last dialed number has completed.
        mLastNumberDialed = EMPTY_NUMBER;  // Since we are going to query again, free stale number.

        SpecialCharSequenceMgr.cleanup();

        /// M: [ALPS01858019] add unregister the call log observer.
        getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
    }

    @Override
    public void onStop() {
        super.onStop();

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }

        if (mClearDigitsOnStop) {
            mClearDigitsOnStop = false;
            clearDialpad();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PREF_DIGITS_FILLED_BY_INTENT, mDigitsFilledByIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPseudoEmergencyAnimator != null) {
            mPseudoEmergencyAnimator.destroy();
            mPseudoEmergencyAnimator = null;
        }
        ((Context) getActivity()).unregisterReceiver(mCallStateReceiver);
        /// M: for plug-in. @{
        ExtensionManager.getInstance().getDialPadExtension().onDestroy();
        /// @}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ///M: WFC @{
        mContext.unregisterReceiver(mReceiver);
        ///@}
    }
    private void keyPressed(int keyCode) {
        if (getView() == null || getView().getTranslationY() != 0) {
            return;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_2:
                playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_3:
                playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_4:
                playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_5:
                playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_6:
                playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_7:
                playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_8:
                playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_9:
                playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_0:
                playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_POUND:
                playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_STAR:
                playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_INFINITE);
                break;
            default:
                break;
        }

        getView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);

        // If the cursor is at the end of the text we hide it.
        final int length = mDigits.length();
        if (length == mDigits.getSelectionStart() && length == mDigits.getSelectionEnd()) {
            mDigits.setCursorVisible(false);
        }
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        if (view.getId() == R.id.digits) {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                handleDialButtonPressed();
                return true;
            }

        }
        return false;
    }

    /**
     * When a key is pressed, we start playing DTMF tone, do vibration, and enter the digit
     * immediately. When a key is released, we stop the tone. Note that the "key press" event will
     * be delivered by the system with certain amount of delay, it won't be synced with user's
     * actual "touch-down" behavior.
     */
    @Override
    public void onPressed(View view, boolean pressed) {
        /** M: Prevent the event if dialpad is not shown. @{ */
        if (pressed && getActivity() != null
                && !((DialtactsActivity)getActivity()).isDialpadShown()) {
            Log.d(TAG, "onPressed but dialpad is not shown, skip !!!");
            return;
        }
        /** @} */
        if (DEBUG) Log.d(TAG, "onPressed(). view: " + view + ", pressed: " + pressed);
        if (pressed) {
            int resId = view.getId();
            if (resId == R.id.one) {
                keyPressed(KeyEvent.KEYCODE_1);
            } else if (resId == R.id.two) {
                keyPressed(KeyEvent.KEYCODE_2);
            } else if (resId == R.id.three) {
                keyPressed(KeyEvent.KEYCODE_3);
            } else if (resId == R.id.four) {
                keyPressed(KeyEvent.KEYCODE_4);
            } else if (resId == R.id.five) {
                keyPressed(KeyEvent.KEYCODE_5);
            } else if (resId == R.id.six) {
                keyPressed(KeyEvent.KEYCODE_6);
            } else if (resId == R.id.seven) {
                keyPressed(KeyEvent.KEYCODE_7);
            } else if (resId == R.id.eight) {
                keyPressed(KeyEvent.KEYCODE_8);
            } else if (resId == R.id.nine) {
                keyPressed(KeyEvent.KEYCODE_9);
            } else if (resId == R.id.zero) {
                keyPressed(KeyEvent.KEYCODE_0);
            } else if (resId == R.id.pound) {
                keyPressed(KeyEvent.KEYCODE_POUND);
            } else if (resId == R.id.star) {
                keyPressed(KeyEvent.KEYCODE_STAR);
            } else {
                Log.wtf(TAG, "Unexpected onTouch(ACTION_DOWN) event from: " + view);
            }
            mPressedDialpadKeys.add(view);
        } else {
            mPressedDialpadKeys.remove(view);
            if (mPressedDialpadKeys.isEmpty()) {
                stopTone();
            }
        }
    }

    /**
     * Called by the containing Activity to tell this Fragment to build an overflow options
     * menu for display by the container when appropriate.
     *
     * @param invoker the View that invoked the options menu, to act as an anchor location.
     */
    private PopupMenu buildOptionsMenu(View invoker) {
        final PopupMenu popupMenu = new PopupMenu(getActivity(), invoker) {
            @Override
            public void show() {
                final Menu menu = getMenu();

                boolean enable = !isDigitsEmpty();
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem item = menu.getItem(i);
                    /// M: [VoLTE ConfCall] Change visible to hide some menu instead of setEnable()
                    item.setVisible(enable);
                    if (item.getItemId() == R.id.menu_call_with_note) {
                        item.setVisible(CallUtil.isCallWithSubjectSupported(getContext()));
                    }
                }
                /** M: [IP Dial] Check whether to show button @{ */
                menu.findItem(R.id.menu_ip_dial).setVisible(
                        DialerFeatureOptions.IP_PREFIX && enable
                        && !PhoneNumberHelper.isUriNumber(mDigits.getText().toString()));
                /** @} */
                /** M: [VoLTE ConfCall] Show conference call menu for volte. @{ */
                boolean visible = mVolteConfCallEnabled;
                menu.findItem(R.id.menu_volte_conf_call).setVisible(visible);
                /** @} */
                //*/ freeme.zhaozehong, 28/07/17. show video call
                menu.findItem(R.id.menu_video_call).setVisible(!isDigitsEmpty()
                        && CallUtil.isVideoEnabled(getContext()));
                //*/

                super.show();
            }
        };
        popupMenu.inflate(R.menu.dialpad_options);
        popupMenu.setOnMenuItemClickListener(this);
        return popupMenu;
    }

    @Override
    public void onClick(View view) {
        /** M: Prevent the event if dialpad is not shown. @{ */
        if (getActivity() != null
                && !((DialtactsActivity)getActivity()).isDialpadShown()) {
            Log.d(TAG, "onClick but dialpad is not shown, skip !!!");
            return;
        }
        /** @} */
        int resId = view.getId();
        //*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2
        switch (resId) {
            case R.id.dialsim1Button:
                simSlot = 0;
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                handleDialButtonPressed();
                break;
            case R.id.dialsim2Button:
                simSlot = 1;
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                handleDialButtonPressed();
                break;
            case R.id.dialpad_button:
                if(mAnimationing)
                    return;
                if (isVisible()) {
                    ((DialtactsActivity) getActivity()).hideDialpadFragment(true, false);
                } else {
                    ((DialtactsActivity) getActivity()).showDialpad();
                }
                break;
            case R.id.delete_button:
                keyPressed(KeyEvent.KEYCODE_DEL);
                break;
            case R.id.digits:
                if (!isDigitsEmpty()) {
                    mDigits.setCursorVisible(true);
                }
            case R.id.dialpad_overflow:
                mOverflowPopupMenu.show();
                break;
            default:
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
        }
        /*/
        if (resId == R.id.dialpad_floating_action_button) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            handleDialButtonPressed();
        } else if (resId == R.id.deleteButton) {
            keyPressed(KeyEvent.KEYCODE_DEL);
        } else if (resId == R.id.digits) {
            if (!isDigitsEmpty()) {
                mDigits.setCursorVisible(true);
            }
        } else if (resId == R.id.dialpad_overflow) {
            /// M: for plug-in @{
            ExtensionManager.getInstance().getDialPadExtension().constructPopupMenu(
                    mOverflowPopupMenu, mOverflowMenuButton, mOverflowPopupMenu.getMenu());
            /// @}
            mOverflowPopupMenu.show();
        } else {
            Log.wtf(TAG, "Unexpected onClick() event from: " + view);
            return;
        }
        //*/
    }

    @Override
    public boolean onLongClick(View view) {
        final Editable digits = mDigits.getText();
        final int id = view.getId();
        if (id == R.id.deleteButton) {
            digits.clear();
            return true;
        } else if (id == R.id.one) {
            if (isDigitsEmpty() || TextUtils.equals(mDigits.getText(), "1")) {
                // We'll try to initiate voicemail and thus we want to remove irrelevant string.
                removePreviousDigitIfPossible('1');

                List<PhoneAccountHandle> subscriptionAccountHandles =
                        PhoneAccountUtils.getSubscriptionPhoneAccounts(getActivity());
                boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                        TelecomUtil.getDefaultOutgoingPhoneAccount(getActivity(),
                                PhoneAccount.SCHEME_VOICEMAIL));
                boolean needsAccountDisambiguation = subscriptionAccountHandles.size() > 1
                        && !hasUserSelectedDefault;

                if (needsAccountDisambiguation || isVoicemailAvailable()) {
                    // On a multi-SIM phone, if the user has not selected a default
                    // subscription, initiate a call to voicemail so they can select an account
                    // from the "Call with" dialog.
                    callVoicemail();
                } else if (getActivity() != null) {
                    // Voicemail is unavailable maybe because Airplane mode is turned on.
                    // Check the current status and show the most appropriate error message.
                    final boolean isAirplaneModeOn =
                            Settings.System.getInt(getActivity().getContentResolver(),
                                    Settings.System.AIRPLANE_MODE_ON, 0) != 0;
                    if (isAirplaneModeOn) {
                        DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                                R.string.dialog_voicemail_airplane_mode_message);
                        dialogFragment.show(getFragmentManager(),
                                "voicemail_request_during_airplane_mode");
                    } else {
                        DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                                R.string.dialog_voicemail_not_ready_message);
                        dialogFragment.show(getFragmentManager(), "voicemail_not_ready");
                    }
                }
                return true;
            }
            return false;
        } else if (id == R.id.zero) {
            if (mPressedDialpadKeys.contains(view)) {
                // If the zero key is currently pressed, then the long press occurred by touch
                // (and not via other means like certain accessibility input methods).
                // Remove the '0' that was input when the key was first pressed.
                removePreviousDigitIfPossible('0');
            }
            keyPressed(KeyEvent.KEYCODE_PLUS);
            stopTone();
            mPressedDialpadKeys.remove(view);
            return true;
        } else if (id == R.id.digits) {
            mDigits.setCursorVisible(true);
            return false;
        }
        //*/ freeme.zhaozehong, 05/07/17. for freemeOS
        switch (id) {
            case R.id.delete_button:
                digits.clear();
                return true;
            case R.id.star:
                if (mDigits.getText().length() != 0) {
                    String p = ",";
                    updateDialString(p.charAt(0));
                    playTone(ToneGenerator.TONE_DTMF_S);
                    return true;
                }
                return true;
        }
        //*/
        return false;
    }

    /**
     * Remove the digit just before the current position of the cursor, iff the following conditions
     *  are true:
     * 1) The cursor is not positioned at index 0.
     * 2) The digit before the current cursor position matches the current digit.
     *
     * @param digit to remove from the digits view.
     */
    private void removePreviousDigitIfPossible(char digit) {
        final int currentPosition = mDigits.getSelectionStart();
        if (currentPosition > 0 && digit == mDigits.getText().charAt(currentPosition - 1)) {
            mDigits.setSelection(currentPosition);
            mDigits.getText().delete(currentPosition - 1, currentPosition);
        }
    }

    public void callVoicemail() {
        DialerUtils.startActivityWithErrorToast(getActivity(),
                new CallIntentBuilder(CallUtil.getVoicemailUri())
                        .setCallInitiationType(LogState.INITIATION_DIALPAD)
                        .build());
        hideAndClearDialpad(false);
    }

    private void hideAndClearDialpad(boolean animate) {
        /// M: Avoid type Incompatible and NPE error @{
        if (getActivity() instanceof DialtactsActivity) {
            ((DialtactsActivity) getActivity()).hideDialpadFragment(animate, true);
        }
        /// @}
    }

    public static class ErrorDialogFragment extends DialogFragment {
        private int mTitleResId;
        private int mMessageResId;

        private static final String ARG_TITLE_RES_ID = "argTitleResId";
        private static final String ARG_MESSAGE_RES_ID = "argMessageResId";

        public static ErrorDialogFragment newInstance(int messageResId) {
            return newInstance(0, messageResId);
        }

        public static ErrorDialogFragment newInstance(int titleResId, int messageResId) {
            final ErrorDialogFragment fragment = new ErrorDialogFragment();
            final Bundle args = new Bundle();
            args.putInt(ARG_TITLE_RES_ID, titleResId);
            args.putInt(ARG_MESSAGE_RES_ID, messageResId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mTitleResId = getArguments().getInt(ARG_TITLE_RES_ID);
            mMessageResId = getArguments().getInt(ARG_MESSAGE_RES_ID);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (mTitleResId != 0) {
                builder.setTitle(mTitleResId);
            }
            if (mMessageResId != 0) {
                builder.setMessage(mMessageResId);
            }
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dismiss();
                            }
                    });
            return builder.create();
        }
    }

    /**
     * In most cases, when the dial button is pressed, there is a
     * number in digits area. Pack it in the intent, start the
     * outgoing call broadcast as a separate task and finish this
     * activity.
     *
     * When there is no digit and the phone is CDMA and off hook,
     * we're sending a blank flash for CDMA. CDMA networks use Flash
     * messages when special processing needs to be done, mainly for
     * 3-way or call waiting scenarios. Presumably, here we're in a
     * special 3-way scenario where the network needs a blank flash
     * before being able to add the new participant.  (This is not the
     * case with all 3-way calls, just certain CDMA infrastructures.)
     *
     * Otherwise, there is no digit, display the last dialed
     * number. Don't finish since the user may want to edit it. The
     * user needs to press the dial button again, to dial it (general
     * case described above).
     */
    private void handleDialButtonPressed() {
        /// M: [IP Dial] add IP dial
        handleDialButtonPressed(Constants.DIAL_NUMBER_INTENT_NORMAL);
    }

    //*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2, dual sim
    private int simSlot = -1;
    //*/

    private void handleDialButtonPressed(int type) {
        if (isDigitsEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            final String number = mDigits.getText().toString();

            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                clearDialpad();
            } else {
                final Intent intent;
                /** M: [IP Dial] check the type of call @{ */
                if (type != Constants.DIAL_NUMBER_INTENT_NORMAL) {
                    intent = IntentUtil.getCallIntent(IntentUtil.getCallUri(number),
                            LogState.INITIATION_DIALPAD, type);
                } else {
                    //*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2, dual sim
                    int simCount = PhoneAccountUtils.getSubscriptionPhoneAccounts(getActivity()).size();
                    if (simSlot != -1 && simCount == 2) {
                        intent = IntentUtil.getCallIntent(IntentUtil.getCallUri(number),
                                DialerUtils.getPhoneAccountHandleBySlot(getActivity(), simSlot),
                                VideoProfile.STATE_AUDIO_ONLY, LogState.INITIATION_DIALPAD);
                        simSlot = -1;
                    } else
                    //*/
                    intent = new CallIntentBuilder(number).
                            setCallInitiationType(LogState.INITIATION_DIALPAD)
                            .build();
                }
                /** @} */
                DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                hideAndClearDialpad(false);
            }
        }
    }

    public void clearDialpad() {
        if (mDigits != null) {
            mDigits.getText().clear();
        }
    }

    public void handleDialButtonClickWithEmptyDigits() {
        /// M:refactor CDMA phone is in call check
        if (isCdmaInCall()) {
            // TODO: Move this logic into services/Telephony
            //
            // This is really CDMA specific. On GSM is it possible
            // to be off hook and wanted to add a 3rd party using
            // the redial feature.
            startActivity(newFlashIntent());
        } else {
            if (!TextUtils.isEmpty(mLastNumberDialed)) {
                // Recall the last number dialed.
                mDigits.setText(mLastNumberDialed);

                // ...and move the cursor to the end of the digits string,
                // so you'll be able to delete digits using the Delete
                // button (just as if you had typed the number manually.)
                //
                // Note we use mDigits.getText().length() here, not
                // mLastNumberDialed.length(), since the EditText widget now
                // contains a *formatted* version of mLastNumberDialed (due to
                // mTextWatcher) and its length may have changed.
                mDigits.setSelection(mDigits.getText().length());
            } else {
                // There's no "last number dialed" or the
                // background query is still running. There's
                // nothing useful for the Dial button to do in
                // this case.  Note: with a soft dial button, this
                // can never happens since the dial button is
                // disabled under these conditons.
                playTone(ToneGenerator.TONE_PROP_NACK);
            }
        }
    }

    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     */
    private void playTone(int tone) {
        playTone(tone, TONE_LENGTH_MS);
    }

    /**
     * Play the specified tone for the specified milliseconds
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * The tone length can be -1, meaning "keep playing the tone." If the caller does so, it should
     * call stopTone() afterward.
     *
     * @param tone a tone code from {@link ToneGenerator}
     * @param durationMs tone length.
     */
    private void playTone(int tone, int durationMs) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        AudioManager audioManager =
                (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
            || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, durationMs);
        }
    }

    /**
     * Stop the tone if it is played.
     */
    private void stopTone() {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "stopTone: mToneGenerator == null");
                return;
            }
            mToneGenerator.stopTone();
        }
    }

    /**
     * Brings up the "dialpad chooser" UI in place of the usual Dialer
     * elements (the textfield/button and the dialpad underneath).
     *
     * We show this UI if the user brings up the Dialer while a call is
     * already in progress, since there's a good chance we got here
     * accidentally (and the user really wanted the in-call dialpad instead).
     * So in this situation we display an intermediate UI that lets the user
     * explicitly choose between the in-call dialpad ("Use touch tone
     * keypad") and the regular Dialer ("Add call").  (Or, the option "Return
     * to call in progress" just goes back to the in-call UI with no dialpad
     * at all.)
     *
     * @param enabled If true, show the "dialpad chooser" instead
     *                of the regular Dialer UI
     */
    private void showDialpadChooser(boolean enabled) {
        if (getActivity() == null) {
            return;
        }
        // Check if onCreateView() is already called by checking one of View objects.
        if (!isLayoutReady()) {
            return;
        }

        if (enabled) {
            Log.d(TAG, "Showing dialpad chooser!");
            if (mDialpadView != null) {
                mDialpadView.setVisibility(View.GONE);
            }

            /// M: Need to check if floatingActionButton is null. because in CT
            // project, OP09 plugin will modify Dialpad layout and floatingActionButton
            // will be null in that case. @{
            if (null != mFloatingActionButtonController) {
                mFloatingActionButtonController.setVisible(false);
            }
            /// @}

            mDialpadChooser.setVisibility(View.VISIBLE);

            // Instantiate the DialpadChooserAdapter and hook it up to the
            // ListView.  We do this only once.
            if (mDialpadChooserAdapter == null) {
                mDialpadChooserAdapter = new DialpadChooserAdapter(getActivity());
            }
            mDialpadChooser.setAdapter(mDialpadChooserAdapter);
        } else {
            Log.d(TAG, "Displaying normal Dialer UI.");
            if (mDialpadView != null) {
                mDialpadView.setVisibility(View.VISIBLE);
            } else {
                mDigits.setVisibility(View.VISIBLE);
            }

            /**
             * M: If the scaleOut() of FloatingActionButtonController be called
             * at previous, the floating button and container would all be set
             * to GONE. But the setVisible() method only set the floating
             * container to visible. So that the floating button is GONE yet.
             * So, it should call the scaleIn() to make sure all of them be set
             * to visible. @{
             */
            /*
             * mFloatingActionButtonController.setVisible(true);
             */

            /// M: Need to check if floatingActionButton is null. because in CT
            // project, OP09 plugin will modify Dialpad layout and floatingActionButton
            // will be null in that case. @{
            if (null != mFloatingActionButtonController) {
                mFloatingActionButtonController.scaleIn(0);
            }
            /// @}

            /** @} */
            mDialpadChooser.setVisibility(View.GONE);
        }

        /// M: for plug-in @{
        ExtensionManager.getInstance().getDialPadExtension().showDialpadChooser(enabled);
        /// @}
    }

    /**
     * @return true if we're currently showing the "dialpad chooser" UI.
     */
    private boolean isDialpadChooserVisible() {
        return mDialpadChooser.getVisibility() == View.VISIBLE;
    }

    /**
     * Simple list adapter, binding to an icon + text label
     * for each item in the "dialpad chooser" list.
     */
    private static class DialpadChooserAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        // Simple struct for a single "choice" item.
        static class ChoiceItem {
            String text;
            Bitmap icon;
            int id;

            public ChoiceItem(String s, Bitmap b, int i) {
                text = s;
                icon = b;
                id = i;
            }
        }

        // IDs for the possible "choices":
        static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
        static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
        static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;

        private static final int NUM_ITEMS = 3;
        private ChoiceItem mChoiceItems[] = new ChoiceItem[NUM_ITEMS];

        public DialpadChooserAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = LayoutInflater.from(context);

            // Initialize the possible choices.
            // TODO: could this be specified entirely in XML?

            // - "Use touch tone keypad"
            mChoiceItems[0] = new ChoiceItem(
                    context.getString(R.string.dialer_useDtmfDialpad),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_tt_keypad),
                    DIALPAD_CHOICE_USE_DTMF_DIALPAD);

            // - "Return to call in progress"
            mChoiceItems[1] = new ChoiceItem(
                    context.getString(R.string.dialer_returnToInCallScreen),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_current_call),
                    DIALPAD_CHOICE_RETURN_TO_CALL);

            // - "Add call"
            mChoiceItems[2] = new ChoiceItem(
                    context.getString(R.string.dialer_addAnotherCall),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_add_call),
                    DIALPAD_CHOICE_ADD_NEW_CALL);
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        /**
         * Return the ChoiceItem for a given position.
         */
        @Override
        public Object getItem(int position) {
            return mChoiceItems[position];
        }

        /**
         * Return a unique ID for each possible choice.
         */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a view for each row.
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // When convertView is non-null, we can reuse it (there's no need
            // to reinflate it.)
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.dialpad_chooser_list_item, null);
            }

            TextView text = (TextView) convertView.findViewById(R.id.text);
            text.setText(mChoiceItems[position].text);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageBitmap(mChoiceItems[position].icon);

            return convertView;
        }
    }

    /**
     * Handle clicks from the dialpad chooser.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        DialpadChooserAdapter.ChoiceItem item =
                (DialpadChooserAdapter.ChoiceItem) parent.getItemAtPosition(position);
        int itemId = item.id;
        if (itemId == DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD) {// Log.i(TAG, "DIALPAD_CHOICE_USE_DTMF_DIALPAD");
            // Fire off an intent to go back to the in-call UI
            // with the dialpad visible.
            returnToInCallScreen(true);
        } else if (itemId == DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL) {// Log.i(TAG, "DIALPAD_CHOICE_RETURN_TO_CALL");
            // Fire off an intent to go back to the in-call UI
            // (with the dialpad hidden).
            returnToInCallScreen(false);
        } else if (itemId == DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL) {// Log.i(TAG, "DIALPAD_CHOICE_ADD_NEW_CALL");
            // Ok, guess the user really did want to be here (in the
            // regular Dialer) after all.  Bring back the normal Dialer UI.
            showDialpadChooser(false);
        } else {
            Log.w(TAG, "onItemClick: unexpected itemId: " + itemId);
        }
    }

    /**
     * Returns to the in-call UI (where there's presumably a call in
     * progress) in response to the user selecting "use touch tone keypad"
     * or "return to call" from the dialpad chooser.
     */
    private void returnToInCallScreen(boolean showDialpad) {
        TelecomUtil.showInCallScreen(getActivity(), showDialpad);

        // Finally, finish() ourselves so that we don't stay on the
        // activity stack.
        // Note that we do this whether or not the showCallScreenWithDialpad()
        // call above had any effect or not!  (That call is a no-op if the
        // phone is idle, which can happen if the current call ends while
        // the dialpad chooser is up.  In this case we can't show the
        // InCallScreen, and there's no point staying here in the Dialer,
        // so we just take the user back where he came from...)
        getActivity().finish();
    }

    /**
     * @return true if the phone is "in use", meaning that at least one line
     *              is active (ie. off hook or ringing or dialing, or on hold).
     */
    private boolean isPhoneInUse() {
        final Context context = getActivity();
        if (context != null) {
            return TelecomUtil.isInCall(context);
        }
        return false;
    }

    /**
     * @return true if the phone is a CDMA phone type
     */
    private boolean phoneIsCdma() {
        return getTelephonyManager().getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
    }

    //*/ freeme.zhaozehong, 28/07/17. video call
    protected boolean onVideoDialMenuItemSelected() {
        Intent intent = CallUtil.getVideoCallIntent(mDigits.getText().toString(), null);
        DialerUtils.startActivityWithErrorToast(getActivity(), intent);
        return true;
    }
    //*/

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int resId = item.getItemId();
        //*/ freeme.zhaozehong, 28/07/17. for new add menu
        switch (resId) {
            case R.id.menu_video_call:
                return onVideoDialMenuItemSelected();
        }
        //*/
        if (resId == R.id.menu_2s_pause) {
            updateDialString(PAUSE);
            return true;
        } else if (resId == R.id.menu_add_wait) {
            updateDialString(WAIT);
            return true;
        /** M: [IP Dial] click IP dial on popup menu @{ */
        } else if (resId == R.id.menu_ip_dial) {
            return onIpDialMenuItemSelected();
        /** @} */
        /** M: [VoLTE ConfCall] handle conference call menu. @{ */
        } else if (resId == R.id.menu_volte_conf_call) {
            Activity activity = getActivity();
            if (activity != null) {
                DialerVolteUtils.handleMenuVolteConfCall(activity);
            }
            return true;
        /** @} */
        } else if (resId == R.id.menu_call_with_note) {
            CallSubjectDialog.start(getActivity(), mDigits.getText().toString());
            hideAndClearDialpad(false);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Updates the dial string (mDigits) after inserting a Pause character (,)
     * or Wait character (;).
     */
    private void updateDialString(char newDigit) {
        if (newDigit != WAIT && newDigit != PAUSE) {
            throw new IllegalArgumentException(
                    "Not expected for anything other than PAUSE & WAIT");
        }

        int selectionStart;
        int selectionEnd;

        // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
        int anchor = mDigits.getSelectionStart();
        int point = mDigits.getSelectionEnd();

        selectionStart = Math.min(anchor, point);
        selectionEnd = Math.max(anchor, point);

        if (selectionStart == -1) {
            selectionStart = selectionEnd = mDigits.length();
        }

        Editable digits = mDigits.getText();

        if (canAddDigit(digits, selectionStart, selectionEnd, newDigit)) {
            //*/ freeme.zhaozehong, 05/07/17. replace '*'
            if (digits.toString().endsWith("*")) {
                digits = (Editable) digits.subSequence(0, digits.length() - 1);
                digits.replace(selectionStart - 1, selectionEnd - 1, Character.toString(newDigit));
                mDigits.setText(digits);
                mDigits.setSelection(digits.length());
                return;
            }
            //*/
            digits.replace(selectionStart, selectionEnd, Character.toString(newDigit));

            if (selectionStart != selectionEnd) {
              // Unselect: back to a regular cursor, just pass the character inserted.
              mDigits.setSelection(selectionStart + 1);
            }
        }
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDeleteButtonEnabledState() {
        if (getActivity() == null) {
            return;
        }
        final boolean digitsNotEmpty = !isDigitsEmpty();
        mDelete.setEnabled(digitsNotEmpty);
        //*/ freeme.zhaozehong, 29/06/17. set delete button status
        mDeleteButton.setEnabled(digitsNotEmpty);
        //*/
    }

    /**
     * Handle transitions for the menu button depending on the state of the digits edit text.
     * Transition out when going from digits to no digits and transition in when the first digit
     * is pressed.
     * @param transitionIn True if transitioning in, False if transitioning out
     */
    private void updateMenuOverflowButton(boolean transitionIn) {
        /** M: [VoLTE ConfCall] Always show overflow menu button for conf call. @{ */
        if (mVolteConfCallEnabled) {
            return;
        }
        /** @} */
        mOverflowMenuButton = mDialpadView.getOverflowMenuButton();
        if (transitionIn) {
            AnimUtils.fadeIn(mOverflowMenuButton, AnimUtils.DEFAULT_DURATION);
        } else {
            AnimUtils.fadeOut(mOverflowMenuButton, AnimUtils.DEFAULT_DURATION);
        }
    }

    /**
     * Check if voicemail is enabled/accessible.
     *
     * @return true if voicemail is enabled and accessible. Note that this can be false
     * "temporarily" after the app boot.
     */
    private boolean isVoicemailAvailable() {
        try {
            PhoneAccountHandle defaultUserSelectedAccount =
                    TelecomUtil.getDefaultOutgoingPhoneAccount(getActivity(),
                            PhoneAccount.SCHEME_VOICEMAIL);
            if (defaultUserSelectedAccount == null) {
                // In a single-SIM phone, there is no default outgoing phone account selected by
                // the user, so just call TelephonyManager#getVoicemailNumber directly.
                return !TextUtils.isEmpty(getTelephonyManager().getVoiceMailNumber());
            } else {
                return !TextUtils.isEmpty(TelecomUtil.getVoicemailNumber(getActivity(),
                        defaultUserSelectedAccount));
            }
        } catch (SecurityException se) {
            // Possibly no READ_PHONE_STATE privilege.
            Log.w(TAG, "SecurityException is thrown. Maybe privilege isn't sufficient.");
        }
        return false;
    }

    /**
     * Returns true of the newDigit parameter can be added at the current selection
     * point, otherwise returns false.
     * Only prevents input of WAIT and PAUSE digits at an unsupported position.
     * Fails early if start == -1 or start is larger than end.
     */
    @VisibleForTesting
    /* package */ static boolean canAddDigit(CharSequence digits, int start, int end,
                                             char newDigit) {
        if(newDigit != WAIT && newDigit != PAUSE) {
            throw new IllegalArgumentException(
                    "Should not be called for anything other than PAUSE & WAIT");
        }

        // False if no selection, or selection is reversed (end < start)
        if (start == -1 || end < start) {
            return false;
        }

        // unsupported selection-out-of-bounds state
        if (start > digits.length() || end > digits.length()) return false;

        // Special digit cannot be the first digit
        if (start == 0) return false;

        if (newDigit == WAIT) {
            // preceding char is ';' (WAIT)
            if (digits.charAt(start - 1) == WAIT) return false;

            // next char is ';' (WAIT)
            if ((digits.length() > end) && (digits.charAt(end) == WAIT)) return false;
        }

        return true;
    }

    /**
     * @return true if the widget with the phone number digits is empty.
     */
    /*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2
    private boolean isDigitsEmpty() {
        return mDigits.length() == 0;
    }
    /*/
    public boolean isDigitsEmpty() {
        return mDigits.length() == 0;
    }
    //*/

    /**
     * Starts the asyn query to get the last dialed/outgoing
     * number. When the background query finishes, mLastNumberDialed
     * is set to the last dialed number or an empty string if none
     * exists yet.
     */
    private void queryLastOutgoingCall() {
        mLastNumberDialed = EMPTY_NUMBER;
        if (!PermissionsUtil.hasPhonePermissions(getActivity())) {
            return;
        }
        CallLogAsync.GetLastOutgoingCallArgs lastCallArgs =
                new CallLogAsync.GetLastOutgoingCallArgs(
                    getActivity(),
                    new CallLogAsync.OnLastOutgoingCallComplete() {
                        @Override
                        public void lastOutgoingCall(String number) {
                            // TODO: Filter out emergency numbers if
                            // the carrier does not want redial for
                            // these.
                            // If the fragment has already been detached since the last time
                            // we called queryLastOutgoingCall in onResume there is no point
                            // doing anything here.
                            if (getActivity() == null) return;
                            mLastNumberDialed = number;
                            updateDeleteButtonEnabledState();
                        }
                    });
        mCallLog.getLastOutgoingCall(lastCallArgs);
    }

    private Intent newFlashIntent() {
        final Intent intent = new CallIntentBuilder(EMPTY_NUMBER).build();
        intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
        return intent;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        final DialtactsActivity activity = (DialtactsActivity) getActivity();
        final DialpadView dialpadView = (DialpadView) getView().findViewById(R.id.dialpad_view);
        if (activity == null) return;
        if (!hidden && !isDialpadChooserVisible()) {
            if (mAnimate) {
                dialpadView.animateShow();
            }

            /// M: [VoLTE ConfCall] initialize value about conference call capability. @{
            mVolteConfCallEnabled = supportOneKeyConference(getActivity());
            Log.d(TAG, "onHiddenChanged false mVolteConfCallEnabled = " + mVolteConfCallEnabled);
            // Always show overflow menu button for conf call, otherwise hide it.
            if (mOverflowMenuButton != null) {
                if (mVolteConfCallEnabled) {
                    mOverflowMenuButton.setVisibility(View.VISIBLE);
                    mOverflowMenuButton.setAlpha(1);
                } else if (isDigitsEmpty()) {
                    mOverflowMenuButton.setVisibility(View.INVISIBLE);
                }
            }
            /// @}

            /// M: Need to check if floatingActionButton is null. because in CT
            // project, OP09 plugin will modify Dialpad layout and floatingActionButton
            // will be null in that case. @{
            if (null != mFloatingActionButtonController) {
                mFloatingActionButtonController.setVisible(false);
                mFloatingActionButtonController.scaleIn(mAnimate ? mDialpadSlideInDuration : 0);
            }
            /// @}

            /// M: for Plug-in @{
            ExtensionManager.getInstance().
                    getDialPadExtension().onHiddenChanged(
                            true, mAnimate ? mDialpadSlideInDuration : 0);
            /// @}
            activity.onDialpadShown();
            mDigits.requestFocus();
        }

        /// M: Need to check if floatingActionButton is null. because in CT
        // project, OP09 plugin will modify Dialpad layout and floatingActionButton
        // will be null in that case. @{
        if (hidden && null != mFloatingActionButtonController) {
            if (mAnimate) {
                mFloatingActionButtonController.scaleOut();
            } else {
                mFloatingActionButtonController.setVisible(false);
            }
        }
        /// @}

        /// M: for Plug-in @{
        if (hidden && mAnimate) {
            ExtensionManager.getInstance().
                    getDialPadExtension().onHiddenChanged(false, 0);
        }
        /// @}
    }

    public void setAnimate(boolean value) {
        mAnimate = value;
    }

    public boolean getAnimate() {
        return mAnimate;
    }

    public void setYFraction(float yFraction) {
        ((DialpadSlidingRelativeLayout) getView()).setYFraction(yFraction);
    }

    public int getDialpadHeight() {
        if (mDialpadView == null) {
            return 0;
        }
        return mDialpadView.getHeight();
    }

    public void process_quote_emergency_unquote(String query) {
        if (PseudoEmergencyAnimator.PSEUDO_EMERGENCY_NUMBER.equals(query)) {
            if (mPseudoEmergencyAnimator == null) {
                mPseudoEmergencyAnimator = new PseudoEmergencyAnimator(
                        new PseudoEmergencyAnimator.ViewProvider() {
                            @Override
                            public View getView() {
                                return DialpadFragment.this.getView();
                            }
                        });
            }
            mPseudoEmergencyAnimator.start();
        } else {
            if (mPseudoEmergencyAnimator != null) {
                mPseudoEmergencyAnimator.end();
            }
        }
    }

    /** M: [IP Dial] add IP dial @{ */
    protected boolean onIpDialMenuItemSelected() {
        handleDialButtonPressed(Constants.DIAL_NUMBER_INTENT_IP);
        return true;
    }
    /** @} */

    /**
     * M: add for plug-in.
     */
    @Override
    public void doCallOptionHandle(Intent intent) {
        DialerUtils.startActivityWithErrorToast(getActivity(), intent);
        hideAndClearDialpad(false);
    }

   /**
    * Shows WFC related notification on status bar when open DialpadFragment
    *
    */
    public void showWfcNotification() {
        Log.i(TAG, "[WFC]showWfcNotification ");
        String wfcText = null;
        String wfcTextSummary = null;
        int wfcIcon = 0;
        final int TIMER_COUNT = 2;
        PhoneAccountHandle defaultAccountHandle =
            TelecomUtil.getDefaultOutgoingPhoneAccount(getActivity(), SCHEME_TEL);
        boolean isWfcEnabled = ( (TelephonyManager)mContext
                .getSystemService(Context.TELEPHONY_SERVICE)).isWifiCallingAvailable();
        if (isWfcEnabled) {
            wfcText = mContext.getResources().getString(R.string.calls_over_wifi);
            wfcIcon = com.mediatek.internal.R.drawable.wfc_notify_registration_success;
            wfcTextSummary = mContext.getResources()
                    .getString(R.string.wfc_notification_summary);
        } else if (isSimPresent(mContext) && !isRatPresent(mContext)) {
            Log.i(TAG, "[WFC]!isRatPresent(mContext) ");
            wfcText = mContext.getResources().getString(R.string.connect_to_wifi);
            wfcIcon = com.mediatek.internal.R.drawable.wfc_notify_registration_error;
            wfcTextSummary = mContext.getResources()
                    .getString(R.string.wfc_notification_summary_fail);
        }
        if (wfcText != null) {
            Log.i(TAG, "[WFC]wfc_text " + wfcText);
            mNotificationTimer = new Timer();
            mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mNotificationCount ++;
                    Log.i(TAG, "[WFC]count:" + mNotificationCount);
                    if (mNotificationCount == TIMER_COUNT) {
                        Log.i(TAG, "[WFC]Canceling notification on time expire mNotiCount"
                                + mNotificationCount);
                        stopWfcNotification();
                    }
                 }
             }, 100, 100);
            Notification noti = new Notification.Builder(mContext)
                    .setContentTitle(wfcText)
                    .setContentText(mContext.getResources()
                            .getString(R.string.wfc_notification_summary))
                    .setSmallIcon(wfcIcon)
                    .setTicker(wfcText)
                    .setOngoing(true)
                    .build();
            Log.i(TAG, "[WFC]Showing WFC notification");
            mNotificationManager.notify(DIALPAD_WFC_NOTIFICATION_ID, noti);
        } else {
            return;
        }
    }


   /**
    * Removes the notification from status bar shown for WFC
    *
    */
    public void stopWfcNotification() {
        Log.i(TAG, "[WFC]canceling notification on stopNotification");
        if (mNotificationTimer != null) {
            mNotificationTimer.cancel();
        };
        mNotificationCount = 0;
        if (mNotificationManager != null) {
            mNotificationManager.cancel(DIALPAD_WFC_NOTIFICATION_ID);
        }
    }

   /**
    * Checks whether SIM is present or not
    *
    * @param context
    */
    private boolean isSimPresent(Context context) {
        boolean ret = false;
        int[] subs =
                SubscriptionManager.from(context).getActiveSubscriptionIdList();
        if (subs.length == 0) {
            ret =  false;
        } else {
             ret = true;
        }
        Log.i(TAG, "[WFC]isSimPresent ret " + ret);
        return ret;
    }

   /**
    * Checks whether any of RAT present: 2G/3G/LTE/Wi-Fi
    *
    *@param context
    */
    private boolean isRatPresent(Context context) {
        Log.i(TAG, "[WFC]isRatPresent");
        int cellularState = ServiceState.STATE_IN_SERVICE;
        ITelephonyEx telephonyEx = ITelephonyEx.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
        Bundle bundle = null;
        try {
            bundle = telephonyEx
                    .getServiceState(SubscriptionManager.getDefaultVoiceSubscriptionId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (bundle != null) {
            cellularState = ServiceState.newFromBundle(bundle).getState();
        }
        Log.i(TAG, "[wfc]cellularState:" + cellularState);
        WifiManager wifiManager =
                (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi =
                cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        Log.i(TAG, "[wfc]wifi state:" + wifiManager.getWifiState());
        Log.i(TAG, "[wfc]wifi connected:" + wifi.isConnected());
        if ((wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED
                || (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED
                && !wifi.isConnected())) && cellularState != ServiceState.STATE_IN_SERVICE) {
            Log.i(TAG, "[wfc]No RAT present");
            return false;
        } else {
            Log.i(TAG, "[wfc]RAT present");
            return true;
        }
    }
    ///@}

    /** M: [ALPS01858019] add listener observer CallLog changes. @{ */
    private ContentObserver mCallLogObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            if (DialpadFragment.this.isAdded()) {
                Log.d(TAG, "Observered the CallLog changes. queryLastOutgoingCall");
                queryLastOutgoingCall();
            }
        };
    };
    /** @} */

    /** M: add for check CDMA phone is in call or not. @{ */
    private boolean isCdmaInCall() {
        for (int subId : SubscriptionManager.from(mContext).getActiveSubscriptionIdList()) {
            if ((TelephonyManager.from(mContext).getCallState(subId)
                    != TelephonyManager.CALL_STATE_IDLE)
                    && (TelephonyManager.from(mContext).getCurrentPhoneType(subId)
                    == TelephonyManager.PHONE_TYPE_CDMA)) {
                Log.d(TAG, "Cdma In Call");
                return true;
            }
        }
        return false;
    }
    /** @} */

    /**
     * M: Checking whether the volte conference is supported or not.
     * @param context
     * @return ture if volte conference is supported
     */
    private boolean supportOneKeyConference(Context context) {
        // We have to requery contacts numbers from provider now.
        // Which requires contacts permissions.
        final boolean hasContactsPermission =
                PermissionsUtil.hasContactsPermissions(context);
        return DialerVolteUtils.isVolteConfCallEnable(context) && hasContactsPermission;
    }

    //*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2
    private ImageButton mDeleteButton;
    private ImageButton mDialsim1Button;
    private ImageButton mDialsim2Button;
    private TelephonyManagerEx mTelephonyManagerEx;

    private void showDialButton() {
        mTelephonyManagerEx = TelephonyManagerEx.getDefault();
        boolean sim1isRead = mTelephonyManagerEx.getSimState(0) == TelephonyManager.SIM_STATE_READY;
        boolean sim2isRead = mTelephonyManagerEx.getSimState(1) == TelephonyManager.SIM_STATE_READY;
        showDialButton(mDialsim1Button, sim1isRead, mDialsim2Button, sim2isRead);
    }

    private void showDialButton(ImageButton mDialButton1, boolean sim1isRead,
                                ImageButton mDialButton2, boolean sim2isRead) {
        if (sim1isRead && sim2isRead) {
            mDialButton1.setVisibility(View.VISIBLE);
            mDialButton2.setVisibility(View.VISIBLE);
            mDialButton1.setImageResource(R.drawable.freeme_call_outgoingcall_icon1_selector);
            mDialButton2.setImageResource(R.drawable.freeme_call_outgoingcall_icon2_selector);
        } else if (sim1isRead && !sim2isRead) {
            mDialButton1.setVisibility(View.VISIBLE);
            mDialButton2.setVisibility(View.GONE);
            mDialButton1.setImageResource(R.drawable.freeme_call_outgoingcall_icon_selector);
        } else if (sim2isRead && !sim1isRead) {
            mDialButton1.setVisibility(View.GONE);
            mDialButton2.setVisibility(View.VISIBLE);
            mDialButton2.setImageResource(R.drawable.freeme_call_outgoingcall_icon_selector);
        } else {
            mDialButton1.setVisibility(View.VISIBLE);
            mDialButton2.setVisibility(View.GONE);
            mDialButton1.setImageResource(R.drawable.freeme_call_outgoingcall_icon_selector);
        }
    }

    private boolean mAnimationing = false;

    public void setAnimationing(boolean mAnimationing) {
        this.mAnimationing = mAnimationing;
    }
    //*/
    //*/ freeme.zhaozehong, 20170801. for freemeOS, show phone number from other activity
    public void fillNumber(String queryNumber) {
        if (mStartedFromNewIntent) {
            mFirstLaunch = true;
            configureScreenFromIntent(getActivity());
            mFirstLaunch = false;
        } else if (!TextUtils.isEmpty(queryNumber)) {
            showNumber(queryNumber);
            if (getParentFragment() instanceof ListsFragment) {
                ((ListsFragment) getParentFragment()).cleanPendingSearchViewQuery();
            }
        }
    }

    private void showNumber(String number) {
        if (mDigits != null) {
            Editable digits = mDigits.getText();
            digits.replace(0, digits.length(), number);
            afterTextChanged(digits);
        }
    }
    //*/
}
