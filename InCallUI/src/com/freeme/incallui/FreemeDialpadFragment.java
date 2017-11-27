package com.freeme.incallui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.dialer.R;
import com.android.incallui.BaseFragment;
import com.android.incallui.DialpadPresenter;
import com.android.incallui.Log;

import java.util.HashMap;

/**
 * Created by zhaozehong on 26/07/17.
 */

public class FreemeDialpadFragment extends BaseFragment<DialpadPresenter, DialpadPresenter.DialpadUi>
        implements DialpadPresenter.DialpadUi, View.OnTouchListener, View.OnKeyListener,
        View.OnHoverListener, View.OnClickListener {
    private static final int ACCESSIBILITY_DTMF_STOP_DELAY_MILLIS = 50;

    private final int[] mButtonIds = new int[] {R.id.zero, R.id.one, R.id.two, R.id.three,
            R.id.four, R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.star,
            R.id.pound};

    private EditText mDtmfDialerField;
    public ImageButton mDeleteButton;

    /** Hash Map to map a view id to a character*/
    private static final HashMap<Integer, Character> mDisplayMap = new HashMap<>();

    private static final Handler sHandler = new Handler(Looper.getMainLooper());


    /** Set up the static maps*/
    static {
        // Map the buttons to the display characters
        mDisplayMap.put(R.id.one, '1');
        mDisplayMap.put(R.id.two, '2');
        mDisplayMap.put(R.id.three, '3');
        mDisplayMap.put(R.id.four, '4');
        mDisplayMap.put(R.id.five, '5');
        mDisplayMap.put(R.id.six, '6');
        mDisplayMap.put(R.id.seven, '7');
        mDisplayMap.put(R.id.eight, '8');
        mDisplayMap.put(R.id.nine, '9');
        mDisplayMap.put(R.id.zero, '0');
        mDisplayMap.put(R.id.pound, '#');
        mDisplayMap.put(R.id.star, '*');
    }

    @Override
    public void onClick(View v) {
        final AccessibilityManager accessibilityManager = (AccessibilityManager)
                v.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        // When accessibility is on, simulate press and release to preserve the
        // semantic meaning of performClick(). Required for Braille support.
        if (accessibilityManager.isEnabled()) {
            final int id = v.getId();
            // Checking the press state prevents double activation.
            if (!v.isPressed() && mDisplayMap.containsKey(id)) {
                getPresenter().processDtmf(mDisplayMap.get(id));
                sHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getPresenter().stopDtmf();
                    }
                }, ACCESSIBILITY_DTMF_STOP_DELAY_MILLIS);
            }
        }
        if (v.getId() == R.id.dialpad_back) {
            getActivity().onBackPressed();
        }
        switch (v.getId()) {
            case R.id.deleteButton:
                int keyCode = KeyEvent.KEYCODE_DEL;
                KeyEvent keyEventDown = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
                mDtmfDialerField.onKeyDown(keyCode, keyEventDown);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onHover(View v, MotionEvent event) {
        // When touch exploration is turned on, lifting a finger while inside
        // the button's hover target bounds should perform a click action.
        final AccessibilityManager accessibilityManager = (AccessibilityManager)
                v.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);

        if (accessibilityManager.isEnabled()
                && accessibilityManager.isTouchExplorationEnabled()) {
            final int left = v.getPaddingLeft();
            final int right = (v.getWidth() - v.getPaddingRight());
            final int top = v.getPaddingTop();
            final int bottom = (v.getHeight() - v.getPaddingBottom());

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    // Lift-to-type temporarily disables double-tap activation.
                    v.setClickable(false);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    final int x = (int) event.getX();
                    final int y = (int) event.getY();
                    if ((x > left) && (x < right) && (y > top) && (y < bottom)) {
                        v.performClick();
                    }
                    v.setClickable(true);
                    break;
            }
        }

        return false;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        Log.d(this, "onKey:  keyCode " + keyCode + ", view " + v);

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            int viewId = v.getId();
            if (mDisplayMap.containsKey(viewId)) {
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (event.getRepeatCount() == 0) {
                            getPresenter().processDtmf(mDisplayMap.get(viewId));
                        }
                        break;
                    case KeyEvent.ACTION_UP:
                        getPresenter().stopDtmf();
                        break;
                }
                // do not return true [handled] here, since we want the
                // press / click animation to be handled by the framework.
            }
        }
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.d(this, "onTouch");
        int viewId = v.getId();

        // if the button is recognized
        if (mDisplayMap.containsKey(viewId)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Append the character mapped to this button, to the display.
                    // start the tone
                    getPresenter().processDtmf(mDisplayMap.get(viewId));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // stop the tone on ANY other event, except for MOVE.
                    getPresenter().stopDtmf();
                    break;
            }
            // do not return true [handled] here, since we want the
            // press / click animation to be handled by the framework.
        }
        return false;
    }

    // TODO(klp) Adds hardware keyboard listener
    @Override
    public DialpadPresenter createPresenter() {
        return new DialpadPresenter();
    }

    @Override
    public DialpadPresenter.DialpadUi getUi() {
        return this;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View parent = inflater.inflate(
                R.layout.freeme_incall_dialpad_fragment, container, false);
        mDtmfDialerField = (EditText) parent.findViewById(R.id.digits);
        if (mDtmfDialerField != null) {
            // remove the long-press context menus that support
            // the edit (copy / paste / select) functions.
            mDtmfDialerField.setLongClickable(false);
            mDtmfDialerField.setElegantTextHeight(false);
            mDtmfDialerField.addTextChangedListener(new NumberTextWatcher());
            configureKeypadListeners(parent);
        }
        mDeleteButton = (ImageButton) parent.findViewById(R.id.deleteButton);
        mDeleteButton.setVisibility(View.VISIBLE);
        mDeleteButton.setOnClickListener(this);

        return parent;
    }

    public void updateColors() {
        // ignore
    }

    /**
     * Getter for Dialpad text.
     *
     * @return String containing current Dialpad EditText text.
     */
    public String getDtmfText() {
        return mDtmfDialerField.getText().toString();
    }

    /**
     * Sets the Dialpad text field with some text.
     *
     * @param text Text to set Dialpad EditText to.
     */
    public void setDtmfText(String text) {
        mDtmfDialerField.setText(PhoneNumberUtilsCompat.createTtsSpannable(text));
        // set the focus to the end of the text
        mDtmfDialerField.setSelection(mDtmfDialerField.length());
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
     * Starts the slide up animation for the Dialpad keys when the Dialpad is revealed.
     */
    public void animateShowDialpad() {
        // ignore
    }

    @Override
    public void appendDigitsToField(char digit) {
        if (mDtmfDialerField != null) {
            // TODO: maybe *don't* manually append this digit if
            // mDialpadDigits is focused and this key came from the HW
            // keyboard, since in that case the EditText field will
            // get the key event directly and automatically appends
            // whetever the user types.
            // (Or, a cleaner fix would be to just make mDialpadDigits
            // *not* handle HW key presses.  That seems to be more
            // complicated than just setting focusable="false" on it,
            // though.)
            mDtmfDialerField.getText().append(digit);
        }
    }

    /**
     * Called externally (from InCallScreen) to play a DTMF Tone.
     */
    public boolean onDialerKeyDown(KeyEvent event) {
        return false;
    }

    /**
     * Called externally (from InCallScreen) to cancel the last DTMF Tone played.
     */
    public boolean onDialerKeyUp(KeyEvent event) {
        return false;
    }

    private void configureKeypadListeners(View parent) {
        for (int i = 0; i < mButtonIds.length; i++) {
            View dialpadKey = parent.findViewById(mButtonIds[i]);
            dialpadKey.setOnTouchListener(this);
            dialpadKey.setOnKeyListener(this);
            dialpadKey.setOnHoverListener(this);
            dialpadKey.setOnClickListener(this);
        }
    }

    private String mWithoutDeleteButtonTextString;
    private String mWithDeleteButtonTextString;
    private class NumberTextWatcher implements TextWatcher{
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mDeleteButton.getVisibility() == View.GONE) {
                mWithoutDeleteButtonTextString = s.toString();
            } else {
                mWithDeleteButtonTextString = s.toString();
            }
        }

        @Override
        public void afterTextChanged(Editable s) {

        }
    }

    public void setupDeleteButton(boolean isDialpadButton) {
        mDeleteButton.setVisibility(isDialpadButton ? View.GONE : View.VISIBLE);
        String hint = isDialpadButton ? null : getString(R.string.onscreenRecordNumberText);
        mDtmfDialerField.setHint(hint);
        String number = isDialpadButton ? mWithoutDeleteButtonTextString : mWithDeleteButtonTextString;
        mDtmfDialerField.setText(number);
        mDtmfDialerField.setSelection(mDtmfDialerField.length());
        if (!isDialpadButton) {
            Toast.makeText(getActivity(), R.string.toast_recordnumber_show, Toast.LENGTH_LONG).show();
        }
    }

    public String getRecordNumber() {
        return mWithDeleteButtonTextString;
    }

    private View dialpad_layout;
    private View dialpad;
    public void updateDialpadSize(boolean isDeviceLandscape){
//        if (getView()==null)
//            return;
//        if (dialpad_layout == null) {
//            dialpad_layout = getView().findViewById(R.id.dialpad_layout);
//        }
//        if (dialpad == null) {
//            dialpad = getView().findViewById(R.id.dialpad);
//        }
//        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
//                dialpad_layout.getLayoutParams();
//        if (isDeviceLandscape) {
//            params.width = getResources().getDimensionPixelSize(
//                    R.dimen.freeme_dialpad_width_in_landscape);
//        } else {
//            params.width = LinearLayout.LayoutParams.MATCH_PARENT;
//        }
//        dialpad_layout.setLayoutParams(params);
//
//        LinearLayout.LayoutParams params2 = (LinearLayout.LayoutParams) dialpad.getLayoutParams();
//        if (isDeviceLandscape) {
//            params.height = getResources().getDimensionPixelSize(
//                    R.dimen.freeme_dialpad_width_in_landscape);
//        } else {
//            params.width = getResources().getDimensionPixelSize(
//                    R.dimen.freeme_dialpad_height_in_portrait);
//        }
//        dialpad.setLayoutParams(params2);
    }
}
