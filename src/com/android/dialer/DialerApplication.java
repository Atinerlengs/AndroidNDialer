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

package com.android.dialer;

import android.app.Application;
import android.content.Context;
import android.os.Trace;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.telecom.TelecomManager;

import com.android.contacts.common.extensions.ExtensionsFactory;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.filterednumber.BlockedNumbersAutoMigrator;
import com.android.internal.annotations.VisibleForTesting;

import com.mediatek.contacts.GlobalEnv;
import com.mediatek.dialer.dialersearch.DialerSearchHelper;
import com.mediatek.dialer.ext.ExtensionManager;

public class DialerApplication extends Application {

    private static final String TAG = "DialerApplication";

    private static Context sContext;

    @Override
    public void onCreate() {
        sContext = this;
        Trace.beginSection(TAG + " onCreate");
        super.onCreate();
        Trace.beginSection(TAG + " ExtensionsFactory initialization");
        ExtensionsFactory.init(getApplicationContext());
        Trace.endSection();
        new BlockedNumbersAutoMigrator(PreferenceManager.getDefaultSharedPreferences(this),
                new FilteredNumberAsyncQueryHandler(getContentResolver())).autoMigrate();
        Trace.beginSection(TAG + " Analytics initialization");
        AnalyticsUtil.initialize(this);
        Trace.endSection();
        /// M: for ALPS01907201, init GlobalEnv for mediatek ContactsCommon
        GlobalEnv.setApplicationContext(getApplicationContext());
        /// M: [MTK Dialer Search] fix ALPS01762713 @{
        DialerSearchHelper.initContactsPreferences(getApplicationContext());
        /// @}
        /// M: For plug-in @{
        ExtensionManager.getInstance().init(this);
        com.mediatek.contacts.ExtensionManager.registerApplicationContext(this);
        /// @}
        ///M:Add for Aas
        GlobalEnv.setAasExtension();
        Trace.endSection();
    }

    @Nullable
    public static Context getContext() {
        return sContext;
    }

    @NeededForTesting
    public static void setContextForTest(Context context) {
        sContext = context;
    }


    /// M: use to override system real service start @{
    private TelecomManager mTelecomManager;

    @Override
    public Object getSystemService(String name) {
        if (Context.TELECOM_SERVICE.equals(name) && mTelecomManager != null) {
            return mTelecomManager;
        }
        return super.getSystemService(name);
    }

    @VisibleForTesting
    public void setTelecomManager(TelecomManager telecom) {
        mTelecomManager = telecom;
    };
    /// M: end @}
}
