package com.freeme.dialer.contacts;

import android.app.IntentService;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.widget.Toast;

import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.R;
import com.mediatek.contacts.util.Log;

import static android.Manifest.permission.WRITE_CONTACTS;

/**
 * Created by zhaozehong on 13/07/17.
 */

public class FreemeContactDeleteService extends IntentService {
    private final static String TAG = "FreemeContactDeleteService";

    public static final String ACTION_DELETE_CONTACT = "delete";
    public static final String ACTION_DELETE_MULTIPLE_CONTACTS = "deleteMultipleContacts";
    public static final String EXTRA_CONTACT_URI = "contactUri";
    public static final String EXTRA_CONTACT_IDS = "contactIds";

    private Handler mMainHandler;

    public FreemeContactDeleteService() {
        super(TAG);
        setIntentRedelivery(true);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (service != null) {
            return service;
        }

        return getApplicationContext().getSystemService(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            Log.d(TAG, "onHandleIntent: could not handle null intent");
            return;
        }
        if (!PermissionsUtil.hasPermission(this, WRITE_CONTACTS)) {
            Log.w(TAG, "No WRITE_CONTACTS permission, unable to write to CP2");
            // TODO: add more specific error string such as "Turn on Contacts
            // permission to update your contacts"
            showToast(R.string.freeme_contact_edit_permission_denied);
            return;
        }

        // Call an appropriate method. If we're sure it affects how incoming phone calls are
        // handled, then notify the fact to in-call screen.
        String action = intent.getAction();
        Log.d(TAG, "[onHandleIntent] action = " + action);
        if (ACTION_DELETE_MULTIPLE_CONTACTS.equals(action)) {
            deleteMultipleContacts(intent);
        } else if (ACTION_DELETE_CONTACT.equals(action)) {
            deleteContact(intent);
        }
    }


    /**
     * Creates an intent that can be sent to this service to delete a contact.
     */
    public static Intent createDeleteContactIntent(Context context, Uri contactUri) {
        Intent serviceIntent = new Intent(context, FreemeContactDeleteService.class);
        serviceIntent.setAction(FreemeContactDeleteService.ACTION_DELETE_CONTACT);
        serviceIntent.putExtra(FreemeContactDeleteService.EXTRA_CONTACT_URI, contactUri);
        return serviceIntent;
    }

    /**
     * Creates an intent that can be sent to this service to delete multiple contacts.
     */
    public static Intent createDeleteMultipleContactsIntent(Context context,
                                                            long[] contactIds) {
        Intent serviceIntent = new Intent(context, FreemeContactDeleteService.class);
        serviceIntent.setAction(FreemeContactDeleteService.ACTION_DELETE_MULTIPLE_CONTACTS);
        serviceIntent.putExtra(FreemeContactDeleteService.EXTRA_CONTACT_IDS, contactIds);
        return serviceIntent;
    }

    private void deleteContact(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for deleteContact request");
            return;
        }

        int count = getContentResolver().delete(contactUri, null, null);
        String toastMsg = getResources().getQuantityString(
                R.plurals.freeme_contacts_deleted_toast, count);
        showToast(toastMsg);
    }

    private void deleteMultipleContacts(Intent intent) {
        Log.d(TAG, "[deleteMultipleContacts] ...");
        final long[] contactIds = intent.getLongArrayExtra(EXTRA_CONTACT_IDS);
        if (contactIds == null) {
            Log.e(TAG, "Invalid arguments for deleteMultipleContacts request");
            return;
        }
        for (long contactId : contactIds) {
            final Uri contactUri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI, contactId);
            getContentResolver().delete(contactUri, null, null);
        }
        String toastMsg = getResources().getQuantityString(
                R.plurals.freeme_contacts_deleted_toast, contactIds.length);
        showToast(toastMsg);
    }

    private void showToast(final int message) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FreemeContactDeleteService.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showToast(final String message) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FreemeContactDeleteService.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
