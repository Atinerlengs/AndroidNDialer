package com.freeme.dialer.calllog;

import android.view.Menu;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ContentUris;
import android.net.Uri;
import android.os.Bundle;
import android.database.Cursor;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.ListView;
import android.os.Handler;
import android.provider.CallLog;
import android.database.ContentObserver;
import android.provider.CallLog.Calls;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.GeoUtil;
import com.android.dialer.calllog.CallDetailHistoryAdapter;
import com.android.dialer.calllog.CallTypeHelper;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.util.TelecomUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.R;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.CallLogAsyncTaskUtil.CallLogAsyncTaskListener;
import com.mediatek.dialer.activities.NeedTestActivity;
import com.mediatek.dialer.calllog.VolteConfCallMemberListAdapter;
import com.mediatek.dialer.util.DialerFeatureOptions;

/**
 * freeme.zhangjunjian,20170713,create for history call
 */
public class FreemeCallLogDetailActivity extends NeedTestActivity {

    public static final String EXTRA_CALL_LOG_TYPE_FILTER = "call_log_type_filter";
    public static final String EXTRA_IS_CONFERENCE_CALL = "call_log_is_conference";
    public static final String EXTRA_CALL_LOG_IDS = "call_log_ids";
    public static final String EXTRA_PHONE_NUMBERS = "phone_numbers";

    private FreemeCallDetailHistoryAdapter mHistoryAdapter;
    private VolteConfCallMemberListAdapter mConferenceAdatper;
    private ContactInfoHelper mContactInfoHelper;
    private LinearLayoutManager mLayoutManager;
    private CallTypeHelper mCallTypeHelper;
    private RecyclerView mConferenceList;
    private ListView mHistoryList;

    private final ContentObserver mCallLogObserver = new CustomContentObserver();

    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            getCallLogsData();
        }
    }

    private boolean mIsConferenceCall;
    private int mCallTypeFilter;
    private String[] mNumbers;
    private long[] mCallIds;
    private EmptyContentView mEmptyListView;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNumbers = getIntent().getStringArrayExtra(EXTRA_PHONE_NUMBERS);
        if (mNumbers == null || mNumbers.length <= 0) {
            finish();
            return;
        }

        mCallTypeFilter = getIntent().getIntExtra(EXTRA_CALL_LOG_TYPE_FILTER,
                CallLogQueryHandler.CALL_TYPE_ALL);
        if (DialerFeatureOptions.isVolteConfCallLogSupport()) {
            mIsConferenceCall = getIntent().getBooleanExtra(EXTRA_IS_CONFERENCE_CALL, false);
        }
        mCallIds = getIntent().getLongArrayExtra(EXTRA_CALL_LOG_IDS);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setContentView(R.layout.freeme_call_log_detail_activity);

        mEmptyListView = (EmptyContentView) findViewById(R.id.empty_list_view);
        mEmptyListView.setImage(R.drawable.empty_call_log);
        mEmptyListView.setDescription(R.string.call_log_all_empty);
        mHistoryList = (ListView) findViewById(R.id.history);
        ImageView headLine = new ImageView(this);
        headLine.setBackgroundResource(R.color.freeme_list_divider_color);
        headLine.setMaxHeight(1);
        headLine.setMinimumHeight(1);
        mHistoryList.addHeaderView(headLine);

        if (mIsConferenceCall) {
            mConferenceList = (RecyclerView) findViewById(R.id.conf_call_member_list);
            mConferenceList.setHasFixedSize(true);
            mLayoutManager = new LinearLayoutManager(this);
            mConferenceList.setLayoutManager(mLayoutManager);
            mConferenceList.setVisibility(View.VISIBLE);
            mCallTypeHelper = new CallTypeHelper(getResources());
            mContactInfoHelper = new ContactInfoHelper(this, GeoUtil.getCurrentCountryIso(this));
            mConferenceAdatper = new VolteConfCallMemberListAdapter(this, mContactInfoHelper);
            mConferenceList.setAdapter(mConferenceAdatper);
            mEmptyListView.setVisibility(View.GONE);
        } else {
            mHistoryAdapter = new FreemeCallDetailHistoryAdapter(this, mNumbers.length > 1);
            mHistoryList.setAdapter(mHistoryAdapter);
        }

        getContentResolver().registerContentObserver(CallLog.CONTENT_URI, true, mCallLogObserver);
    }

    private void getCallDetails() {
        if (mNumbers == null || mNumbers.length <= 0) {
            mHistoryAdapter.changeCursor(null);
            return;
        }

        StringBuffer numBuffer = new StringBuffer();
        for (String num : mNumbers) {
            if (numBuffer.length() > 0) {
                numBuffer.append(",");
            }
            // perhaps a special character
            numBuffer.append("\'" + num + "\'");
        }

        StringBuffer buffer = new StringBuffer()
                .append("number in (")
                .append(numBuffer.toString())
                .append(")");
        if (mCallTypeFilter != CallLogQueryHandler.CALL_TYPE_ALL) {
            buffer.append(" and type = ").append(mCallTypeFilter);
        }

        Cursor cursor = getContentResolver().query(Calls.CONTENT_URI, null, buffer.toString(),
                null, "date desc");
        mHistoryAdapter.changeCursor(cursor);
        if (mHistoryAdapter.getCount() > 0) {
            mEmptyListView.setVisibility(View.GONE);
        } else {
            mEmptyListView.setVisibility(View.VISIBLE);
        }

        invalidateOptionsMenu();

        if (cursor != null && cursor.getCount() <= 0) {
            cursor.close();
        }

    }

    private void updateConfCallData() {
        if (mCallIds != null && mCallIds.length > 0) {
            mConferenceAdatper.invalidateCache();
            mConferenceAdatper.setLoading(true);
            CallLogAsyncTaskUtil.getConferenceCallDetails(this, mCallIds,
                    mConfCallLogAsyncTaskListener);
        } else {
            mConferenceAdatper.setLoading(false);
            mConferenceAdatper.changeCursor(null);
        }
    }

    private CallLogAsyncTaskUtil.ConfCallLogAsyncTaskListener mConfCallLogAsyncTaskListener =
            new CallLogAsyncTaskUtil.ConfCallLogAsyncTaskListener() {

                @Override
                public void onGetConfCallDetails(Cursor cursor, PhoneCallDetails[] details) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        Toast.makeText(FreemeCallLogDetailActivity.this,
                                R.string.toast_call_detail_error, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    mConferenceAdatper.setLoading(false);
                    mConferenceAdatper.setCallDetailHistoryAdapter(
                            new CallDetailHistoryAdapter(FreemeCallLogDetailActivity.this,
                                    LayoutInflater.from(FreemeCallLogDetailActivity.this),
                                    mCallTypeHelper, generateConferenceCallDetails(details)));
                    mConferenceAdatper.setConferenceCallDetails(details);
                    mConferenceAdatper.invalidatePositions();
                    mConferenceAdatper.changeCursor(cursor);
                    mHistoryList.setVisibility(View.GONE);
                }

                private PhoneCallDetails[] generateConferenceCallDetails(PhoneCallDetails[] details) {
                    PhoneCallDetails[] confCallDetails = new PhoneCallDetails[1];
                    if (details == null || details.length < 1) {
                        return confCallDetails;
                    }
                    long minDate = details[0].date;
                    long maxDuration = details[0].duration;
                    Long sumDataUsage = null;
                    for (PhoneCallDetails detail : details) {
                        if (minDate > detail.date) {
                            minDate = detail.date;
                        }
                        if (maxDuration < detail.duration) {
                            maxDuration = detail.duration;
                        }
                        if (null != detail.dataUsage) {
                            if (sumDataUsage == null) {
                                sumDataUsage = 0L;
                            }
                            sumDataUsage += detail.dataUsage;
                        }
                    }
                    confCallDetails[0] = details[0];
                    confCallDetails[0].date = minDate;
                    confCallDetails[0].duration = maxDuration;
                    confCallDetails[0].dataUsage = sumDataUsage;
                    return confCallDetails;
                }
            };

    @Override
    public void onResume() {
        super.onResume();
        getCallLogsData();
    }

    private void getCallLogsData() {
        if (mIsConferenceCall) {
            updateConfCallData();
            if (mConferenceAdatper != null) {
                mConferenceAdatper.onResume();
            }
            return;
        }
        getCallDetails();
    }

    @Override
    public void onPause() {
        if (mConferenceAdatper != null) {
            mConferenceAdatper.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mConferenceAdatper != null) {
            mConferenceAdatper.changeCursor(null);
        }
        if (mHistoryAdapter != null) {
            mHistoryAdapter.changeCursor(null);
        }
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mCallLogObserver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        if (onFreemeOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final int MENU_ITEM_ID_CLEAR = 0x100;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ITEM_ID_CLEAR, 0, R.string.freeme_contact_delete_positive_button)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    private boolean onFreemeOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_ITEM_ID_CLEAR) {
            showDialogComfirmDelete();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem deleteMenu = menu.findItem(MENU_ITEM_ID_CLEAR);
        deleteMenu.setEnabled(mHistoryAdapter.getCount()  > 0);
        return true;
    }

    private void showDialogComfirmDelete() {
        AlertDialog.Builder build = new AlertDialog.Builder(FreemeCallLogDetailActivity.this);
        build.setMessage(R.string.deleteCallLogConfirmation_title);
        build.setNegativeButton(android.R.string.cancel, null);
        build.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int position) {
                if (mCallIds != null && mCallIds.length > 0) {
                    final StringBuilder builder = new StringBuilder();
                    for (Uri callUri : getCallLogEntryUris()) {
                        if (builder.length() != 0) {
                            builder.append(",");
                        }
                        builder.append(ContentUris.parseId(callUri));
                    }
                    CallLogAsyncTaskUtil.deleteCalls(
                            FreemeCallLogDetailActivity.this, builder.toString(), mCallLogAsyncTaskListener);
                }
                CallLogAsyncTaskUtil.deleteCalls(
                        FreemeCallLogDetailActivity.this, mNumbers, mCallTypeFilter, mCallLogAsyncTaskListener);
            }
        });
        build.show();
    }

    private Uri[] getCallLogEntryUris() {
        final int numIds = mCallIds == null ? 0 : mCallIds.length;
        final Uri[] uris = new Uri[numIds];
        for (int index = 0; index < numIds; ++index) {
            uris[index] = ContentUris.withAppendedId(TelecomUtil.getCallLogUri(this), mCallIds[index]);
        }
        return uris;
    }

    private CallLogAsyncTaskListener mCallLogAsyncTaskListener = new CallLogAsyncTaskListener() {
        @Override
        public void onDeleteCall() {
            Toast.makeText(FreemeCallLogDetailActivity.this, R.string.freeme_toast_call_delete_success,
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        @Override
        public void onDeleteVoicemail() {

        }

        @Override
        public void onGetCallDetails(PhoneCallDetails[] details) {

        }
    };
}