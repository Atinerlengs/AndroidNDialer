package com.freeme.dialer.utils;

import android.content.Intent;

public class FreemeEntranceRequst {

    private final String CLASS_NAME_CONTACTS = "com.android.dialer.DialerContactsActivity";
    private final String CLASS_NAME_DIALER = "com.android.dialer.DialtactsActivity";

    public final static int ENTRANCE_NOCHANGE = 0;
    public final static int ENTRANCE_DAIL = 10;
    public final static int ENTRANCE_CONTACTS = 20;

    private int mEntranceCode = 0;
    private boolean mIsRecreatedInstance;
    private boolean mIsFromNewIntent;

    public void resolveIntent(Intent intent) {
        String className = intent.getComponent().getClassName();
        if (CLASS_NAME_CONTACTS.equals(className)) {
            mEntranceCode = ENTRANCE_CONTACTS;
        } else if (CLASS_NAME_DIALER.equals(className)) {
            mEntranceCode = ENTRANCE_DAIL;
        }
        if (!mIsFromNewIntent && mIsRecreatedInstance) {
            mEntranceCode = ENTRANCE_NOCHANGE;
        }
    }

    public int getEntranceCode() {
        return mEntranceCode;
    }

    public void setIsRecreatedInstance(boolean isRecreatedInstance) {
        this.mIsRecreatedInstance = isRecreatedInstance;
    }

    public boolean isRecreatedInstance() {
        return mIsRecreatedInstance;
    }

    public void setIsFromNewIntent(boolean isFromNewIntent) {
        this.mIsFromNewIntent = isFromNewIntent;
    }

    public boolean isFromNewIntent() {
        return mIsFromNewIntent;
    }
}
