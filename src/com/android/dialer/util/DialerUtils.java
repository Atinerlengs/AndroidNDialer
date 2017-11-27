/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.dialer.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.mediatek.dialer.ext.ExtensionManager;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
//*/ freeme.zhaozehong, 05/06/17. for get phone account
import android.telecom.PhoneAccount;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
//*/
//*/ freeme.zhaozehong, 17-1-6. for get default sim
import android.telecom.PhoneAccountHandle;
//*/

/**
 * General purpose utility methods for the Dialer.
 */
public class DialerUtils {
    /// M: add for logging @{
    private static final boolean DEBUG = DialtactsActivity.DEBUG;
    private static final String TAG = DialerUtils.class.getSimpleName();
    /// @}

    /**
     * Attempts to start an activity and displays a toast with the default error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent to start the activity with.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent) {
       ///M: Plug-in Call to Enable Video call from Call Settings through pop-up if it is disabled
        if (ExtensionManager.getInstance().getDialPadExtension().
                                 checkVideoSetting(context, intent)) {
            return;
        }
        startActivityWithErrorToast(context, intent, R.string.activity_not_available);
    }

    /**
     * Attempts to start an activity and displays a toast with a provided error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent to start the activity with.
     * @param msgId Resource ID of the string to display in an error message if the activity is
     *              not found.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent, int msgId) {
        try {
            if ((IntentUtil.CALL_ACTION.equals(intent.getAction())
                            && context instanceof Activity)) {
                // All dialer-initiated calls should pass the touch point to the InCallUI
                Point touchPoint = TouchPointManager.getInstance().getPoint();
                if (touchPoint.x != 0 || touchPoint.y != 0) {
                    Bundle extras;
                    // Make sure to not accidentally clobber any existing extras
                    if (intent.hasExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)) {
                        extras = intent.getParcelableExtra(
                                TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
                    } else {
                        extras = new Bundle();
                    }
                    extras.putParcelable(TouchPointManager.TOUCH_POINT, touchPoint);
                    intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
                }
                /// M: add log for debugging
                if (DEBUG) {
                    Log.d(TAG, "startActivityWithErrorToast placeCall with intent " + intent + " : "
                            + context);
                }

                final boolean hasCallPermission = TelecomUtil.placeCall((Activity) context, intent);
                if (!hasCallPermission) {
                    // TODO: Make calling activity show request permission dialog and handle
                    // callback results appropriately.
                    Toast.makeText(context, "Cannot place call without Phone permission",
                            Toast.LENGTH_SHORT);
                }
            /// M: avoid NPE error
            } else if (context != null) {
                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Returns the component name to use in order to send an SMS using the default SMS application,
     * or null if none exists.
     */
    public static ComponentName getSmsComponent(Context context) {
        String smsPackage = Telephony.Sms.getDefaultSmsPackage(context);
        if (smsPackage != null) {
            final PackageManager packageManager = context.getPackageManager();
            final Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts(ContactsUtils.SCHEME_SMSTO, "", null));
            final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
            for (ResolveInfo resolveInfo : resolveInfos) {
                if (smsPackage.equals(resolveInfo.activityInfo.packageName)) {
                    return new ComponentName(smsPackage, resolveInfo.activityInfo.name);
                }
            }
        }
        return null;
    }

    /**
     * Closes an {@link AutoCloseable}, silently ignoring any checked exceptions. Does nothing if
     * null.
     *
     * @param closeable to close.
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Joins a list of {@link CharSequence} into a single {@link CharSequence} seperated by a
     * localized delimiter such as ", ".
     *
     * @param resources Resources used to get list delimiter.
     * @param list List of char sequences to join.
     * @return Joined char sequences.
     */
    public static CharSequence join(Resources resources, Iterable<CharSequence> list) {
        StringBuilder sb = new StringBuilder();
        final BidiFormatter formatter = BidiFormatter.getInstance();
        final CharSequence separator = resources.getString(R.string.list_delimeter);

        Iterator<CharSequence> itr = list.iterator();
        boolean firstTime = true;
        while (itr.hasNext()) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(separator);
            }
            // Unicode wrap the elements of the list to respect RTL for individual strings.
            sb.append(formatter.unicodeWrap(
                    itr.next().toString(), TextDirectionHeuristics.FIRSTSTRONG_LTR));
        }

        // Unicode wrap the joined value, to respect locale's RTL ordering for the whole list.
        return formatter.unicodeWrap(sb.toString());
    }

    /**
     * @return True if the application is currently in RTL mode.
     */
    public static boolean isRtl() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
            View.LAYOUT_DIRECTION_RTL;
    }

    public static void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    public static void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * M: Attempts to start an activity for result and displays a toast with the
     * default error message if the activity is not found, instead of throwing
     * an exception.
     *
     * @param activity to start the activity with.
     * @param intent to start the activity with.
     * @param requestCode the request code
     */
    public static void startActivityForResultWithErrorToast(Activity activity,
            Intent intent, int requestCode) {
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.activity_not_available,
                    Toast.LENGTH_SHORT).show();
        }
    }

    //*/ freeme.zhaozehong, 17-1-6. get default phone account
    public static PhoneAccountHandle getDefaultSmartDialAccount(Context context) {
        PhoneAccountHandle defaultPhoneAccountHandle = null;
        final TelecomManager telecomManager = TelecomManager.from(context);
        final List<PhoneAccountHandle> phoneAccountsList = telecomManager.getCallCapablePhoneAccounts();
        if (phoneAccountsList.size() == 1) {
            defaultPhoneAccountHandle = phoneAccountsList.get(0);
        } else if (phoneAccountsList.size() > 1) {
            defaultPhoneAccountHandle = telecomManager.getUserSelectedOutgoingPhoneAccount();
            if (defaultPhoneAccountHandle == null) {
                defaultPhoneAccountHandle = phoneAccountsList.get(0);
            }
        }
        return defaultPhoneAccountHandle;
    }
    //*/

    //*/ freeme.zhaozehong, 05/06/17. get phone account by slot
    public static PhoneAccountHandle getPhoneAccountHandleBySlot(Context c, int slot) {
        TelecomManager telecomManager = TelecomManager.from(c);
        TelephonyManager telephonyManager = TelephonyManager.from(c);
        List<PhoneAccountHandle> mPhoneAccountHandles = telecomManager.getAllPhoneAccountHandles();
        for (PhoneAccountHandle account : mPhoneAccountHandles) {
            PhoneAccount phoneAccount = telecomManager.getPhoneAccount(account);
            int subId = telephonyManager.getSubIdForPhoneAccount(phoneAccount);
            SubscriptionInfo sir = SubscriptionManager.from(c).getActiveSubscriptionInfo(subId);
            if (sir != null && slot == sir.getSimSlotIndex()) {
                return account;
            }
        }
        return null;
    }
    //*/

    //*/ freeme.zhaozehong, 27/07/17. for custom ringtone
    public static final boolean supportCustomRingtone() {
        return com.freeme.util.FreemeOption.FREEME_CUSTOM_RINGTONE_SUPPORT;
    }

    public static final boolean supportDualSimCustomRingtone() {
        return supportCustomRingtone()
                && com.freeme.util.FreemeOption.FREEME_DUAL_SIM_RINGTONE_SUPPORT;
    }

    public static final int getSimCount(Context context) {
        List<SubscriptionInfo> infos = SubscriptionManager.from(context)
                .getActiveSubscriptionInfoList();
        return infos == null ? 0 : infos.size();
    }

    public static final int getRingtoneType(Context context) {
        if (supportDualSimCustomRingtone()) {
            List<SubscriptionInfo> infos = SubscriptionManager.from(context)
                    .getActiveSubscriptionInfoList();
            int simslot = infos == null ? 0 : infos.get(0).getSimSlotIndex();
            return simslot == 0 ? android.media.RingtoneManager.TYPE_RINGTONE
                    : com.freeme.media.FreemeRingtoneManager.TYPE_RINGTONE_SIM2;
        }
        return android.media.RingtoneManager.TYPE_RINGTONE;
    }
    //*/
}
