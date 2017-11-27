package com.freeme.incallui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.dialer.R;

/**
 * Created by zhaozehong on 20170804.
 */

public class FreemeCallInfoView extends FrameLayout {

    public TextView mCallName;
    public View mCallProviderInfo;
    public ImageView mCallProviderIcon;
    public TextView mCallProviderLabel;
    public TextView mCallStatus;
    public View mCallConferenceCallIcon;

    public FreemeCallInfoView(Context context) {
        super(context);
    }

    public FreemeCallInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflate = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflate.inflate(R.layout.freeme_call_info_view, this);
        mCallName = (TextView) findViewById(R.id.callName);
        mCallProviderInfo = (View) findViewById(R.id.call_provider_info);
        mCallProviderIcon = (ImageView) findViewById(R.id.callProviderIcon);
        mCallProviderLabel = (TextView) findViewById(R.id.callProviderLabel);
        mCallStatus = (TextView) findViewById(R.id.callStatus);
        mCallConferenceCallIcon = findViewById(R.id.callConferenceCallIcon);
    }
}
