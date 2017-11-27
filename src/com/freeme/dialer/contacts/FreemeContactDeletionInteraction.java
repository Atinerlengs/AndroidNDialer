/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.freeme.dialer.contacts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Entity;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.dialer.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.internal.telephony.PhoneConstants;
import com.google.common.collect.Sets;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.util.ContactsConstants;
import com.mediatek.contacts.util.Log;

import java.util.HashSet;

import com.mediatek.contacts.eventhandler.BaseEventHandlerFragment;
import com.mediatek.contacts.eventhandler.GeneralEventHandler;

/**
 * Created by zhaozehong on 13/07/17.
 */

public class FreemeContactDeletionInteraction extends BaseEventHandlerFragment
        implements LoaderCallbacks<Cursor>,
        OnDismissListener, FreemeSimDeleteProcessor.Listener {

    private final String TAG = this.getClass().getSimpleName();
    private static final String FRAGMENT_TAG = "deleteContact";

    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CONTACT_URI = "contactUri";

    private static final String KEY_FINISH_WHEN_DONE = "finishWhenDone";
    public static final String ARG_CONTACT_URI = "contactUri";
    public static final int RESULT_CODE_DELETED = 3;

    private static final String[] ENTITY_PROJECTION = new String[]{
            Entity.RAW_CONTACT_ID, //0
            Entity.ACCOUNT_TYPE, //1
            Entity.DATA_SET, // 2
            Entity.CONTACT_ID, // 3
            Entity.LOOKUP_KEY, // 4
            RawContacts.INDICATE_PHONE_SIM, // 5
            RawContacts.INDEX_IN_SIM, //6
    };

    private static final int COLUMN_INDEX_RAW_CONTACT_ID = 0;
    private static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
    private static final int COLUMN_INDEX_DATA_SET = 2;
    private static final int COLUMN_INDEX_CONTACT_ID = 3;
    private static final int COLUMN_INDEX_LOOKUP_KEY = 4;
    private static final int COLUMN_INDEX_INDICATE_PHONE_SIM = 5;
    private static final int COLUMN_INDEX_IN_SIM = 6;

    private boolean mActive;
    private Uri mContactUri;
    private boolean mFinishActivityWhenDone;
    private Context mContext;
    private AlertDialog mDialog;

    /**
     * Starts the interaction.
     *
     * @param activity               the activity within which to start the interaction
     * @param contactUri             the URI of the contact to delete
     * @param finishActivityWhenDone whether to finish the activity upon completion of the
     *                               interaction
     * @return the newly created interaction
     */
    public static FreemeContactDeletionInteraction start(
            Activity activity, Uri contactUri, boolean finishActivityWhenDone) {
        Log.d(FRAGMENT_TAG, "[start] set mSimUri and mSimWhere are null");
        FreemeContactDeletionInteraction deletion = startWithLoaderManager(activity,
                contactUri, finishActivityWhenDone);
        return deletion;
    }

    /**
     * Starts the interaction and optionally
     *
     * @param activity               the activity within which to start the interaction
     * @param contactUri             the URI of the contact to delete
     * @param finishActivityWhenDone whether to finish the activity upon completion of the
     *                               interaction
     * @return the newly created interaction
     */
    private static FreemeContactDeletionInteraction startWithLoaderManager(
            Activity activity, Uri contactUri, boolean finishActivityWhenDone) {
        if (contactUri == null || activity.isDestroyed()) {
            return null;
        }

        FragmentManager fragmentManager = activity.getFragmentManager();
        FreemeContactDeletionInteraction fragment =
                (FreemeContactDeletionInteraction) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new FreemeContactDeletionInteraction();
            fragment.setContactUri(contactUri);
            fragment.setFinishActivityWhenDone(finishActivityWhenDone);
            fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG)
                    .commitAllowingStateLoss();
        } else {
            fragment.setContactUri(contactUri);
            fragment.setFinishActivityWhenDone(finishActivityWhenDone);
        }
        // add for sim contact
        fragment.mSimUri = null;
        fragment.mSimIndex = -1;

        return fragment;
    }

    @Override
    public LoaderManager getLoaderManager() {
        return super.getLoaderManager();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        // Add for SIM Service refactory
        FreemeSimDeleteProcessor.registerListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
            mDialog = null;
        }

        // Add for SIM Service refactory
        FreemeSimDeleteProcessor.unregisterListener(this);
    }

    public void setContactUri(Uri contactUri) {
        mContactUri = contactUri;
        mActive = true;
        if (isStarted()) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().restartLoader(R.id.freeme_dialog_delete_contact_loader_id, args, this);
        }
    }

    private void setFinishActivityWhenDone(boolean finishActivityWhenDone) {
        this.mFinishActivityWhenDone = finishActivityWhenDone;

    }

    /* Visible for testing */
    boolean isStarted() {
        return isAdded();
    }

    @Override
    public void onStart() {
        if (mActive) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().initLoader(R.id.freeme_dialog_delete_contact_loader_id, args, this);
        }
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDialog != null) {
            mDialog.hide();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri contactUri = args.getParcelable(ARG_CONTACT_URI);
        return new CursorLoader(mContext,
                Uri.withAppendedPath(contactUri, Entity.CONTENT_DIRECTORY), ENTITY_PROJECTION,
                null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (!mActive) {
            return;
        }

        if (cursor == null || cursor.isClosed()) {
            Log.e(TAG, "Failed to load contacts");
            return;
        }

        long contactId = 0;
        String lookupKey = null;

        // This cursor may contain duplicate raw contacts, so we need to de-dupe them first
        HashSet<Long> readOnlyRawContacts = Sets.newHashSet();
        HashSet<Long> writableRawContacts = Sets.newHashSet();

        AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long rawContactId = cursor.getLong(COLUMN_INDEX_RAW_CONTACT_ID);
            final String accountType = cursor.getString(COLUMN_INDEX_ACCOUNT_TYPE);
            final String dataSet = cursor.getString(COLUMN_INDEX_DATA_SET);
            contactId = cursor.getLong(COLUMN_INDEX_CONTACT_ID);
            lookupKey = cursor.getString(COLUMN_INDEX_LOOKUP_KEY);
            mSubId = cursor.getInt(COLUMN_INDEX_INDICATE_PHONE_SIM);
            mSimIndex = cursor.getInt(COLUMN_INDEX_IN_SIM);
            mSimUri = SubInfoUtils.getIccProviderUri(mSubId);
            AccountType type = accountTypes.getAccountType(accountType, dataSet);
            boolean writable = type == null || type.areContactsWritable();
            if (writable) {
                writableRawContacts.add(rawContactId);
            } else {
                readOnlyRawContacts.add(rawContactId);
            }
        }
        if (TextUtils.isEmpty(lookupKey)) {
            Log.e(TAG, "Failed to find contact lookup key");
            getActivity().finish();
            return;
        }

        int readOnlyCount = readOnlyRawContacts.size();
        int writableCount = writableRawContacts.size();
        int positiveButtonId = android.R.string.ok;
        int mMessageId;
        if (readOnlyCount > 0 && writableCount > 0) {
            mMessageId = R.string.freeme_readonly_contact_delete_confirmation;
        } else if (readOnlyCount > 0 && writableCount == 0) {
            mMessageId = R.string.freeme_readonly_contact_delete_warning;
            positiveButtonId = R.string.freeme_readonly_contact_delete_positive_button;
        } else if (readOnlyCount == 0 && writableCount > 1) {
            mMessageId = R.string.freeme_multiple_contact_delete_confirmation;
            positiveButtonId = R.string.freeme_contact_delete_positive_button;
        } else {
            mMessageId = R.string.freeme_contact_delete_confirmation;
            positiveButtonId = R.string.freeme_contact_delete_positive_button;
        }

        // Forbid user to delete the read only account contacts in Contacts AP. The delete
        // flow is not suit for these accounts.
        // Change the dialog message, because we can't make it in-visible or delete it clearly. @{
        if (readOnlyCount > 0) {
            showReadonlyDialog();
        } else {
            final Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);
            showDialog(mMessageId, positiveButtonId, contactUri);
        }

        // We don't want onLoadFinished() calls any more, which may come when the database is
        // updating.
        getLoaderManager().destroyLoader(R.id.freeme_dialog_delete_contact_loader_id);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void showDialog(int messageId, int positiveButtonId, final Uri contactUri) {
        mDialog = new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(messageId)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(positiveButtonId,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                doDeleteContact(contactUri);
                            }
                        }).create();
        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mActive = false;
        mDialog = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ACTIVE, mActive);
        outState.putParcelable(KEY_CONTACT_URI, mContactUri);

        //to save sim_uri and sim_index to delete
        outState.putParcelable(KEY_CONTACT_SIM_URI, mSimUri);
        outState.putInt(KEY_CONTACT_SIM_INDEX, mSimIndex);
        outState.putInt(KEY_CONTACT_SUB_ID, mSubId);

        outState.putBoolean(KEY_FINISH_WHEN_DONE, mFinishActivityWhenDone);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mActive = savedInstanceState.getBoolean(KEY_ACTIVE);
            mContactUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);

            // to get sim_uri and sim_index to delete
            mSimUri = savedInstanceState.getParcelable(KEY_CONTACT_SIM_URI);
            mSimIndex = savedInstanceState.getInt(KEY_CONTACT_SIM_INDEX);
            mSubId = savedInstanceState.getInt(KEY_CONTACT_SUB_ID);

            mFinishActivityWhenDone = savedInstanceState.getBoolean(KEY_FINISH_WHEN_DONE);
        }
    }

    protected void doDeleteContact(final Uri contactUri) {
        // Add for SIM Contact
        if (!isAdded()) {
            Log.w(FRAGMENT_TAG, "[doDeleteContact] This Fragment is not add to the Activity.");
            return;
        }
        if (!FreemeSimDeleteProcessor.doDeleteSimContact(mContext,
                contactUri, mSimUri, mSimIndex, mSubId, this)) {
            mContext.startService(FreemeContactDeleteService.createDeleteContactIntent(
                    mContext, contactUri));
            if (isAdded() && mFinishActivityWhenDone) {
                Log.d(FRAGMENT_TAG, "[doDeleteContact] finished");
                getActivity().setResult(RESULT_CODE_DELETED);
                getActivity().finish();
            }
        }
    }

    private Uri mSimUri = null;
    private int mSimIndex = -1;
    // change for SIM Service refactoring
    private static int mSubId = SubInfoUtils.getInvalidSubId();

    // Add for SIM Service refactory
    @Override
    public void onSIMDeleteFailed() {
        if (isAdded()) {
            getActivity().finish();
        }
        return;
    }

    @Override
    public void onSIMDeleteCompleted() {
        if (isAdded() && mFinishActivityWhenDone) {
            getActivity().setResult(RESULT_CODE_DELETED);
            // Conn't show toast in non UI thread
            final String toastMsg = getResources().getQuantityString(
                    R.plurals.freeme_contacts_deleted_toast, /* quantity */ 1);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, toastMsg, Toast.LENGTH_LONG).show();
                }
            });
            getActivity().finish();
        }
        return;
    }

    // refactor phb state change
    @Override
    public void onReceiveEvent(String eventType, Intent extraData) {
        int stateChangeSubId = extraData.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                ContactsConstants.ERROR_SUB_ID);
        Log.i(TAG,
                "[onReceiveEvent] eventType: " + eventType + ", extraData: " + extraData.toString()
                        + ",stateChangeSubId: " + stateChangeSubId + ",mSubId: " + mSubId);
        if (GeneralEventHandler.EventType.PHB_STATE_CHANGE_EVENT.equals(eventType)
                && (mSubId == stateChangeSubId)) {
            Log.i(TAG, "[onReceiveEvent] phb state change,finish EditorActivity ");
            getActivity().setResult(RESULT_CODE_DELETED);
            getActivity().finish();
            return;
        }
    }

    // add for alert the read only contact can not be delete in Contact APP
    private void showReadonlyDialog() {
        mDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.deleteConfirmation_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.freeme_readonly_contact_delete_warning)
                .setPositiveButton(android.R.string.ok, null).create();
        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    // key to map sim_uri and sim_index to delete
    private static final String KEY_CONTACT_SIM_URI = "contact_sim_uri";
    private static final String KEY_CONTACT_SIM_INDEX = "contact_sim_index";
    private static final String KEY_CONTACT_SUB_ID = "contact_sub_id";
}
