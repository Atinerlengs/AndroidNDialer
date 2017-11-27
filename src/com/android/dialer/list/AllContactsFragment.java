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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.QuickContact;
import android.support.v13.app.FragmentCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
//*/ freeme.zhaozehong, 12/07/17. for freemeOS, search contacts
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
//*/
//*/ freeme.zhaozehong, 13/07/17. for freemeOS, show content menu
import android.content.ContentResolver;
import android.content.ContentValues;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.MenuItem;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.util.Constants;
import com.android.dialer.util.PhoneNumberUtil;
import com.android.incallui.Call;
import com.freeme.dialer.contacts.FreemeContactDeletionInteraction;
import com.mediatek.contacts.simcontact.SimCardUtils;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;
import android.content.CursorLoader;
import com.android.contacts.common.list.ProfileAndContactsLoader;
import android.graphics.Rect;
import android.net.Uri;
import android.widget.Button;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.provider.ContactsContract;
//*/

/**
 * Fragments to show all contacts with phone numbers.
 */
public class AllContactsFragment extends ContactEntryListFragment<ContactEntryListAdapter>
        implements OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback {

    /** M: request full group permissions instead of READ_CALL_LOG,
     * Because MTK changed the group permissions granting logic.
     */
    private static final String[] READ_CONTACTS = PermissionsUtil.CONTACTS_FULL_GROUP;

    private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;

    private EmptyContentView mEmptyListView;

    /**
     * Listen to broadcast events about permissions in order to be notified if the READ_CONTACTS
     * permission is granted via the UI in another fragment.
     */
    private BroadcastReceiver mReadContactsPermissionGrantedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadData();
        }
    };

    public AllContactsFragment() {
        setQuickContactEnabled(false);
        setAdjustSelectionBoundsEnabled(true);
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setDarkTheme(false);
        /*/ freeme.zhaozehong, 10/07/17. for freemeOS
        setVisibleScrollbarEnabled(true);
        /*/
        setVisibleScrollbarEnabled(false);
        setVisibleIndexScrollbarEnabled(true);
        setIncludeProfile(true);
        //*/
    }
    //*/freeme.zhangjunjian, 20171103. dispaly my card
    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new ProfileAndContactsLoader(context);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        if (!getAdapter().hasProfile()) {
            addEmptyUserProfileHeader(inflater);
        }
    }
    //*/

    //*/ freeme.zhaozehong, 20170927. for hide index in multi window mode
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        boolean isInMultiWindowMode = getActivity().isInMultiWindowMode();
        setVisibleIndexScrollbarEnabled(!isInMultiWindowMode);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        setVisibleIndexScrollbarEnabled(!isInMultiWindowMode);
    }
    //*/

    @Override
    public void onViewCreated(View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mEmptyListView = (EmptyContentView) view.findViewById(R.id.empty_list_view);
        mEmptyListView.setImage(R.drawable.empty_contacts);
        /*/ freeme.zhangjunjian, 20170815, marked wrong when contacts is empty
        mEmptyListView.setDescription(R.string.all_contacts_empty);
        mEmptyListView.setActionClickedListener(this);
        getListView().setEmptyView(mEmptyListView);
        /*/
        mEmptyListView.setActionClickedListener(this);
        //*/
        mEmptyListView.setVisibility(View.GONE);

        //*/ freeme.zhaozehong, 10/07/17. for freemeOS, search contacts view
        searchContactView = (EditText) view.findViewById(R.id.freeme_search_edit);
        searchContactView.setInputType(EditorInfo.TYPE_CLASS_TEXT
                | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        searchContactView.addTextChangedListener(new SearchTextWatcher());
        searchContactView.setOnFocusChangeListener(new SearchViewFocuseChage());
        mDeleteAllEdit = (ImageView) view.findViewById(R.id.freeme_remove_edit);
        mDeleteAllEdit.setVisibility(View.INVISIBLE);
        mDeleteAllEdit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                searchContactView.setText(null);
            }
        });
        //*/
        //*/ freeme.zhaozehong, 12/07/17. show context menu
        getListView().setOnCreateContextMenuListener(this);
        //*/
        //*/ freeme.zhaozehong, 20170817. for freemeOS, add create contact button (redesign dialpad)
        ImageButton floatingActionButton = (ImageButton) view.findViewById(R.id.floating_action_button);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialerUtils.startActivityWithErrorToast(getContext(),
                        IntentUtil.getNewContactIntent(), R.string.add_contact_not_available);
            }
        });

        floatingActionButton.setImageDrawable(
                getResources().getDrawable(R.drawable.ic_person_add_24dp));
        floatingActionButton.setContentDescription(
                getResources().getString(R.string.search_shortcut_create_new_contact));
        //*/

        ViewUtil.addBottomPaddingToListViewForFab(getListView(), getResources());
    }

    @Override
    public void onStart() {
        super.onStart();
        PermissionsUtil.registerPermissionReceiver(getActivity(),
                mReadContactsPermissionGrantedReceiver, READ_CONTACTS);
    }

    @Override
    public void onStop() {
        PermissionsUtil.unregisterPermissionReceiver(getActivity(),
                mReadContactsPermissionGrantedReceiver);
        super.onStop();
    }

    @Override
    protected void startLoading() {
        if (PermissionsUtil.hasPermission(getActivity(), READ_CONTACTS)) {
            super.startLoading();
            /*/ freeme.zhangjunjian, 20170815, marked wrong when contacts is empty
            mEmptyListView.setDescription(R.string.all_contacts_empty);
            mEmptyListView.setActionLabel(R.string.all_contacts_empty_add_contact_action);
            //*/
            /// M: Add it to reload data.
            mHasContactsPermission = true;
        } else {
            mEmptyListView.setDescription(R.string.permission_no_contacts);
            mEmptyListView.setActionLabel(R.string.permission_single_turn_on);
            mEmptyListView.setVisibility(View.VISIBLE);
            /// M: Add it to reload data.
            mHasContactsPermission = false;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);

        if (data == null || data.getCount() == 0) {
            mEmptyListView.setVisibility(View.VISIBLE);
            //*/ freeme.zhangjunjian,20170808,marked wrong when contacts is empty
            if(TextUtils.isEmpty(searchContactView.getText())){
                mEmptyListView.setDescription(R.string.all_contacts_empty);
                mEmptyListView.setActionLabel(R.string.all_contacts_empty_add_contact_action);
            }else{
                mEmptyListView.setDescription(R.string.freeme_no_match_contact);
                mEmptyListView.setActionLabel(EmptyContentView.NO_LABEL);
            }
            //*/
        }
        //*/ freeme.zhangjunjian, 20170815, icon bounce when search contacts
        else {
            mEmptyListView.setVisibility(View.GONE);
        }
        //*/
        //*/ freeme.liqiang, 20170925, display numbers of contacts
        if (TextUtils.isEmpty(searchContactView.getText())) {
            showContactsNumbers();
        }
        //*/
    }

    //*/freeme.zhangjunjian, 20171102. display my card
    private FrameLayout mProfileHeaderContainer;
    private View mProfileHeader;
    private Button mProfileMessage;
    private TextView mProfileTitle;

    private void showContactsNumbers() {
        int count = getListView().getAdapter().getCount() - 1;//minus 1 because of the header view
        if (getAdapter().hasProfile()) {
            count -= 1;
        }
        searchContactView.setHint(getString(R.string.freeme_hint_findContacts, count));
    }

    protected void setProfileHeader() {
        int visable = !getAdapter().hasProfile() && !isSearchMode() ? View.VISIBLE : View.GONE;
        mProfileHeaderContainer.setVisibility(visable);
        mProfileHeader.setVisibility(visable);
        mProfileTitle.setVisibility(visable);
        mProfileMessage.setVisibility(visable);
    }

    private void addEmptyUserProfileHeader(LayoutInflater inflater) {
        ListView list = getListView();
        mProfileHeader = inflater.inflate(R.layout.freeme_user_profile_header, null, false);
        mProfileTitle = (TextView) mProfileHeader.findViewById(R.id.freeme_profile_title);
        mProfileHeaderContainer = new FrameLayout(inflater.getContext());
        mProfileHeaderContainer.addView(mProfileHeader);
        list.addHeaderView(mProfileHeaderContainer, null, false);

        // Add a button with a message inviting the user to create a local profile
        mProfileMessage = (Button) mProfileHeader.findViewById(R.id.freeme_user_profile_button);
        mProfileMessage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                {
                    Intent intent = new Intent(Intent.ACTION_INSERT,
                            ContactsContract.Contacts.CONTENT_URI);
                    intent.putExtra("newLocalProfile", true);
                    startActivity(intent);
                }
            }
        });
    }
    //*/

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        final DefaultContactListAdapter adapter = new DefaultContactListAdapter(getActivity()) {
            @Override
            protected void bindView(View itemView, int partition, Cursor cursor, int position) {
                super.bindView(itemView, partition, cursor, position);
                itemView.setTag(this.getContactUri(partition, cursor));
            }
        };
        adapter.setDisplayPhotos(true);
        /*/ freeme.zhangjunjian, 20170817,add new menu for the contact you want to display
        adapter.setFilter(ContactListFilter.createFilterWithType(
                ContactListFilter.FILTER_TYPE_DEFAULT));
        /*/
        adapter.setFilter(ContactListFilter.restoreDefaultPreferences(getActivity()));
        //*/
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        /*/ freeme.zhaozehong, 12/07/17. for freemeOS
        return inflater.inflate(R.layout.all_contacts_fragment, null);
        /*/
        return inflater.inflate(R.layout.freeme_all_contacts_fragment, null);
        //*/
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Uri uri = (Uri) view.getTag();
        if (uri != null) {
            if (CompatUtils.hasPrioritizedMimeType()) {
                QuickContact.showQuickContact(getContext(), view, uri, null,
                        Phone.CONTENT_ITEM_TYPE);
            } else {
                QuickContact.showQuickContact(getActivity(), view, uri, QuickContact.MODE_LARGE,
                        null);
            }
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        // Do nothing. Implemented to satisfy ContactEntryListFragment.
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (!PermissionsUtil.hasPermission(activity, READ_CONTACTS)) {
          FragmentCompat.requestPermissions(this, /*M:*/READ_CONTACTS,
              READ_CONTACTS_PERMISSION_REQUEST_CODE);
        } else {
            // Add new contact
            DialerUtils.startActivityWithErrorToast(activity, IntentUtil.getNewContactIntent(),
                    R.string.add_contact_not_available);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length >= 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                // Force a refresh of the data since we were missing the permission before this.
                reloadData();
            }
        }
    }

    /// M: In the multi-window mode, the permission changed, but the onStop(),onStart()
    /// never be called. Trigger reloading in onResume() @{
    private boolean mHasContactsPermission = false;

    @Override
    public void onResume() {
        super.onResume();
        //*/ freeme.zhaozehong, 20170927. for hide index in multi window mode
        boolean isInMultiWindowMode = getActivity().isInMultiWindowMode();
        setVisibleIndexScrollbarEnabled(!isInMultiWindowMode);
        //*/
        boolean hasContactsPermisssion =
                PermissionsUtil.hasPermission(getActivity(), READ_CONTACTS);
        if (!mHasContactsPermission && hasContactsPermisssion) {
            // We didn't have the permission before, and now we do. Force reload the contacts.
            reloadData();
        }
    }
    /// @}

    //*/ freeme.zhaozehong, 12/07/17. for freemeOS, search contactor
    public EditText searchContactView;
    private String mQueryString;
    private boolean isSearchContactorMode;
    private ImageView mDeleteAllEdit;

    private class SearchTextWatcher implements TextWatcher {

        @Override
        public void onTextChanged(CharSequence queryString, int start, int before, int count) {
            if (queryString.equals(mQueryString)) {
                return;
            }
            mQueryString = queryString.toString();
            if (TextUtils.isEmpty(searchContactView.getText())) {
                mDeleteAllEdit.setVisibility(View.INVISIBLE);
                setIncludeProfile(true);
            } else {
                mDeleteAllEdit.setVisibility(View.VISIBLE);
                setIncludeProfile(false);
            }
            setQueryString(mQueryString, true);

        }

        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
    }

    class SearchViewFocuseChage implements View.OnFocusChangeListener {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            isSearchContactorMode = hasFocus;
        }
    }

    public boolean inSearchContactorMode() {
        return isSearchContactorMode;
    }

    public void clearSearchContactorFocus() {
        searchContactView.clearFocus();
        searchContactView.setText(null);
    }
    //*/

    //*/ freeme.zhaozehong, 12/07/17. show content menu
    private static final int MENU_ITEM_VIEW_CONTACT = 1;
    private static final int MENU_ITEM_CALL = 2;
    private static final int MENU_ITEM_SEND_SMS = 3;
    private static final int MENU_ITEM_EDIT = 4;
    private static final int MENU_ITEM_DELETE = 5;
    private static final int MENU_ITEM_TOGGLE_STAR = 6;
    private static final int MENU_ITEM_IP_CALL = 7;
    private static final int MENU_ITEM_REMOVE_FROM_STARRED = 8;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            return;
        }
        //headView is exist so we need minus 1
        Cursor cursor = (Cursor) getAdapter().getItem(info.position - 1);
        if (cursor == null) {
            return;
        }

        if(cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.IS_USER_PROFILE)) == 1){
            return;
        }
        ContentResolver cr = getActivity().getContentResolver();
        int indicate = cursor.getInt(
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.INDICATE_PHONE_SIM));
        long contact_id = cursor.getLong(ContactListAdapter.ContactQuery.CONTACT_ID);
        //headView is exist so we need minus 1
        Uri mContactUri = getContactUri(info.position - 1);
        long raw_contact_id = ContactsUtils.queryForRawContactId(cr, contact_id);
        String phoneNumber = ContactsUtils.queryPhoneNumber(cr, raw_contact_id);
        String header = cursor.getString(ContactListAdapter.ContactQuery.CONTACT_DISPLAY_NAME);
        if (TextUtils.isEmpty(header)) {
            menu.setHeaderTitle(R.string.unknown);
        } else {
            menu.setHeaderTitle(header);
        }

        if (PhoneNumberUtil.canPlaceCallsTo(phoneNumber, CallLog.Calls.PRESENTATION_ALLOWED)) {
            menu.add(ContextMenu.NONE, MENU_ITEM_CALL, ContextMenu.NONE,
                    R.string.freeme_menu_item_call_contacts)
                    .setIntent(IntentUtil.getCallIntent(IntentUtil.getCallUri(phoneNumber),
                            Call.LogState.INITIATION_QUICK_CONTACTS,
                            Constants.DIAL_NUMBER_INTENT_NORMAL));
            menu.add(ContextMenu.NONE, MENU_ITEM_IP_CALL, ContextMenu.NONE, R.string.call_ip_dial)
                    .setIntent(IntentUtil.getCallIntent(IntentUtil.getCallUri(phoneNumber),
                            Call.LogState.INITIATION_QUICK_CONTACTS,
                            Constants.DIAL_NUMBER_INTENT_IP));
            menu.add(ContextMenu.NONE, MENU_ITEM_SEND_SMS, ContextMenu.NONE, R.string.send_message)
                    .setIntent(IntentUtil.getSendSmsIntent(phoneNumber));
        }
        menu.add(ContextMenu.NONE, MENU_ITEM_VIEW_CONTACT, ContextMenu.NONE,
                R.string.description_view_contact_detail)
                .setIntent(new Intent(Intent.ACTION_VIEW, mContactUri)
                        .putExtra(QuickContact.EXTRA_MODE,
                                /*QuickContactActivity.MODE_FULLY_EXPANDED*/4));
        int starred = ContactsUtils.queryContactStarred(cr, contact_id);
        if ((!TextUtils.isEmpty(phoneNumber)) && (indicate == ContactsContract.RawContacts.INDICATE_PHONE)) {
            if (starred == 0) {
                menu.add(ContextMenu.NONE, MENU_ITEM_TOGGLE_STAR, ContextMenu.NONE,
                        R.string.freeme_menu_item_starred_contacts);
            } else {
                menu.add(ContextMenu.NONE, MENU_ITEM_REMOVE_FROM_STARRED, ContextMenu.NONE,
                        R.string.freeme_menu_item_star_remove_contacts);
            }
        }
        menu.add(ContextMenu.NONE, MENU_ITEM_EDIT, ContextMenu.NONE,
                R.string.freeme_menu_item_edit_contacts)
                .setIntent(new Intent(Intent.ACTION_EDIT, mContactUri));
        if (indicate == ContactsContract.RawContacts.INDICATE_PHONE
                || SimCardUtils.isSimReady(indicate)) {
            menu.add(ContextMenu.NONE, MENU_ITEM_DELETE, ContextMenu.NONE,
                    R.string.menu_delete_contact);
        }
    }

    private Uri getContactUri(int position){
        ListView listView = getListView();
        ContactListAdapter adapter = null;
        if (listView.getAdapter() instanceof HeaderViewListAdapter) {
            HeaderViewListAdapter listAdapter = (HeaderViewListAdapter) listView.getAdapter();
            adapter = (ContactListAdapter) listAdapter.getWrappedAdapter();
        } else {
            adapter = (ContactListAdapter) listView.getAdapter();
        }
        return adapter.getContactUri(position);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            return false;
        }
        //headView is exist so we need minus 1
        Cursor cursor = (Cursor) getAdapter().getItem(info.position - 1);
        if (cursor == null) {
            return false;
        }
        ContentResolver cr = getContext().getContentResolver();
        long contact_id = cursor.getLong(ContactListAdapter.ContactQuery.CONTACT_ID);
        long raw_contact_id = ContactsUtils.queryForRawContactId(cr, contact_id);
        if (raw_contact_id == -1) {
            return false;
        }
        //headView is exist so we need minus 1
        Uri mContactUri = getContactUri(info.position - 1);
        switch (item.getItemId()) {
            case MENU_ITEM_TOGGLE_STAR: {
                flagContactToFavorites(mContactUri, true);
                return true;
            }
            case MENU_ITEM_REMOVE_FROM_STARRED: {
                flagContactToFavorites(mContactUri, false);
                return true;
            }
            case MENU_ITEM_DELETE: {
                FreemeContactDeletionInteraction.start(getActivity(), mContactUri, false);
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    private void flagContactToFavorites(Uri contactUri, boolean isAddToFavorites) {
        ContentValues values = new ContentValues(1);
        values.put(ContactsContract.Contacts.STARRED, isAddToFavorites ? 1 : 0);
        int count = getActivity().getContentResolver().update(contactUri, values, null, null);
        if (count > 0) {
            Toast.makeText(getActivity(),
                    getString(isAddToFavorites
                            ? R.string.freeme_starred_success
                            : R.string.freeme_star_remove_success),
                    Toast.LENGTH_SHORT).show();
        }
    }
    //*/

    //*/ freeme.zhangjunjian, 20170817,add new menu for the contact you want to display
    public void setFilter(ContactListFilter filter) {
        getAdapter().setFilter(filter);
        super.reloadData();
    }
    //*/
}
