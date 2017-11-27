package com.freeme.dialer.list;

import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.R;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.list.PhoneFavoritesTileAdapter;
import com.android.dialer.list.SpeedDialFragment;
import com.android.dialer.widget.EmptyContentView;
import com.android.incallui.Call;

import java.util.HashMap;

/**
 * Created by zhaozehong on 06/07/17.
 */

public class FreemeSpeedDialFragment extends ListFragment implements
        EmptyContentView.OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback,
        PhoneFavoritesTileAdapter.OnDataSetChangedForAnimationListener {

    private static final String TAG = FreemeSpeedDialFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * request full group permissions instead of READ_CALL_LOG,
     * Because MTK changed the group permissions granting logic.
     */
    private static final String[] READ_CONTACTS = PermissionsUtil.CONTACTS_FULL_GROUP;

    private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;

    /**
     * Used with LoaderManager.
     */
    private static int LOADER_ID_CONTACT_TILE = 1;

    private class ContactTileLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onCreateLoader.");
            return ContactTileLoaderFactory.createStrequentPhoneOnlyLoader(getActivity());
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoadFinished");
            mContactTileAdapter.setContactCursor(data);
            setEmptyViewVisibility(mContactTileAdapter.getCount() == 0);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoaderReset. ");
        }
    }

    private class ContactTileAdapterListener implements ContactTileView.Listener {
        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            if (mPhoneNumberPickerActionListener != null) {
                mPhoneNumberPickerActionListener.onPickDataUri(contactUri,
                        false /* isVideoCall */, Call.LogState.INITIATION_SPEED_DIAL);
            }
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            if (mPhoneNumberPickerActionListener != null) {
                mPhoneNumberPickerActionListener.onPickPhoneNumber(phoneNumber,
                        false /* isVideoCall */, Call.LogState.INITIATION_SPEED_DIAL);
            }
        }

        @Override
        public int getApproximateTileWidth() {
            return getView().getWidth();
        }
    }

    private class ScrollListener implements ListView.OnScrollListener {
        @Override
        public void onScroll(AbsListView view,
                             int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (mActivityScrollListener != null) {
                mActivityScrollListener.onListFragmentScroll(firstVisibleItem, visibleItemCount,
                        totalItemCount);
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
        }
    }

    private OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener;

    private OnListFragmentScrolledListener mActivityScrollListener;
    private PhoneFavoritesTileAdapter mContactTileAdapter;

    private View mParentView;

    private ListView mListView;


    private final HashMap<Long, Integer> mItemIdTopMap = new HashMap<Long, Integer>();
    private final HashMap<Long, Integer> mItemIdLeftMap = new HashMap<Long, Integer>();

    /**
     * Layout used when there are no favorites.
     */
    private EmptyContentView mEmptyView;

    private final ContactTileView.Listener mContactTileAdapterListener =
            new ContactTileAdapterListener();
    private final LoaderManager.LoaderCallbacks<Cursor> mContactTileLoaderListener =
            new ContactTileLoaderListener();
    private final ScrollListener mScrollListener = new ScrollListener();

    @Override
    public void onAttach(Activity activity) {
        if (DEBUG) Log.d(TAG, "onAttach()");
        super.onAttach(activity);

        // Construct two base adapters which will become part of PhoneFavoriteMergedAdapter.
        // We don't construct the resultant adapter at this moment since it requires LayoutInflater
        // that will be available on onCreateView().
        mContactTileAdapter = new PhoneFavoritesTileAdapter(activity, mContactTileAdapterListener,
                this);
        mContactTileAdapter.setPhotoLoader(ContactPhotoManager.getInstance(activity));
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (DEBUG) Log.d(TAG, "onCreate()");
        super.onCreate(savedState);
        setListAdapter(mContactTileAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mContactTileAdapter != null) {
            mContactTileAdapter.refreshContactsPreferences();
        }
        if (PermissionsUtil.hasContactsPermissions(getActivity())) {
            if (getLoaderManager().getLoader(LOADER_ID_CONTACT_TILE) == null) {
                getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null,
                        mContactTileLoaderListener);
            } else {
                getLoaderManager().getLoader(LOADER_ID_CONTACT_TILE).forceLoad();
            }
            mEmptyView.setDescription(R.string.speed_dial_empty);
            mEmptyView.setActionLabel(R.string.speed_dial_empty_add_favorite_action);
        } else {
            mEmptyView.setDescription(R.string.permission_no_speeddial);
            mEmptyView.setActionLabel(R.string.permission_single_turn_on);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mParentView = inflater.inflate(R.layout.freeme_speed_dial_fragment, container, false);

        mEmptyView = (EmptyContentView) mParentView.findViewById(R.id.empty_list_view);
        mEmptyView.setImage(R.drawable.empty_speed_dial);
        mEmptyView.setActionClickedListener(this);

        return mParentView;
    }

    public boolean hasFrequents() {
        if (mContactTileAdapter == null) return false;
        return mContactTileAdapter.getNumFrequents() > 0;
    }

    /* package */ void setEmptyViewVisibility(final boolean visible) {
        final int previousVisibility = mEmptyView.getVisibility();
        final int emptyViewVisibility = visible ? View.VISIBLE : View.GONE;
        final int listViewVisibility = visible ? View.GONE : View.VISIBLE;

        if (previousVisibility != emptyViewVisibility) {
            mEmptyView.setVisibility(emptyViewVisibility);
            getListView().setVisibility(listViewVisibility);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        final Activity activity = getActivity();

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }

        try {
            mPhoneNumberPickerActionListener = (OnPhoneNumberPickerActionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement PhoneFavoritesFragment.listener");
        }

        // Use initLoader() instead of restartLoader() to refraining unnecessary reload.
        // This method call implicitly assures ContactTileLoaderListener's onLoadFinished() will
        // be called, on which we'll check if "all" contacts should be reloaded again or not.
        if (PermissionsUtil.hasContactsPermissions(activity)) {
            getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null, mContactTileLoaderListener);
        } else {
            setEmptyViewVisibility(true);
        }
    }

    private boolean containsId(long[] ids, long target) {
        // Linear search on array is fine because this is typically only 0-1 elements long
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == target) {
                return true;
            }
        }
        return false;
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
            // Switch tabs
            ((SpeedDialFragment.HostInterface) activity).showAllContactsTab();
        }
    }

    @Override
    public void onDataSetChangedForAnimation(long... idsInPlace) {
        //ignore
    }

    @Override
    public void cacheOffsetsForDatasetChange() {
        //ignore
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length == 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                PermissionsUtil.notifyPermissionGranted(getActivity(), READ_CONTACTS);
            }
            /// M: notify group permissions when them were granted @{
            if (PermissionsUtil.hasPermission(getActivity(), READ_CONTACTS)) {
                PermissionsUtil.notifyPermissionGranted(getActivity(), READ_CONTACTS);
            }
            ///@}
        }
    }
}
