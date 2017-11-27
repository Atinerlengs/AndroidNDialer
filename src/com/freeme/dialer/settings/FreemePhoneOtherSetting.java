package com.freeme.dialer.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.SwitchPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.freeme.provider.FreemeSettings;
import com.mediatek.dialer.util.DialerFeatureOptions;
import com.android.dialer.R;
/**
 * freeme.zhangjunjian,2017-6-20,create for phone other setting
 */

public class FreemePhoneOtherSetting extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener{

    private static final String BUTTON_OTHERS_GRADIENT_RING_KEY = "gradient_ring_key";
    private static final String BUTTON_OTHERS_REVERSE_SILENT_KEY = "reverse_silent_key";
    private static final String BUTTON_PHONE_VIBRATE_KEY = "phone_vibrate_key";
    private static final String BUTTON_OTHERS_POCKET_MODE_KEY = "pocket_mode_ring_key";

    private SwitchPreference mButtonPV;
    private SwitchPreference mButtonMute;//reverse mute
    private SwitchPreference mButtonRingType;//gradient ring
    private SwitchPreference mButtonPocketModeType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.freeme_phone_other_setting);
        mButtonMute = (SwitchPreference) findPreference(BUTTON_OTHERS_REVERSE_SILENT_KEY);
        mButtonRingType = (SwitchPreference) findPreference(BUTTON_OTHERS_GRADIENT_RING_KEY);
        mButtonPocketModeType = (SwitchPreference) findPreference(BUTTON_OTHERS_POCKET_MODE_KEY);
        mButtonPV = (SwitchPreference) findPreference(BUTTON_PHONE_VIBRATE_KEY);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mButtonMute != null) {
            if (Settings.System.getInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_REVERSE_SILENT_SETTING, 0) == 1) {
                mButtonMute.setChecked(true);
            } else {
                mButtonMute.setChecked(false);
            }
            mButtonMute.setOnPreferenceChangeListener(this);
        }
        if (mButtonRingType != null) {
            if (Settings.System.getInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_GRADIENT_RING_KEY, 0) == 0) {
                mButtonRingType.setChecked(false);
            } else {
                mButtonRingType.setChecked(true);
            }
            mButtonRingType.setOnPreferenceChangeListener(this);
        }
        if (mButtonPocketModeType != null) {
            if (Settings.System.getInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_POCKET_MODE_KEY, 0) == 1) {
                mButtonPocketModeType.setChecked(true);
            } else {
                mButtonPocketModeType.setChecked(false);
            }

            mButtonPocketModeType.setOnPreferenceChangeListener(this);
        }
        if (mButtonPV != null) {
            if (Settings.System.getInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_PHONE_VIBRAT_KEY, 0) == 1) {
                mButtonPV.setChecked(true);
            } else {
                mButtonPV.setChecked(false);
            }
            mButtonPV.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mButtonMute) {
            boolean preMuteStatus=Settings.System.getInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_REVERSE_SILENT_SETTING, 0) == 1;
            mButtonMute.setChecked(!preMuteStatus);
            Settings.System.putInt(getActivity().getContentResolver(), FreemeSettings.System.FREEME_REVERSE_SILENT_SETTING, preMuteStatus?0:1);
        } else if (preference == mButtonRingType) {
            boolean preRingStatus=Settings.System.getInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_GRADIENT_RING_KEY, 0) == 0;
            Settings.System.putInt(getActivity().getContentResolver(), FreemeSettings.System.FREEME_GRADIENT_RING_KEY, preRingStatus?1:0);
        } else if (preference == mButtonPocketModeType) {
            boolean preStatus = Settings.System.getInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_POCKET_MODE_KEY, 0) == 1;
            mButtonPocketModeType.setChecked(!preStatus);
            Settings.System.putInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_POCKET_MODE_KEY, preStatus ? 0 : 1);
        } else if (preference == mButtonPV) {
            boolean prePvStatus=Settings.System.getInt(getActivity().getContentResolver(),
                    FreemeSettings.System.FREEME_PHONE_VIBRAT_KEY, 0) == 1;
            Settings.System.putInt(getActivity().getContentResolver(), FreemeSettings.System.FREEME_PHONE_VIBRAT_KEY, prePvStatus?0:1);
        }
        return true;
    }
}