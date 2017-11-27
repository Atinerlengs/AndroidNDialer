package com.freeme.dialer.action;

import android.app.PendingIntent;

/**
 * Created by zhangjunjian on 20171018.
 */

public class FreemeCallAction {
    public static final int TEXT_COLOR_INIT = 0XFF8D8D8D;
    public static final int TEXT_COLOR_ANSWER = 0XFF29BD68;
    public static final int TEXT_COLOR_ACTION = 0Xde000000;
    public static final int TEXT_COLOR_VIDEO = TEXT_COLOR_ANSWER;
    public static final int TEXT_COLOR_AUDIO = TEXT_COLOR_INIT;
    public static final int TEXT_COLOR_REFUSE = 0XFFDE2D03;

    private PendingIntent mPendingIntent;
    private CharSequence mText;
    private int mTextColor;
    private int mIcon;
    private boolean isEmpty;

    public FreemeCallAction() {
        this(0, null, TEXT_COLOR_INIT, null);
        isEmpty = true;
    }

    public FreemeCallAction(int icon, CharSequence text, PendingIntent pendingIntent) {
        this(icon, text, TEXT_COLOR_INIT, pendingIntent);
    }

    public FreemeCallAction(CharSequence text, int textColor, PendingIntent pendingIntent) {
        this.mText = text;
        this.mTextColor = textColor;
        this.mPendingIntent = pendingIntent;
        isEmpty = false;
    }

    public FreemeCallAction(int icon, CharSequence text, int textColor, PendingIntent pendingIntent) {
        this.mIcon = icon;
        this.mText = text;
        this.mTextColor = textColor;
        this.mPendingIntent = pendingIntent;
        isEmpty = false;
    }

    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    public CharSequence getText() {
        return mText;
    }

    public int getTextColor() {
        return mTextColor;
    }

    public int getIcon() {
        return mIcon;
    }

    public boolean isEmpty() {
        return isEmpty;
    }
}
