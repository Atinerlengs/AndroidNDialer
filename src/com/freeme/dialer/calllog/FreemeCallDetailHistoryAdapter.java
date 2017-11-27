package com.freeme.dialer.calllog;

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.dialer.calllog.CallTypeHelper;
import com.android.dialer.calllog.CallTypeIconsView;
import com.android.dialer.R;
import com.freeme.contacts.common.utils.FreemeDateTimeUtils;
import com.google.common.collect.Lists;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.DialerFeatureOptions;

/**
 * Created by zhangjunjian on 17-7-14.add for history call
 */

public class FreemeCallDetailHistoryAdapter extends CursorAdapter {

    private final Context mContext;
    private CallTypeHelper mCallTypeHelper;
    private boolean mIsMultNumber;

    public FreemeCallDetailHistoryAdapter(Context context, boolean isMultNumber) {
        super(context, null, true);
        mContext = context;
        mIsMultNumber = /*isMultNumber*/true;
        mCallTypeHelper = new CallTypeHelper(mContext.getResources());
    }

    @Override
    public boolean isEnabled(int position) {
        return false;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view;
        if (mIsMultNumber) {
            view = View.inflate(mContext, R.layout.freeme_contactor_call_logs_item, null);
            MultiNumberCallLogsDetail holder = new MultiNumberCallLogsDetail(view);
            view.setTag(holder);
        } else {
            view = View.inflate(mContext, R.layout.freeme_call_detail_history_item, null);
            SingleNumberCallLogsDetail holder = new SingleNumberCallLogsDetail(view);
            view.setTag(holder);
        }
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view.getTag() instanceof CallLogsDetails) {
            ((CallLogsDetails) view.getTag()).bindView(cursor);
        }
    }

    private class SingleNumberCallLogsDetail extends CallLogsDetails {
        CallTypeIconsView callTypeIconView;
        TextView dateView;
        TextView durationView;
        ImageView sim_card_status;

        SingleNumberCallLogsDetail(View view) {
            callTypeIconView = (CallTypeIconsView) view.findViewById(R.id.call_type_icon);
            dateView = (TextView) view.findViewById(R.id.date);
            durationView = (TextView) view.findViewById(R.id.duration);
            sim_card_status = (ImageView) view.findViewById(R.id.sim_card_status);
        }

        @Override
        void bindView(Cursor cursor) {
            int features = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.FEATURES));
            int callType = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
            int simId = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.SIM_IDX));
            String ipPrefix = cursor.getString(cursor.getColumnIndex(CallLog.Calls.IP_PREFIX));
            long date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
            long duration = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION));

            boolean isVideoCall = (features & CallLog.Calls.FEATURES_VIDEO) == CallLog.Calls.FEATURES_VIDEO
                    && CallUtil.isVideoEnabled(mContext);

            if (simId == -1 || simId == 0) {
                sim_card_status.setVisibility(View.VISIBLE);
                sim_card_status.setImageResource(R.drawable.freeme_contact_account_icon_sim1);
            } else {
                sim_card_status.setVisibility(View.VISIBLE);
                sim_card_status.setImageResource(R.drawable.freeme_contact_account_icon_sim2);
            }

            callTypeIconView.clear();
            callTypeIconView.add(callType);
            callTypeIconView.setShowVideo(isVideoCall);

            String callTypeString;
            if (DialerFeatureOptions.IP_PREFIX && !TextUtils.isEmpty(ipPrefix)
                    && callType == CallLog.Calls.OUTGOING_TYPE) {
                StringBuffer buffer = new StringBuffer();
                if (ipPrefix.length() > 8) {
                    buffer.append(ipPrefix.substring(0, 5));
                    buffer.append("...");
                } else {
                    buffer.append(ipPrefix);
                }
                String mIPOutgoingName = mContext.getString(
                        R.string.type_ip_outgoing, /* ipPrefix */buffer.toString());

                callTypeString = mIPOutgoingName;
            } else {
                callTypeString = mCallTypeHelper.getCallTypeText(callType, isVideoCall).toString();
            }
            CharSequence dateValue = DateUtils.formatDateRange(mContext, date, date,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                            DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_YEAR);
            dateView.setText(dateValue);

            if (CallLog.Calls.VOICEMAIL_TYPE == callType || CallTypeHelper.isMissedCallType(callType)) {
                durationView.setText(callTypeString);
            } else {
                durationView.setText(callTypeString + " " + FreemeDateTimeUtils.getDurations(duration));
            }

            ExtensionManager.getInstance().getCallDetailExtension().setDurationViewVisibility(
                    durationView);

            int color_res = R.color.call_log_item_title_color;
            if (callType == CallLog.Calls.MISSED_TYPE) {
                color_res = R.color.missed_call_color;
            }
            int color = mContext.getColor(color_res);
            dateView.setTextColor(color);
            durationView.setTextColor(color);
        }
    }

    private class MultiNumberCallLogsDetail extends CallLogsDetails {
        TextView numberTextView;
        View dateInfoLayout;
        CallTypeIconsView callTypeIconView;
        TextView dateView;
        TextView geoTextView;
        ImageView simCardImg;
        TextView durationView;

        MultiNumberCallLogsDetail(View view) {
            numberTextView = (TextView) view.findViewById(R.id.number);
            dateInfoLayout = view.findViewById(R.id.date_info_layout);
            geoTextView = (TextView) view.findViewById(R.id.call_location_and_date);
            callTypeIconView = (CallTypeIconsView) view.findViewById(R.id.call_type_icon);
            dateView = (TextView) view.findViewById(R.id.date);
            simCardImg = (ImageView) view.findViewById(R.id.sim_card_status);
            durationView = (TextView) view.findViewById(R.id.duration);
            durationView.setVisibility(View.VISIBLE);
        }

        @Override
        void bindView(Cursor cursor) {
            String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
            int features = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.FEATURES));
            int callType = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
            int simId = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.SIM_IDX));
            long date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
            String geo = cursor.getString(cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION));
            String ipPrefix = cursor.getString(cursor.getColumnIndex(CallLog.Calls.IP_PREFIX));
            long duration = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION));

            numberTextView.setText(number);
            int color_res = R.color.call_log_item_title_color;
            if (callType == CallLog.Calls.MISSED_TYPE) {
                color_res = R.color.missed_call_color;
            }
            int color = mContext.getColor(color_res);
            numberTextView.setTextColor(color);

            boolean isVideoCall = (features & CallLog.Calls.FEATURES_VIDEO) == CallLog.Calls.FEATURES_VIDEO
                    && CallUtil.isVideoEnabled(mContext);

            if (simId == -1 || simId == 0) {
                simCardImg.setVisibility(View.VISIBLE);
                simCardImg.setImageResource(R.drawable.freeme_contact_account_icon_sim1);
            } else {
                simCardImg.setVisibility(View.VISIBLE);
                simCardImg.setImageResource(R.drawable.freeme_contact_account_icon_sim2);
            }

            callTypeIconView.clear();
            callTypeIconView.add(callType);
            callTypeIconView.setShowVideo(isVideoCall);

            if (TextUtils.isEmpty(geo)) {
                geo = mContext.getString(R.string.freeme_geo_unknown_city);
            }
            geoTextView.setText(geo);

            CharSequence dateValue = DateUtils.formatDateRange(mContext, date, date,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                            DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_YEAR);
            dateView.setText(dateValue);

            String callTypeString;
            if (DialerFeatureOptions.IP_PREFIX && !TextUtils.isEmpty(ipPrefix)
                    && callType == CallLog.Calls.OUTGOING_TYPE) {
                StringBuffer buffer = new StringBuffer();
                if (ipPrefix.length() > 8) {
                    buffer.append(ipPrefix.substring(0, 5));
                    buffer.append("...");
                } else {
                    buffer.append(ipPrefix);
                }
                String mIPOutgoingName = mContext.getString(
                        R.string.type_ip_outgoing, /* ipPrefix */buffer.toString());

                callTypeString = mIPOutgoingName;
            } else {
                callTypeString = mCallTypeHelper.getCallTypeText(callType, isVideoCall).toString();
            }
            if (CallLog.Calls.VOICEMAIL_TYPE == callType || CallTypeHelper.isMissedCallType(callType)) {
                durationView.setText(callTypeString);
            } else {
                durationView.setText(callTypeString + " " + FreemeDateTimeUtils.getDurations(duration));
            }

            dateInfoLayout.measure(0, 0);
            int w = dateInfoLayout.getMeasuredWidth();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)
                    numberTextView.getLayoutParams();
            params.setMarginEnd(w);
            numberTextView.setLayoutParams(params);
        }
    }

    abstract class CallLogsDetails {
        abstract void bindView(Cursor cursor);
    }
}
