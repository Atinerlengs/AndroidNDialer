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
 * limitations under the License.
 */
package com.android.dialer.list;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Trace;
import android.preference.PreferenceManager;
import android.provider.CallLog.Calls;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.list.ViewPagerTabs;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.calllog.CallLogNotificationsHelper;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.VisualVoicemailCallLogFragment;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.voicemail.VisualVoicemailEnabledChecker;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.android.dialer.widget.ActionBarController;
import com.mediatek.dialer.ext.ExtensionManager;
import java.util.ArrayList;
import java.util.List;
//*/ freeme.zhaozehong, 14/07/17. for freemeOS
import com.freeme.dialer.list.FreemeSpeedDialFragment;
import com.freeme.dialer.utils.FreemeEntranceRequst;
import com.freeme.dialer.calllog.FreemeCalllogFragment;
import com.freeme.dialer.widgets.FreemeViewPager;
//*/
//*/ freeme.zhangjunjian, 20170817,add new menu for the contact you want to display
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.mediatek.contacts.util.AccountTypeUtils;
//*/

/**
 * Fragment that is used as the main screen of the Dialer.
 *
 * Contains a ViewPager that contains various contact lists like the Speed Dial list and the
 * All Contacts list. This will also eventually contain the logic that allows sliding the
 * ViewPager containing the lists up above the search bar and pin it against the top of the
 * screen.
 */
public class ListsFragment extends Fragment
        implements ViewPager.OnPageChangeListener, CallLogQueryHandler.Listener {

    private static final boolean DEBUG = DialtactsActivity.DEBUG;
    private static final String TAG = "ListsFragment";

    /*/ freeme.zhaozehong, 05/06/17. for freemeOS 7.2, ui redesign
    public static final int TAB_INDEX_SPEED_DIAL = 0;
    public static final int TAB_INDEX_HISTORY = 1;
    public static final int TAB_INDEX_ALL_CONTACTS = 2;
    /*/
    public static final int TAB_INDEX_SPEED_DIAL = 2;
    public static final int TAB_INDEX_HISTORY = 0;
    public static final int TAB_INDEX_ALL_CONTACTS = 1;
    //*/
    public static final int TAB_INDEX_VOICEMAIL = 3;

    public static final int TAB_COUNT_DEFAULT = 3;
    public static final int TAB_COUNT_WITH_VOICEMAIL = 4;

    public interface HostInterface {
        public ActionBarController getActionBarController();
    }

    /*/ freeme.zhaozehong, 02/06/17. for freemeOS 7.2, ui redesign, unused
    private ActionBar mActionBar;
    //*/
    private ViewPager mViewPager;
    private ViewPagerTabs mViewPagerTabs;
    private ViewPagerAdapter mViewPagerAdapter;
    private RemoveView mRemoveView;
    private View mRemoveViewContent;

    /*/ freeme.zhaozehong, 06/07/17. for freemeOS
    private SpeedDialFragment mSpeedDialFragment;
    private CallLogFragment mHistoryFragment;
    /*/
    private FreemeSpeedDialFragment mSpeedDialFragment;
    private FreemeCalllogFragment mHistoryFragment;
    //*/
    private AllContactsFragment mAllContactsFragment;
    private CallLogFragment mVoicemailFragment;

    private SharedPreferences mPrefs;
    private boolean mHasActiveVoicemailProvider;
    private boolean mHasFetchedVoicemailStatus;
    private boolean mShowVoicemailTabAfterVoicemailStatusIsFetched;

    private VoicemailStatusHelper mVoicemailStatusHelper;
    private ArrayList<OnPageChangeListener> mOnPageChangeListeners =
            new ArrayList<OnPageChangeListener>();

    private String[] mTabTitles;
    private int[] mTabIcons;

    /**
     * The position of the currently selected tab.
     */
    private int mTabIndex = TAB_INDEX_SPEED_DIAL;
    private CallLogQueryHandler mCallLogQueryHandler;

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragments = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
            for (int i = 0; i < TAB_COUNT_WITH_VOICEMAIL; i++) {
                mFragments.add(null);
            }
        }

        @Override
        public long getItemId(int position) {
            return getRtlPosition(position);
        }

        @Override
        public Fragment getItem(int position) {
            switch (getRtlPosition(position)) {
                /*/ freeme.zhaozehong, 20170818. for freemeOS, redesign dialpad
                case TAB_INDEX_SPEED_DIAL:
                    mSpeedDialFragment = new SpeedDialFragment();
                    return mSpeedDialFragment;
                case TAB_INDEX_HISTORY:
                    mHistoryFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL);
                    /// M: [Call Log Account Filter] don't use Account Filter when
                    // viewing recents log
                    mHistoryFragment.setAccountFilterState(false);
                    return mHistoryFragment;
                /*/
                case TAB_INDEX_SPEED_DIAL:
                    mSpeedDialFragment = new FreemeSpeedDialFragment();
                    return mSpeedDialFragment;
                case TAB_INDEX_HISTORY:
                    mHistoryFragment = new FreemeCalllogFragment();
                    return mHistoryFragment;
                //*/
                case TAB_INDEX_ALL_CONTACTS:
                    mAllContactsFragment = new AllContactsFragment();
                    return mAllContactsFragment;
                case TAB_INDEX_VOICEMAIL:
                    mVoicemailFragment = new VisualVoicemailCallLogFragment();
                    /// M: [Call Log Account Filter] don't use Account Filter when
                    // viewing logs in main activity
                    mVoicemailFragment.setAccountFilterState(false);
                    return mVoicemailFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public Fragment instantiateItem(ViewGroup container, int position) {
            // On rotation the FragmentManager handles rotation. Therefore getItem() isn't called.
            // Copy the fragments that the FragmentManager finds so that we can store them in
            // instance variables for later.
            final Fragment fragment =
                    (Fragment) super.instantiateItem(container, position);
            /*/ freeme.zhaozehong, 06/07/17. for freemeOS
            if (fragment instanceof SpeedDialFragment) {
                mSpeedDialFragment = (SpeedDialFragment) fragment;
            } else if (fragment instanceof CallLogFragment && position == TAB_INDEX_HISTORY) {
                mHistoryFragment = (CallLogFragment) fragment;
            /*/
            if (fragment instanceof FreemeSpeedDialFragment) {
                mSpeedDialFragment = (FreemeSpeedDialFragment) fragment;
            } else if (fragment instanceof FreemeCalllogFragment && position == TAB_INDEX_HISTORY) {
                mHistoryFragment = (FreemeCalllogFragment) fragment;
                mHistoryFragment.resetDialpadStatus();
            //*/
            } else if (fragment instanceof AllContactsFragment) {
                mAllContactsFragment = (AllContactsFragment) fragment;
            } else if (fragment instanceof CallLogFragment && position == TAB_INDEX_VOICEMAIL) {
                mVoicemailFragment = (CallLogFragment) fragment;
            }
            mFragments.set(position, fragment);
            return fragment;
        }

        /**
         * When {@link android.support.v4.view.PagerAdapter#notifyDataSetChanged} is called,
         * this method is called on all pages to determine whether they need to be recreated.
         * When the voicemail tab is removed, the view needs to be recreated by returning
         * POSITION_NONE. If notifyDataSetChanged is called for some other reason, the voicemail
         * tab is recreated only if it is active. All other tabs do not need to be recreated
         * and POSITION_UNCHANGED is returned.
         */
        @Override
        public int getItemPosition(Object object) {
            return !mHasActiveVoicemailProvider &&
                    mFragments.indexOf(object) == TAB_INDEX_VOICEMAIL ? POSITION_NONE :
                    POSITION_UNCHANGED;
        }

        @Override
        public int getCount() {
            return mHasActiveVoicemailProvider ? TAB_COUNT_WITH_VOICEMAIL : TAB_COUNT_DEFAULT;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Trace.beginSection(TAG + " onCreate");
        super.onCreate(savedInstanceState);

        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mHasFetchedVoicemailStatus = false;

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mHasActiveVoicemailProvider = mPrefs.getBoolean(
                VisualVoicemailEnabledChecker.PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER, false);

        Trace.endSection();
    }

    @Override
    public void onResume() {
        Trace.beginSection(TAG + " onResume");
        super.onResume();

        /*/ freeme.zhaozehong, 02/06/17. for freemeOS 7.2, ui redesign, unused
        mActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        //*/
        if (getUserVisibleHint()) {
            sendScreenViewForCurrentPosition();
        }

        //*/ freeme.zhangjunjian, 20170817,add new menu for the contact you want to display
        displayContactsName();
        //*/

        // Fetch voicemail status to determine if we should show the voicemail tab.
        mCallLogQueryHandler =
                new CallLogQueryHandler(getActivity(), getActivity().getContentResolver(), this);
        mCallLogQueryHandler.fetchVoicemailStatus();
        mCallLogQueryHandler.fetchMissedCallsUnreadCount();
        Trace.endSection();
    }

    //*/ freeme.zhangjunjian, 20170817,add new menu for the contact you want to display
    private void displayContactsName(){
        ContactListFilter filter = ContactListFilter.restoreDefaultPreferences(getActivity());
        String contactDisplayName="";
        if (filter != null) {
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
                contactDisplayName = getResources().getString(R.string.tab_all_contacts);
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
                contactDisplayName = getResources().getString(R.string.freeme_list_filter_customize);
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_STARRED) {
                contactDisplayName =  getResources().getString(R.string.list_filter_all_starred);
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY) {
                contactDisplayName = getResources().getString(R.string.list_filter_phones);
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                contactDisplayName = getResources().getString(R.string.list_filter_single);
            } else if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                final AccountType accountType =
                        AccountTypeManager.getInstance(getActivity()).getAccountType(filter.accountType, filter.dataSet);
                contactDisplayName = String.valueOf(accountType.getDisplayLabel(getContext()));
            }
        } else {
            contactDisplayName = getResources().getString(R.string.contactsList);
        }

        if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT
                && !AccountType.ACCOUNT_TYPE_LOCAL_PHONE.equals(filter.accountType)
                || filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            StringBuffer sb = new StringBuffer(contactDisplayName);
            int slot = AccountTypeUtils.getSlotByAccountName(getContext(), filter.accountName);
            switch (slot) {
                case 0:
                    sb.append("1");
                    break;
                case 1:
                    sb.append("2");
                    break;
                default:
                    break;
            }
            sb.append(getResources().getString(R.string.freeme_display_contacts_name));
            mTabTitles[TAB_INDEX_ALL_CONTACTS] = sb.toString();
        } else {
            mTabTitles[TAB_INDEX_ALL_CONTACTS] = contactDisplayName;
        }
        mViewPagerAdapter.notifyDataSetChanged();
    }
    //*/

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Trace.beginSection(TAG + " onCreateView");
        Trace.beginSection(TAG + " inflate view");
        final View parentView = inflater.inflate(R.layout.lists_fragment, container, false);
        Trace.endSection();
        Trace.beginSection(TAG + " setup views");
        mViewPager = (ViewPager) parentView.findViewById(R.id.lists_pager);
        mViewPagerAdapter = new ViewPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOffscreenPageLimit(TAB_COUNT_WITH_VOICEMAIL - 1);
        mViewPager.setOnPageChangeListener(this);
        /*/ freeme.zhaozehong, 24/06/17. for freeme 7.2, init show fragment
        showTab(TAB_INDEX_SPEED_DIAL);
        /*/
        if (getActivity() instanceof DialtactsActivity) {
            FreemeEntranceRequst feq = ((DialtactsActivity) getActivity()).getFreemeEntriceRequst();
            if (feq != null) {
                if (!feq.isRecreatedInstance()) {
                    int code = ((DialtactsActivity) getActivity()).getFreemeEntriceRequst().getEntranceCode();
                    if (code == FreemeEntranceRequst.ENTRANCE_CONTACTS) {
                        showTab(TAB_INDEX_ALL_CONTACTS);
                    } else {
                        showTab(TAB_INDEX_HISTORY);
                    }
                }
            }
        }
        //*/

        mTabTitles = new String[TAB_COUNT_WITH_VOICEMAIL];
        /*/ freeme.zhaozehong, 29/06/17.
        mTabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.tab_speed_dial);
        mTabTitles[TAB_INDEX_HISTORY] = getResources().getString(R.string.tab_history);
        /*/
        mTabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.contactsFavoritesLabel);
        mTabTitles[TAB_INDEX_HISTORY] = getResources().getString(R.string.freeme_tab_call_label);
        //*/
        mTabTitles[TAB_INDEX_ALL_CONTACTS] = getResources().getString(R.string.tab_all_contacts);
        mTabTitles[TAB_INDEX_VOICEMAIL] = getResources().getString(R.string.tab_voicemail);

        /*/ freeme.zhaozehong, 02/06/17. for freemeOS 7.2, ui redesign, do not show icon
        mTabIcons = new int[TAB_COUNT_WITH_VOICEMAIL];
        mTabIcons[TAB_INDEX_SPEED_DIAL] = R.drawable.ic_grade_24dp;
        mTabIcons[TAB_INDEX_HISTORY] = R.drawable.ic_schedule_24dp;
        mTabIcons[TAB_INDEX_ALL_CONTACTS] = R.drawable.ic_people_24dp;
        mTabIcons[TAB_INDEX_VOICEMAIL] = R.drawable.ic_voicemail_24dp;

        mViewPagerTabs = (ViewPagerTabs) parentView.findViewById(R.id.lists_pager_header);
        mViewPagerTabs.configureTabIcons(mTabIcons);
        /*/
        mViewPagerTabs = (ViewPagerTabs) parentView.findViewById(R.id.lists_pager_header);
        ((DialtactsActivity) getActivity()).getFreemeTabLayout().setupWithViewPager(mViewPager);
        //*/
        mViewPagerTabs.setViewPager(mViewPager);
        addOnPageChangeListener(mViewPagerTabs);

        mRemoveView = (RemoveView) parentView.findViewById(R.id.remove_view);
        mRemoveViewContent = parentView.findViewById(R.id.remove_view_content);

        /// M: [For Plugin Customization] @{
        ExtensionManager.getInstance().getDialPadExtension().customizeDefaultTAB(this);
        /// @}

        Trace.endSection();
        Trace.endSection();
        return parentView;
    }

    public void addOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
        if (!mOnPageChangeListeners.contains(onPageChangeListener)) {
            mOnPageChangeListeners.add(onPageChangeListener);
        }
    }

    //*/ freeme.zhangjunjian, 20170817,add new menu for the contact you want to display
    public void setFilter(ContactListFilter filter) {
        if (mAllContactsFragment != null) {
            mAllContactsFragment.setFilter(filter);
        }
    }
    //*/

    /**
     * Shows the tab with the specified index. If the voicemail tab index is specified, but the
     * voicemail status hasn't been fetched, it will try to show the tab after the voicemail status
     * has been fetched.
     */
    public void showTab(int index) {
        if (index == TAB_INDEX_VOICEMAIL) {
            if (mHasActiveVoicemailProvider) {
                //*/ freeme.zhaozehong, 24/06/17. for freemeOS 7.2, set currtent page
                mViewPager.setCurrentItem(getRtlPosition(TAB_INDEX_VOICEMAIL), false);
                onPageSelected(index);
                /*/
                mViewPager.setCurrentItem(getRtlPosition(TAB_INDEX_VOICEMAIL));
                //*/
            } else if (!mHasFetchedVoicemailStatus) {
                // Try to show the voicemail tab after the voicemail status returns.
                mShowVoicemailTabAfterVoicemailStatusIsFetched = true;
            }
        } else if (index < getTabCount()){
            //*/ freeme.zhaozehong, 24/06/17. for freemeOS 7.2, set currtent page
            mViewPager.setCurrentItem(getRtlPosition(index), false);
            onPageSelected(index);
            /*/
            mViewPager.setCurrentItem(getRtlPosition(index));
            //*/
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        mTabIndex = getRtlPosition(position);

        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrolled(position, positionOffset,
                    positionOffsetPixels);
        }
    }

    @Override
    public void onPageSelected(int position) {
        mTabIndex = getRtlPosition(position);

        //*/ freeme.zhaozehong, 20170913. close input method
        if (mAllContactsFragment != null) {
            mAllContactsFragment.hideSoftKeyboard();
        }
        //*/

        // Show the tab which has been selected instead.
        mShowVoicemailTabAfterVoicemailStatusIsFetched = false;

        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageSelected(position);
        }
        sendScreenViewForCurrentPosition();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrollStateChanged(state);
        }
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        mHasFetchedVoicemailStatus = true;

        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }

        // Update mHasActiveVoicemailProvider, which controls the number of tabs displayed.
        boolean hasActiveVoicemailProvider =
                mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor) > 0;
        if (hasActiveVoicemailProvider != mHasActiveVoicemailProvider) {
            mHasActiveVoicemailProvider = hasActiveVoicemailProvider;
            mViewPagerAdapter.notifyDataSetChanged();

            if (hasActiveVoicemailProvider) {
                mViewPagerTabs.updateTab(TAB_INDEX_VOICEMAIL);
            } else {
                mViewPagerTabs.removeTab(TAB_INDEX_VOICEMAIL);
                removeVoicemailFragment();
            }

            mPrefs.edit()
                  .putBoolean(VisualVoicemailEnabledChecker.PREF_KEY_HAS_ACTIVE_VOICEMAIL_PROVIDER,
                          hasActiveVoicemailProvider)
                  .commit();
        }

        if (hasActiveVoicemailProvider) {
            mCallLogQueryHandler.fetchVoicemailUnreadCount();
        }

        if (mHasActiveVoicemailProvider && mShowVoicemailTabAfterVoicemailStatusIsFetched) {
            mShowVoicemailTabAfterVoicemailStatusIsFetched = false;
            showTab(TAB_INDEX_VOICEMAIL);
        }
    }

    @Override
    public void onVoicemailUnreadCountFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing() || cursor == null) {
            return;
        }

        int count = 0;
        try {
            count = cursor.getCount();
        } finally {
            cursor.close();
        }

        mViewPagerTabs.setUnreadCount(count, TAB_INDEX_VOICEMAIL);
        mViewPagerTabs.updateTab(TAB_INDEX_VOICEMAIL);
    }

    @Override
    public void onMissedCallsUnreadCountFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing() || cursor == null) {
            return;
        }

        int count = 0;
        try {
            count = cursor.getCount();
        } finally {
            cursor.close();
        }

        mViewPagerTabs.setUnreadCount(count, TAB_INDEX_HISTORY);
        mViewPagerTabs.updateTab(TAB_INDEX_HISTORY);
    }

    @Override
    public boolean onCallsFetched(Cursor statusCursor) {
        // Return false; did not take ownership of cursor
        return false;
    }

    public int getCurrentTabIndex() {
        return mTabIndex;
    }

    /**
     * External method to update unread count because the unread count changes when the user
     * expands a voicemail in the call log or when the user expands an unread call in the call
     * history tab.
     */
    public void updateTabUnreadCounts() {
        if (mCallLogQueryHandler != null) {
            mCallLogQueryHandler.fetchMissedCallsUnreadCount();
            if (mHasActiveVoicemailProvider) {
                mCallLogQueryHandler.fetchVoicemailUnreadCount();
            }
        }
    }

    /**
     * External method to mark all missed calls as read.
     */
    public void markMissedCallsAsReadAndRemoveNotifications() {
        if (mCallLogQueryHandler != null) {
            mCallLogQueryHandler.markMissedCallsAsRead();
            CallLogNotificationsHelper.removeMissedCallNotifications(getActivity());
        }
    }


    public void showRemoveView(boolean show) {
        mRemoveViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
        mRemoveView.setAlpha(show ? 0 : 1);
        mRemoveView.animate().alpha(show ? 1 : 0).start();
    }

    public boolean shouldShowActionBar() {
        // TODO: Update this based on scroll state.
        /*/ freeme.zhaozehong, 02/06/17. for freemeOS 7.2, ui redesign
        return mActionBar != null;
        /*/
        return false;
        //*/
    }

    /*/ freeme.zhaozehong, 06/07/17. for freemeOS
    public SpeedDialFragment getSpeedDialFragment() {
        return mSpeedDialFragment;
    }
    /*/
    public FreemeSpeedDialFragment getSpeedDialFragment() {
        return mSpeedDialFragment;
    }
    //*/

    public RemoveView getRemoveView() {
        return mRemoveView;
    }

    public int getTabCount() {
        return mViewPagerAdapter.getCount();
    }

    private int getRtlPosition(int position) {
        if (DialerUtils.isRtl()) {
            return mViewPagerAdapter.getCount() - 1 - position;
        }
        return position;
    }

    public void sendScreenViewForCurrentPosition() {
        if (!isResumed()) {
            return;
        }

        int screenType;
        switch (getCurrentTabIndex()) {
            case TAB_INDEX_SPEED_DIAL:
                screenType = ScreenEvent.SPEED_DIAL;
                break;
            case TAB_INDEX_HISTORY:
                screenType = ScreenEvent.CALL_LOG;
                break;
            case TAB_INDEX_ALL_CONTACTS:
                screenType = ScreenEvent.ALL_CONTACTS;
                break;
            case TAB_INDEX_VOICEMAIL:
                screenType = ScreenEvent.VOICEMAIL_LOG;
            default:
                return;
        }
        Logger.logScreenView(screenType, getActivity());
    }

    private void removeVoicemailFragment() {
        if (mVoicemailFragment != null) {
            getChildFragmentManager().beginTransaction().remove(mVoicemailFragment)
                    .commitAllowingStateLoss();
            mVoicemailFragment = null;
        }
    }

    /// M: [Multi-Delete] For CallLog delete @{
    @Override
    public void onCallsDeleted() {
        // Do nothing
    }
    /// @}

    //*/ freeme.zhaozehong, 12/07/17. for freemeOS, search contacts
    public boolean inSearchContactorMode() {
        if (mAllContactsFragment != null) {
            return mAllContactsFragment.inSearchContactorMode();
        } else {
            return false;
        }
    }

    public void clearSearchContactorFocus() {
        if (mAllContactsFragment != null)
            mAllContactsFragment.clearSearchContactorFocus();
    }
    //*/

    //*/ freeme.zhaozehong, 20170810. for multi delete call logs
    public int getCallLogsCount() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.getItemCount();
        } else {
            return 0;
        }
    }
    //*/

    //*/ freeme.zhaozehong, 20170818. for freemeOS, redesign dialpad
    public void showDialpadFragment(boolean animate) {
        if (mHistoryFragment != null) {
            mHistoryFragment.showDialpadFragment(animate);
        }
    }

    public void onDialpadShown() {
        if (mHistoryFragment != null) {
            mHistoryFragment.onDialpadShown();
        }
    }

    public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
        if (mHistoryFragment != null) {
            mHistoryFragment.hideDialpadFragment(animate, clearDialpad);
        }
    }

    public boolean isDialpadVisible() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.isDialpadVisible();
        }
        return false;
    }

    public void exitSearchUi() {
        if (mHistoryFragment != null) {
            mHistoryFragment.exitSearchUi();
        }
    }

    public boolean onBackPress() {
        if (mTabIndex == TAB_INDEX_HISTORY && mHistoryFragment != null) {
            return mHistoryFragment.onBackPress();
        }
        return false;
    }

    public int getDialpadHeight() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.getDialpadHeight();
        }
        return 0;
    }

    public boolean isInSearchUi() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.isInSearchUi();
        }
        return true;
    }

    public boolean isDialpadShow() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.isDialpadShow();
        }
        return false;
    }

    public String getSearchQuery() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.getSearchQuery();
        }
        return "";
    }

    public String getDialpadQuery() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.getDialpadQuery();
        }
        return "";
    }

    public void commitDialpadFragmentHide() {
        if (mHistoryFragment != null) {
            mHistoryFragment.commitDialpadFragmentHide();
        }
    }

    public boolean isShowingPermissionRequest() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.isShowingPermissionRequest();
        }
        return false;
    }

    public boolean isDialpadSlideOutStarting() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.isDialpadSlideOutStarting();
        }
        return false;
    }

    public void hideDialpadAndSearchUi() {
        if (mHistoryFragment != null) {
            mHistoryFragment.hideDialpadAndSearchUi();
        }
    }

    public void setClearSearchOnPause(boolean clearSearch) {
        if (mHistoryFragment != null) {
            mHistoryFragment.setClearSearchOnPause(clearSearch);
        }
    }

    public boolean getClearSearchOnPause() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.getClearSearchOnPause();
        }
        return false;
    }

    public void onDialpadQueryChanged(String query, String normalizedQuery) {
        if (mHistoryFragment != null) {
            mHistoryFragment.onDialpadQueryChanged(query, normalizedQuery);
        }
    }

    public void setViewPagerIsCanScroll(boolean isCallScroll) {
        if (mViewPager != null && mViewPager instanceof FreemeViewPager) {
            ((FreemeViewPager) mViewPager).setIsCanScroll(isCallScroll);
        }
    }
    //*/

    //*/ freeme.zhaozehong, 20170908. for operator dialpad
    private boolean mIsFirstLaunch;

    public void setIsFirstLaunch(boolean isFirstLaunch) {
        this.mIsFirstLaunch = isFirstLaunch;
    }

    public boolean isFirstLaunch() {
        return mIsFirstLaunch;
    }

    private boolean mShowDialpadOnResume;

    public boolean isShowDialpadOnResume() {
        return mShowDialpadOnResume;
    }

    public void setShowDialpadOnResume(boolean showDialpadOnResume) {
        this.mShowDialpadOnResume = showDialpadOnResume;
    }

    private boolean mIsStartFromNewIntent;

    public void setStartedFromNewIntent(boolean isStartFromNewIntent) {
        this.mIsStartFromNewIntent = isStartFromNewIntent;
    }

    public boolean isStartFromNewIntent() {
        return mIsStartFromNewIntent;
    }

    private String mPendingSearchViewQuery;

    public String getPendingSearchViewQuery() {
        return mPendingSearchViewQuery;
    }

    public void setPendingSearchViewQuery(String pendingSearchViewQuery) {
        this.mPendingSearchViewQuery = pendingSearchViewQuery;
    }

    public void cleanPendingSearchViewQuery(){
        this.mPendingSearchViewQuery = null;
        if (getActivity() instanceof DialtactsActivity) {
            ((DialtactsActivity) getActivity()).clearPendingSearchViewQuery();
        }
    }
    //*/
}
