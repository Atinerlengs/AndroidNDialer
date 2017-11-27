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

import android.app.Activity;
import android.app.Fragment;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.Log;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.R;
import com.android.dialer.list.ListsFragment;
import com.android.dialer.util.EmptyLoader;
import com.android.dialer.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import com.android.dialerbind.ObjectFactory;

import com.mediatek.contacts.util.VvmUtils;
import com.mediatek.dialer.activities.CallLogSearchResultActivity;
import com.mediatek.dialer.calllog.PhoneAccountInfoHelper;
import com.mediatek.dialer.calllog.PhoneAccountInfoHelper.AccountInfoListener;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.DialerConstants;
import com.mediatek.dialer.util.DialerFeatureOptions;

import java.util.List;
//*/ freeme.zhaozehong, 20170803. close dialpad when scroll list
import com.android.dialer.list.OnListFragmentScrolledListener;
//*/
//*/ freeme.zhaozehong, 20170818. for freemeOS, redesign dialpad
import com.android.dialer.DialtactsActivity;
//*/

/**
 * Displays a list of call log entries. To filter for a particular kind of call
 * (all, missed or voicemails), specify it in the constructor.
 */
public class CallLogFragment extends Fragment implements CallLogQueryHandler.Listener,
        CallLogAdapter.CallFetcher, OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback, /*M:*/AccountInfoListener {
    private static final String TAG = "CallLogFragment";

    /** M: request full group permissions instead of READ_CALL_LOG,
     * Because MTK changed the group permissions granting logic.
     */
    private static final String[] READ_CALL_LOG = PermissionsUtil.PHONE_FULL_GROUP;

    /**
     * ID of the empty loader to defer other fragments.
     */
    private static final int EMPTY_LOADER_ID = 0;

    private static final String KEY_FILTER_TYPE = "filter_type";
    private static final String KEY_LOG_LIMIT = "log_limit";
    private static final String KEY_DATE_LIMIT = "date_limit";

    private static final String KEY_IS_CALL_LOG_ACTIVITY = "is_call_log_activity";

    // No limit specified for the number of logs to show; use the CallLogQueryHandler's default.
    private static final int NO_LOG_LIMIT = -1;
    // No date-based filtering.
    private static final int NO_DATE_LIMIT = 0;

    private static final int READ_CALL_LOG_PERMISSION_REQUEST_CODE = 1;

    private static final int EVENT_UPDATE_DISPLAY = 1;

    private static final long MILLIS_IN_MINUTE = 60 * 1000;

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private CallLogAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private boolean mScrollToTop;


    private EmptyContentView mEmptyListView;
    private KeyguardManager mKeyguardManager;

    private boolean mEmptyLoaderRunning;
    private boolean mCallLogFetched;
    private boolean mVoicemailStatusFetched;

    private final Handler mDisplayUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_UPDATE_DISPLAY:
                    refreshData();
                    rescheduleDisplayUpdate();
                    break;
            }
        }
    };

    private final Handler mHandler = new Handler();

    protected class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(mHandler);
        }
        @Override
        public void onChange(boolean selfChange) {
            mRefreshDataRequired = true;
        }
    }

    // See issue 6363009
    private final ContentObserver mCallLogObserver = new CustomContentObserver();
    private final ContentObserver mContactsObserver = new CustomContentObserver();
    private boolean mRefreshDataRequired = true;

    private boolean mHasReadCallLogPermission = false;

    // Exactly same variable is in Fragment as a package private.
    private boolean mMenuVisible = true;

    // Default to all calls.
    private int mCallTypeFilter = CallLogQueryHandler.CALL_TYPE_ALL;

    // Log limit - if no limit is specified, then the default in {@link CallLogQueryHandler}
    // will be used.
    private int mLogLimit = NO_LOG_LIMIT;

    // Date limit (in millis since epoch) - when non-zero, only calls which occurred on or after
    // the date filter are included.  If zero, no date-based filtering occurs.
    private long mDateLimit = NO_DATE_LIMIT;

    /// M: [Call Log Account Filter] @{
    private TextView mNoticeText;
    private View mNoticeTextDivider;
    /// @}

    //*/ freeme.zhaozehong, 18/07/17. filter call logs
    private TextView mCalllogsFilterAll;
    private TextView mCalllogsFilterMissed;
    private TextView mCalllogsFilterOutgoing;
    private TextView mCalllogsFilterIncoming;
    //*/

    /*
     * True if this instance of the CallLogFragment shown in the CallLogActivity.
     */
    private boolean mIsCallLogActivity = false;

    public interface HostInterface {
        public void showDialpad();
    }

    public CallLogFragment() {
        this(CallLogQueryHandler.CALL_TYPE_ALL, NO_LOG_LIMIT);
    }

    public CallLogFragment(int filterType) {
        this(filterType, NO_LOG_LIMIT);
    }

    public CallLogFragment(int filterType, boolean isCallLogActivity) {
        this(filterType, NO_LOG_LIMIT);
        mIsCallLogActivity = isCallLogActivity;
    }

    public CallLogFragment(int filterType, int logLimit) {
        this(filterType, logLimit, NO_DATE_LIMIT);
    }

    /**
     * Creates a call log fragment, filtering to include only calls of the desired type, occurring
     * after the specified date.
     * @param filterType type of calls to include.
     * @param dateLimit limits results to calls occurring on or after the specified date.
     */
    public CallLogFragment(int filterType, long dateLimit) {
        this(filterType, NO_LOG_LIMIT, dateLimit);
    }

    public CallLogFragment(int filterType, int logLimit, long dateLimit) {
        mCallTypeFilter = filterType;
        mLogLimit = logLimit;
        mDateLimit = dateLimit;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            mCallTypeFilter = state.getInt(KEY_FILTER_TYPE, mCallTypeFilter);
            mLogLimit = state.getInt(KEY_LOG_LIMIT, mLogLimit);
            mDateLimit = state.getLong(KEY_DATE_LIMIT, mDateLimit);

            /// M: [Call Log Account Filter]
            mNeedAccountFilter = state.getBoolean(KEY_NEED_ACCOUNT_FILTER);

            mIsCallLogActivity = state.getBoolean(KEY_IS_CALL_LOG_ACTIVITY, mIsCallLogActivity);
        }

        final Activity activity = getActivity();
        final ContentResolver resolver = activity.getContentResolver();
        String currentCountryIso = GeoUtil.getCurrentCountryIso(activity);
        mCallLogQueryHandler = new CallLogQueryHandler(activity, resolver, this, mLogLimit);
        mKeyguardManager =
                (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        resolver.registerContentObserver(CallLog.CONTENT_URI, true, mCallLogObserver);
        resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true,
                mContactsObserver);
        setHasOptionsMenu(true);

        /// M: [Call Log Account Filter] add account change listener
        PhoneAccountInfoHelper.getInstance(getActivity()).registerForAccountChange(this);
        /// @}
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public boolean onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            // Return false; we did not take ownership of the cursor
            return false;
        }
        mAdapter.invalidatePositions();
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);
        // This will update the state of the "Clear call log" menu item.
        getActivity().invalidateOptionsMenu();

        boolean showListView = cursor != null && cursor.getCount() > 0;
        mRecyclerView.setVisibility(showListView ? View.VISIBLE : View.GONE);
        mEmptyListView.setVisibility(!showListView ? View.VISIBLE : View.GONE);

        if (mScrollToTop) {
            // The smooth-scroll animation happens over a fixed time period.
            // As a result, if it scrolls through a large portion of the list,
            // each frame will jump so far from the previous one that the user
            // will not experience the illusion of downward motion.  Instead,
            // if we're not already near the top of the list, we instantly jump
            // near the top, and animate from there.
            if (mLayoutManager.findFirstVisibleItemPosition() > 5) {
                // TODO: Jump to near the top, then begin smooth scroll.
                mRecyclerView.smoothScrollToPosition(0);
            }
            // Workaround for framework issue: the smooth-scroll doesn't
            // occur if setSelection() is called immediately before.
            mHandler.post(new Runnable() {
               @Override
               public void run() {
                   if (getActivity() == null || getActivity().isFinishing()) {
                       return;
                   }
                   mRecyclerView.smoothScrollToPosition(0);
               }
            });

            mScrollToTop = false;
        }
        mCallLogFetched = true;
        destroyEmptyLoaderIfAllDataFetched();

        /** M:  [Dialer Global Search] notify search activity update search result. @{*/
        updateSearchResultIfNeed(cursor);
        /** @}*/
        return true;
    }

    /**
     * Called by {@link CallLogQueryHandler} after a successful query to voicemail status provider.
     */
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        mVoicemailStatusFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    private void destroyEmptyLoaderIfAllDataFetched() {
        if (mCallLogFetched && mVoicemailStatusFetched && mEmptyLoaderRunning) {
            mEmptyLoaderRunning = false;
            getLoaderManager().destroyLoader(EMPTY_LOADER_ID);
        }
    }

    @Override
    public void onVoicemailUnreadCountFetched(Cursor cursor) {}

    @Override
    public void onMissedCallsUnreadCountFetched(Cursor cursor) {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);
        setupView(view, null);
        return view;
    }

    protected void setupView(
            View view, @Nullable VoicemailPlaybackPresenter voicemailPlaybackPresenter) {
        //*/ freeme.zhaozehong, 18/07/17. filter call logs
        mCalllogsFilterAll = (TextView)view.findViewById(R.id.call_logs_all);
        mCalllogsFilterAll.setSelected(true);
        mCalllogsFilterMissed = (TextView)view.findViewById(R.id.call_logs_missed);
        mCalllogsFilterOutgoing = (TextView)view.findViewById(R.id.call_logs_outgoing);
        mCalllogsFilterIncoming = (TextView)view.findViewById(R.id.call_logs_incoming);
        mCalllogsFilterAll.setOnClickListener(mCalllogsFilterListener);
        mCalllogsFilterMissed.setOnClickListener(mCalllogsFilterListener);
        mCalllogsFilterOutgoing.setOnClickListener(mCalllogsFilterListener);
        mCalllogsFilterIncoming.setOnClickListener(mCalllogsFilterListener);
        //*/
        /** M: [Call Log Account Filter] add Notice for account filter @{ */
        mNoticeText = (TextView) view.findViewById(R.id.notice_text);
        mNoticeTextDivider = view.findViewById(R.id.notice_text_divider);
        /** @} */
        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mEmptyListView = (EmptyContentView) view.findViewById(R.id.empty_list_view);
        mEmptyListView.setImage(R.drawable.empty_call_log);
        mEmptyListView.setActionClickedListener(this);

        int activityType = mIsCallLogActivity ? CallLogAdapter.ACTIVITY_TYPE_CALL_LOG :
                CallLogAdapter.ACTIVITY_TYPE_DIALTACTS;
        String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        mAdapter = ObjectFactory.newCallLogAdapter(
                        getActivity(),
                        this,
                        new ContactInfoHelper(getActivity(), currentCountryIso),
                        voicemailPlaybackPresenter,
                        activityType);
        mRecyclerView.setAdapter(mAdapter);
        /// M: listening recyclerview scroll state
        mRecyclerView.addOnScrollListener(new ViewScrollListener());
        //*/ freeme.zhaozehong, 20170801. for freemeOS, update call logs filter title
        updateCalllogsFilterTitle();
        //*/
        fetchCalls();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateEmptyMessage(mCallTypeFilter);
        mAdapter.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        // Start the empty loader now to defer other fragments.  We destroy it when both calllog
        // and the voicemail status are fetched.
        getLoaderManager().initLoader(EMPTY_LOADER_ID, null,
                new EmptyLoader.Callback(getActivity()));
        mEmptyLoaderRunning = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean hasReadCallLogPermission =
                PermissionsUtil.hasPermission(getActivity(), READ_CALL_LOG);
        if (!mHasReadCallLogPermission && hasReadCallLogPermission) {
            // We didn't have the permission before, and now we do. Force a refresh of the call log.
            // Note that this code path always happens on a fresh start, but mRefreshDataRequired
            // is already true in that case anyway.
            mRefreshDataRequired = true;
            updateEmptyMessage(mCallTypeFilter);
        }

        mHasReadCallLogPermission = hasReadCallLogPermission;
        refreshData();
        mAdapter.onResume();

        rescheduleDisplayUpdate();
    }

    @Override
    public void onPause() {
        cancelDisplayUpdate();
        mAdapter.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        updateOnTransition();

        super.onStop();
    }

    @Override
    public void onDestroy() {
        mAdapter.changeCursor(null);

        getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);

        /// M: [Call Log Account Filter] unregister account change listener
        PhoneAccountInfoHelper.getInstance(getActivity()).unRegisterForAccountChange(this);
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_FILTER_TYPE, mCallTypeFilter);
        outState.putInt(KEY_LOG_LIMIT, mLogLimit);
        outState.putLong(KEY_DATE_LIMIT, mDateLimit);
        outState.putBoolean(KEY_IS_CALL_LOG_ACTIVITY, mIsCallLogActivity);

        /// M: [Call Log Account Filter]
        outState.putBoolean(KEY_NEED_ACCOUNT_FILTER, mNeedAccountFilter);

        mAdapter.onSaveInstanceState(outState);
    }

    @Override
    public void fetchCalls() {
        //*/ freeme.zhangjunjian, 17-7-28, add for call log
        mAdapter.setCalllogsFilter(mCallTypeFilter);
        //*/
        /// M: Do nothing while view scrolling to improve scroll performance
        if (mIsScrolling) {
            return;
        }
        /** M: [Dialer Global Search] Displays a list of call log entries @{ */
        if (isQueryMode()) {
            startSearchCalls(mQueryData);
        } else {
        /** @} */
            /// M: [Call Log Account Filter] add call log account filter support @{
            mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit, getAccountFilterId());
            /// @}
            if (!mIsCallLogActivity) {
                /*/ freeme.zhaozehong, 20170818. for freemeOS, redesign dialpad
                ((ListsFragment) getParentFragment()).updateTabUnreadCounts();
                /*/
                if (getActivity() instanceof DialtactsActivity) {
                    ((DialtactsActivity) getActivity()).getListsFragment().updateTabUnreadCounts();
                }
                //*/
            }
        }
    }

    private void updateEmptyMessage(int filterType) {
        final Context context = getActivity();
        if (context == null) {
            return;
        }

        if (!PermissionsUtil.hasPermission(context, READ_CALL_LOG)) {
            mEmptyListView.setDescription(R.string.permission_no_calllog);
            mEmptyListView.setActionLabel(R.string.permission_single_turn_on);
            return;
        }

        final int messageId;
        switch (filterType) {
            case Calls.MISSED_TYPE:
                /*/ freeme.zhangjunjian,20170808,add for update empty message
                messageId = R.string.call_log_missed_empty;
                /*/
                messageId = R.string.freeme_call_log_missed_empty;
                //*/
                break;
            case Calls.VOICEMAIL_TYPE:
                messageId = R.string.call_log_voicemail_empty;
                break;
            case CallLogQueryHandler.CALL_TYPE_ALL:
                /** M: [Dialer Global Search] Search mode with customer empty string. */
                messageId = isQueryMode() ? R.string.noMatchingCalllogs
                            : R.string.call_log_all_empty;
                /** @} */
                break;
            /** M: [CallLog Incoming and Outgoing Filter] @{ */
            /*/ freeme.zhangjunjian,20170808,add for update empty message
            case Calls.INCOMING_TYPE:
                messageId = R.string.call_log_all_empty;
                break;
            case Calls.OUTGOING_TYPE:
                messageId = R.string.call_log_all_empty;
                break;
            /*/
            case Calls.INCOMING_TYPE:
                messageId = R.string.freeme_call_log_incoming_empty;
                break;
            case Calls.OUTGOING_TYPE:
                messageId = R.string.freeme_call_log_outgoing_empty;
                break;
            //*/
            /** @} */
            default:
                throw new IllegalArgumentException("Unexpected filter type in CallLogFragment: "
                        + filterType);
        }
        mEmptyListView.setDescription(messageId);
        /*/ freeme.zhangjunjian, 20170804 add for Dial number disappear when the screen orientation changes
        if (mIsCallLogActivity) {
            mEmptyListView.setActionLabel(EmptyContentView.NO_LABEL);
        } else if (filterType == CallLogQueryHandler.CALL_TYPE_ALL) {
            mEmptyListView.setActionLabel(R.string.call_log_all_empty_action);
        }
        /*/
        if (mIsCallLogActivity) {
            mEmptyListView.setActionLabel(EmptyContentView.NO_LABEL);
        } else {
            mEmptyListView.setActionLabel(R.string.call_log_all_empty_action);
        }
        //*/
    }

    CallLogAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (mMenuVisible != menuVisible) {
            mMenuVisible = menuVisible;
            if (!menuVisible) {
                updateOnTransition();
            } else if (isResumed()) {
                refreshData();
            }
        }
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Prevent unnecessary refresh.
        /// M: for Op01 Plug-in about callog CDD @{
        if (mRefreshDataRequired || ExtensionManager
                .getInstance().getCallLogExtension().isNeedRefesh()) {
        /// @}
            mAdapter.invalidateCache();
            mAdapter.setLoading(true);

            fetchCalls();
            mCallLogQueryHandler.fetchVoicemailStatus();
            mCallLogQueryHandler.fetchMissedCallsUnreadCount();
            updateOnTransition();
            mRefreshDataRequired = false;
        } else {
            // Refresh the display of the existing data to update the timestamp text descriptions.
            mAdapter.notifyDataSetChanged();
        }

        /** M: [Call Log Account Filter] @{ */
        if (mNeedAccountFilter) {
            updateNotice();
        }
        /** @} */
    }

    /**
     * Updates the voicemail notification state.
     *
     * TODO: Move to CallLogActivity
     */
    private void updateOnTransition() {
        // We don't want to update any call data when keyguard is on because the user has likely not
        // seen the new calls yet.
        // This might be called before onCreate() and thus we need to check null explicitly.
        if (mKeyguardManager != null && !mKeyguardManager.inKeyguardRestrictedInputMode()
                && mCallTypeFilter == Calls.VOICEMAIL_TYPE) {
            CallLogNotificationsHelper.updateVoicemailNotifications(getActivity());
        }
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (!PermissionsUtil.hasPermission(activity, READ_CALL_LOG)) {
          FragmentCompat.requestPermissions(this, /*M:*/READ_CALL_LOG,
              READ_CALL_LOG_PERMISSION_REQUEST_CODE);
        } else if (!mIsCallLogActivity) {
            // Show dialpad if we are not in the call log activity.
            ((HostInterface) activity).showDialpad();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == READ_CALL_LOG_PERMISSION_REQUEST_CODE) {
            if (grantResults.length >= 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                // Force a refresh of the data since we were missing the permission before this.
                mRefreshDataRequired = true;
            }
        }
    }

    /**
     * Schedules an update to the relative call times (X mins ago).
     */
    private void rescheduleDisplayUpdate() {
        if (!mDisplayUpdateHandler.hasMessages(EVENT_UPDATE_DISPLAY)) {
            long time = System.currentTimeMillis();
            // This value allows us to change the display relatively close to when the time changes
            // from one minute to the next.
            long millisUtilNextMinute = MILLIS_IN_MINUTE - (time % MILLIS_IN_MINUTE);
            mDisplayUpdateHandler.sendEmptyMessageDelayed(
                    EVENT_UPDATE_DISPLAY, millisUtilNextMinute);
        }
    }

    /**
     * Cancels any pending update requests to update the relative call times (X mins ago).
     */
    private void cancelDisplayUpdate() {
        mDisplayUpdateHandler.removeMessages(EVENT_UPDATE_DISPLAY);
    }

    /// M: [Multi-Delete] For CallLog delete @{
    @Override
    public void onCallsDeleted() {
        // Do nothing
    }
    /// @}

    /// M: [Call Log Account Filter] @{
    private static final String KEY_NEED_ACCOUNT_FILTER = "need_account_filter";
    // Whether or not to use account filter, currently call log screen use account filter
    // while recents call log  need not
    private boolean mNeedAccountFilter = DialerFeatureOptions.isCallLogAccountFilterEnabled();

    public void setAccountFilterState(boolean enable) {
        mNeedAccountFilter = enable;
    }

    private String getAccountFilterId() {
        if (DialerFeatureOptions.isCallLogAccountFilterEnabled() && mNeedAccountFilter) {
            return PhoneAccountInfoHelper.getInstance(getActivity()).getPreferAccountId();
        } else {
            return PhoneAccountInfoHelper.FILTER_ALL_ACCOUNT_ID;
        }
    }

    private void updateNotice() {
        String lable = null;
        String id = PhoneAccountInfoHelper.getInstance(getActivity()).getPreferAccountId();
        if (getActivity() != null && !PhoneAccountInfoHelper.FILTER_ALL_ACCOUNT_ID.equals(id)) {
            PhoneAccountHandle account = PhoneAccountUtils.getPhoneAccountById(getActivity(), id);
            if (account != null) {
                lable = PhoneAccountUtils.getAccountLabel(getActivity(), account);
            }
        }
        if (!TextUtils.isEmpty(lable) && mNoticeText != null && mNoticeTextDivider != null) {
            mNoticeText.setText(getActivity().getString(R.string.call_log_via_sim_name_notice,
                    lable));
            mNoticeText.setVisibility(View.VISIBLE);
            mNoticeTextDivider.setVisibility(View.VISIBLE);
        } else {
            mNoticeText.setVisibility(View.GONE);
            mNoticeTextDivider.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAccountInfoUpdate() {
        // clear account cache, and refresh list items
        mAdapter.pauseCache();
        forceToRefreshData();
    }

    @Override
    public void onPreferAccountChanged(String id) {
        forceToRefreshData();
    }
    /// @}

    /**
     * M : force refresh calllog data
     */
    public void forceToRefreshData() {
        mRefreshDataRequired = true;
        /// M: for ALPS01683374
        // refreshData only when CallLogFragment is in foreground
        if (isResumed()) {
            refreshData();
            // refreshData would cause ContactInfoCache.invalidate
            // and cache thread starting would be stopped seldom.
            // we have to call adapter onResume again to start cache thread.
            mAdapter.onResume();
        }
    }

    /**
     * M: [Dialer Global Search] Displays a list of call log entries.
     * CallLogSearch activity reused CallLogFragment.  @{
     */
    // Default null, while in search mode it is not null.
    private String mQueryData = null;

    /**
     * Use it to inject search data.
     * This is the entrance of call log search mode.
     * @param query
     */
    public void setQueryData(String query) {
        mQueryData = query;
        mAdapter.setQueryString(query);
    }

    private void startSearchCalls(String query) {
        Uri uri = Uri.withAppendedPath(DialerConstants.CALLLOG_SEARCH_URI_BASE, query);
        /// support search Voicemail calllog
        uri = VvmUtils.buildVvmAllowedUri(uri);

        mCallLogQueryHandler.fetchSearchCalls(uri);
    }

    private boolean isQueryMode() {
        return !TextUtils.isEmpty(mQueryData) && DialerFeatureOptions.DIALER_GLOBAL_SEARCH;
    }

    private void updateSearchResultIfNeed(Cursor result) {
        if (isQueryMode() && getActivity() instanceof CallLogSearchResultActivity) {
            int count = result != null ? result.getCount() : 0;
            ((CallLogSearchResultActivity) getActivity()).updateSearchResult(count);
        }
    }

    public int getItemCount() {
        //*/ freeme.zhaozehong, 20170810. add null judgement
        if (mAdapter == null) {
            return 0;
        }
        //*/
        return mAdapter.getItemCount();
    }
    /** @} */

    //*/ freeme.zhaozehong, 20170803. for freemeOS, close dialpad
    private OnListFragmentScrolledListener mActivityScrollListener;
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) getActivity();
        } catch (ClassCastException e) {
        }
    }
    //*/

    /** M: To improve scroll performance, add scroll listener to ignore fetching calls while
     *  scroll view @{*/
    private boolean mIsScrolling = false;
    private class ViewScrollListener extends RecyclerView.OnScrollListener {

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            //*/ freeme.zhaozehong, 20170803. for freemeOS, close dialpad
            if (mActivityScrollListener != null) {
                mActivityScrollListener.onListFragmentScrollStateChange(newState);
            }
            //*/
            if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                mIsScrolling = true;
            } else {
                mIsScrolling = false;
                if (mRefreshDataRequired) {
                    refreshData();
                    mAdapter.onResume();
                    Log.d(TAG, " scroll state changed to idle, refresh data");
                }
            }
        }
    }
    /** @}*/
    //*/ freeme.zhaozehong, 18/07/17. filter call logs
    private View.OnClickListener mCalllogsFilterListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.call_logs_all:
                    mCallTypeFilter = CallLogQueryHandler.CALL_TYPE_ALL;
                    break;
                case R.id.call_logs_missed:
                    mCallTypeFilter = Calls.MISSED_TYPE;
                    break;
                case R.id.call_logs_outgoing:
                    mCallTypeFilter = Calls.OUTGOING_TYPE;
                    break;
                case R.id.call_logs_incoming:
                    mCallTypeFilter = Calls.INCOMING_TYPE;
                    break;
                default:
                    break;
            }
            updateCalllogsFilterTitle();
            updateEmptyMessage(mCallTypeFilter);
            fetchCalls();
        }
    };

    private void updateCalllogsFilterTitle() {
        mCalllogsFilterAll.setSelected(mCallTypeFilter == CallLogQueryHandler.CALL_TYPE_ALL);
        mCalllogsFilterMissed.setSelected(mCallTypeFilter == Calls.MISSED_TYPE);
        mCalllogsFilterOutgoing.setSelected(mCallTypeFilter == Calls.OUTGOING_TYPE);
        mCalllogsFilterIncoming.setSelected(mCallTypeFilter == Calls.INCOMING_TYPE);
    }
    //*/
}
