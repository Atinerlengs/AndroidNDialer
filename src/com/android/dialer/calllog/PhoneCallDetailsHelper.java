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

package com.android.dialer.calllog;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.Aas;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.content.ContextCompat;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.dialer.calllog.calllogcache.CallLogCache;
import com.android.dialer.util.DialerUtils;
import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.ICallerInfoExt;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.CallLogHighlighter;
import com.mediatek.dialer.util.DialerFeatureOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
//*/ freeme.zhaozehong, 05/07/17. for freemeOS
import android.text.format.DateFormat;
import com.android.dialer.util.AppCompatConstants;
import com.freeme.contacts.common.utils.FreemeDateTimeUtils;
import java.util.Date;
//*/

/**
 * Helper class to fill in the views in {@link PhoneCallDetailsViews}.
 */
public class PhoneCallDetailsHelper {

    /** The maximum number of icons will be shown to represent the call types in a group. */
    /*/ freeme.zhaozehong, 03/07/17. reset call type icon num
    private static final int MAX_CALL_TYPE_ICONS = 3;
    /*/
    private static final int MAX_CALL_TYPE_ICONS = 1;
    //*/

    private final Context mContext;
    private final Resources mResources;
    /** The injected current time in milliseconds since the epoch. Used only by tests. */
    private Long mCurrentTimeMillisForTest;

    private CharSequence mPhoneTypeLabelForTest;

    private final CallLogCache mCallLogCache;

    /** Calendar used to construct dates */
    private final Calendar mCalendar;

    /**
     * List of items to be concatenated together for accessibility descriptions
     */
    private ArrayList<CharSequence> mDescriptionItems = Lists.newArrayList();

    //*/ freeme.zhaozehong, 05/07/17. for call date
    private java.text.DateFormat mDateFormat;
    //*/

    /**
     * Creates a new instance of the helper.
     * <p>
     * Generally you should have a single instance of this helper in any context.
     *
     * @param resources used to look up strings
     */
    public PhoneCallDetailsHelper(
            Context context,
            Resources resources,
            CallLogCache callLogCache) {
        mContext = context;
        mResources = resources;

        /// M: [Dialer Global Search] for CallLogSearch @{
        if (DialerFeatureOptions.DIALER_GLOBAL_SEARCH) {
            initHighlighter();
        }
        /// @}
        mCallLogCache = callLogCache;
        mCalendar = Calendar.getInstance();

        //*/ freeme.zhaozehong, 05/07/17. for call date
        mDateFormat = DateFormat.getTimeFormat(mContext);
        //*/
    }

    /** Fills the call details views with content. */
    public void setPhoneCallDetails(PhoneCallDetailsViews views, PhoneCallDetails details) {
        // Display up to a given number of icons.
        views.callTypeIcons.clear();
        int count = details.callTypes.length;
        boolean isVoicemail = false;
        for (int index = 0; index < count && index < MAX_CALL_TYPE_ICONS; ++index) {
            views.callTypeIcons.add(details.callTypes[index]);
            if (index == 0) {
                isVoicemail = details.callTypes[index] == Calls.VOICEMAIL_TYPE;
            }
        }

        // Show the video icon if the call had video enabled.
        views.callTypeIcons.setShowVideo(
                (details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO);
        ///M: Plug-in call to show different icons VoLTE, VoWifi, ViWifi in call logs
        ExtensionManager.getInstance().getCallLogExtension().setShowVolteWifi(
                       views.callTypeIcons, details.features);

        views.callTypeIcons.requestLayout();
        views.callTypeIcons.setVisibility(View.VISIBLE);

        // Show the total call count only if there are more than the maximum number of icons.
        final Integer callCount;
        if (count > MAX_CALL_TYPE_ICONS) {
            callCount = count;
        } else {
            callCount = null;
        }

        // Set the call count, location, date and if voicemail, set the duration.
        setDetailText(views, callCount, details);

        // Set the account label if it exists.
        String accountLabel = mCallLogCache.getAccountLabel(details.accountHandle);
        if (!TextUtils.isEmpty(details.viaNumber)) {
            if (!TextUtils.isEmpty(accountLabel)) {
                accountLabel = mResources.getString(R.string.call_log_via_number_phone_account,
                        accountLabel, details.viaNumber);
            } else {
                accountLabel = mResources.getString(R.string.call_log_via_number,
                        details.viaNumber);
            }
        }
        if (!TextUtils.isEmpty(accountLabel)) {
            views.callAccountLabel.setVisibility(View.VISIBLE);
            views.callAccountLabel.setText(accountLabel);
            int color = mCallLogCache.getAccountColor(details.accountHandle);
            if (color == PhoneAccount.NO_HIGHLIGHT_COLOR) {
                int defaultColor = R.color.dialtacts_secondary_text_color;
                views.callAccountLabel.setTextColor(mContext.getResources().getColor(defaultColor));
            } else {
                views.callAccountLabel.setTextColor(color);
            }
        } else {
            views.callAccountLabel.setVisibility(View.GONE);
        }

        CharSequence nameText;
        final CharSequence displayNumber = details.displayNumber;
        if (TextUtils.isEmpty(details.getPreferredName())) {
            nameText = displayNumber;
            // We have a real phone number as "nameView" so make it always LTR
            views.nameView.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            nameText = details.getPreferredName();
        }

        /// M: [Dialer Global Search]for CallLog Search @{
        if (DialerFeatureOptions.DIALER_GLOBAL_SEARCH && mHighlightString != null
                && mHighlightString.length > 0) {
            boolean onlyNumber = TextUtils.isEmpty(details.getPreferredName());
            nameText = getHightlightedCallLogName(nameText.toString(),
                    mHighlightString, onlyNumber);
        }
        /// @}
        views.nameView.setText(nameText);

        if (isVoicemail) {
            views.voicemailTranscriptionView.setText(TextUtils.isEmpty(details.transcription) ? null
                    : details.transcription);
        }

        // Bold if not read
        Typeface typeface = details.isRead ? Typeface.SANS_SERIF : Typeface.DEFAULT_BOLD;
        views.nameView.setTypeface(typeface);
        //*/ freeme.zhaozehong, 05/07/17. set name color
        views.nameView.setTextColor(ContextCompat.getColor(mContext,
                details.callTypes[0] == AppCompatConstants.CALLS_MISSED_TYPE
                        ? R.color.freeme_calllog_missed_title_color
                        : R.color.freeme_calllog_title_color));
        //*/
        views.voicemailTranscriptionView.setTypeface(typeface);
        /*/ freeme.zhaozehong, 03/07/17. not need
        views.callLocationAndDate.setTypeface(typeface);
        views.callLocationAndDate.setTextColor(ContextCompat.getColor(mContext, details.isRead ?
                R.color.call_log_detail_color : R.color.call_log_unread_text_color));
        //*/
    }

    /**
     * Builds a string containing the call location and date. For voicemail logs only the call date
     * is returned because location information is displayed in the call action button
     *
     * @param details The call details.
     * @return The call location and date string.
     */
    private CharSequence getCallLocationAndDate(PhoneCallDetails details) {
        mDescriptionItems.clear();

        if (details.callTypes[0] != Calls.VOICEMAIL_TYPE) {
            // Get type of call (ie mobile, home, etc) if known, or the caller's location.
            CharSequence callTypeOrLocation = getCallTypeOrLocation(details);

            // Only add the call type or location if its not empty.  It will be empty for unknown
            // callers.
            if (!TextUtils.isEmpty(callTypeOrLocation)) {
                mDescriptionItems.add(callTypeOrLocation);
            }
        }

        // The date of this call
        /*/ freeme.zhaozehong, 05/07/17. not need
        mDescriptionItems.add(getCallDate(details));
        //*/

        // Create a comma separated list from the call type or location, and call date.
        return DialerUtils.join(mResources, mDescriptionItems);
    }

    /**
     * For a call, if there is an associated contact for the caller, return the known call type
     * (e.g. mobile, home, work).  If there is no associated contact, attempt to use the caller's
     * location if known.
     *
     * @param details Call details to use.
     * @return Type of call (mobile/home) if known, or the location of the caller (if known).
     */
    public CharSequence getCallTypeOrLocation(PhoneCallDetails details) {
        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(details.number)
                && !PhoneNumberHelper.isUriNumber(details.number.toString())
                && !mCallLogCache.isVoicemailNumber(details.accountHandle, details.number)) {

            //*/ freeme.zhaozehong, 20170913. set unknown if geocode is null
            String geo = details.geocode;
            if (TextUtils.isEmpty(geo)) {
                geo = mResources.getString(R.string.freeme_geo_unknown_city);
                numberFormattedLabel = geo;
            }
            //*/

            if (TextUtils.isEmpty(details.namePrimary) && !TextUtils.isEmpty(details.geocode)) {
                numberFormattedLabel = details.geocode;
            } else if (!(details.numberType == Phone.TYPE_CUSTOM
                    && TextUtils.isEmpty(details.numberLabel))) {
                /*/ freeme.zhaozehong, 05/07/17. reset text
                /// M: add permission check for AAS @{
                if (details.numberType == Aas.PHONE_TYPE_AAS
                        && !TextUtils.isEmpty(details.numberLabel)
                        && !PermissionsUtil.hasContactsPermissions(mContext)) {
                    numberFormattedLabel = "";
                } else {
                /// @}
                // Get type label only if it will not be "Custom" because of an empty number label.
                /// M: Using new API for AAS phone number label lookup
                numberFormattedLabel = MoreObjects.firstNonNull(mPhoneTypeLabelForTest,
                        Phone.getTypeLabel(
                                mContext, details.numberType, details.numberLabel));
                }
                /*/
                numberFormattedLabel = details.number + " " + geo;
                //*/
            }
        }

        if (!TextUtils.isEmpty(details.namePrimary) && TextUtils.isEmpty(numberFormattedLabel)) {
            numberFormattedLabel = details.displayNumber;
        }
        return numberFormattedLabel;
    }

    @NeededForTesting
    public void setPhoneTypeLabelForTest(CharSequence phoneTypeLabel) {
        this.mPhoneTypeLabelForTest = phoneTypeLabel;
    }

    /**
     * Get the call date/time of the call. For the call log this is relative to the current time.
     * e.g. 3 minutes ago. For voicemail, see {@link #getGranularDateTime(PhoneCallDetails)}
     *
     * @param details Call details to use.
     * @return String representing when the call occurred.
     */
    public CharSequence getCallDate(PhoneCallDetails details) {
        if (details.callTypes[0] == Calls.VOICEMAIL_TYPE) {
            return getGranularDateTime(details);
        }

        return DateUtils.getRelativeTimeSpanString(details.date, getCurrentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    /**
     * Get the granular version of the call date/time of the call. The result is always in the form
     * 'DATE at TIME'. The date value changes based on when the call was created.
     *
     * If created today, DATE is 'Today'
     * If created this year, DATE is 'MMM dd'
     * Otherwise, DATE is 'MMM dd, yyyy'
     *
     * TIME is the localized time format, e.g. 'hh:mm a' or 'HH:mm'
     *
     * @param details Call details to use
     * @return String representing when the call occurred
     */
    public CharSequence getGranularDateTime(PhoneCallDetails details) {
        return mResources.getString(R.string.voicemailCallLogDateTimeFormat,
                getGranularDate(details.date),
                DateUtils.formatDateTime(mContext, details.date, DateUtils.FORMAT_SHOW_TIME));
    }

    /**
     * Get the granular version of the call date. See {@link #getGranularDateTime(PhoneCallDetails)}
     */
    private String getGranularDate(long date) {
        if (DateUtils.isToday(date)) {
            return mResources.getString(R.string.voicemailCallLogToday);
        }
        return DateUtils.formatDateTime(mContext, date, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_ABBREV_MONTH
                | (shouldShowYear(date) ? DateUtils.FORMAT_SHOW_YEAR : DateUtils.FORMAT_NO_YEAR));
    }

    /**
     * Determines whether the year should be shown for the given date
     *
     * @return {@code true} if date is within the current year, {@code false} otherwise
     */
    private boolean shouldShowYear(long date) {
        mCalendar.setTimeInMillis(getCurrentTimeMillis());
        int currentYear = mCalendar.get(Calendar.YEAR);
        mCalendar.setTimeInMillis(date);
        return currentYear != mCalendar.get(Calendar.YEAR);
    }

    /** Sets the text of the header view for the details page of a phone call. */
    @NeededForTesting
    public void setCallDetailsHeader(TextView nameView, PhoneCallDetails details) {
        final CharSequence nameText;
        if (!TextUtils.isEmpty(details.namePrimary)) {
            nameText = details.namePrimary;
        } else if (!TextUtils.isEmpty(details.displayNumber)) {
            nameText = details.displayNumber;
        } else {
            nameText = mResources.getString(R.string.unknown);
        }

        nameView.setText(nameText);
    }

    @NeededForTesting
    public void setCurrentTimeForTest(long currentTimeMillis) {
        mCurrentTimeMillisForTest = currentTimeMillis;
    }

    /**
     * Returns the current time in milliseconds since the epoch.
     * <p>
     * It can be injected in tests using {@link #setCurrentTimeForTest(long)}.
     */
    private long getCurrentTimeMillis() {
        if (mCurrentTimeMillisForTest == null) {
            return System.currentTimeMillis();
        } else {
            return mCurrentTimeMillisForTest;
        }
    }

    /** Sets the call count, date, and if it is a voicemail, sets the duration. */
    private void setDetailText(PhoneCallDetailsViews views, Integer callCount,
                               PhoneCallDetails details) {
        //*/ freeme.zhaozehong, 03/07/17. show detail info
        views.callCount.setText(callCount != null ? "(" + callCount + ")" : "");
        CharSequence dateText = mDateFormat.format(new Date(details.date));
        String dateText_notoday = FreemeDateTimeUtils.getFormatedDateText(mContext, details.date,
                FreemeDateTimeUtils.DATE_FORMAT_MM_DD);
        if(dateText_notoday.equals(mContext.getResources().getString(R.string.calllog_today))){
            views.callDate.setText(dateText);
        } else {
            views.callDate.setText(dateText_notoday);
        }
        //*/
        // Combine the count (if present) and the date.
        /*/ freeme.zhaozehong, 05/07/17. reset text
        CharSequence dateText = getCallLocationAndDate(details);
        final CharSequence text;
        if (callCount != null) {
            text = mResources.getString(
                    R.string.call_log_item_count_and_date, callCount.intValue(), dateText);
        } else {
            text = dateText;
        }
        /*/
        final CharSequence text = getCallLocationAndDate(details);
        //*/

        if (details.callTypes[0] == Calls.VOICEMAIL_TYPE && details.duration > 0) {
            views.callLocationAndDate.setText(mResources.getString(
                    R.string.voicemailCallLogDateTimeFormatWithDuration, text,
                    getVoicemailDuration(details)));
        } else {
            views.callLocationAndDate.setText(text);
        }

    }

    private String getVoicemailDuration(PhoneCallDetails details) {
        long minutes = TimeUnit.SECONDS.toMinutes(details.duration);
        long seconds = details.duration - TimeUnit.MINUTES.toSeconds(minutes);
        if (minutes > 99) {
            minutes = 99;
        }
        return mResources.getString(R.string.voicemailDurationFormat, minutes, seconds);
    }

    /// M: [Dialer Global Search] for CallLog search @{
    private CallLogHighlighter mHighlighter;
    private char[] mHighlightString;

    private void initHighlighter() {
        mHighlighter = new CallLogHighlighter(Color.GREEN);
    }

    public void setHighlightedText(char[] highlightedText) {
        mHighlightString = highlightedText;
    }

    private String getHightlightedCallLogName(String text, char[] highlightText,
            boolean isOnlyNumber) {
        String name = text;
        if (isOnlyNumber) {
            name = mHighlighter.applyNumber(text, highlightText).toString();
        } else {
            name = mHighlighter.applyName(text, highlightText).toString();
        }
        return name;
    }
    /// @}
}
