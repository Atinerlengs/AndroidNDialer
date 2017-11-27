package com.freeme.dialer.calllog;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;

import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.list.ListsFragment;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialer.util.Assert;
import com.android.dialer.util.DialerUtils;
import com.android.dialerbind.ObjectFactory;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.animation.AnimationListenerAdapter;
import com.mediatek.dialer.util.DialerFeatureOptions;

public class FreemeCalllogFragment extends Fragment implements View.OnClickListener {

    private static final String FRAGMENT_TAG_CALL_LOGS = "CallLogFragment";
    private static final String FRAGMENT_TAG_DIALPAD = "DialpadFragment";
    private static final String FRAGMENT_TAG_REGULAR_SEARCH = "search";
    private static final String FRAGMENT_TAG_SMARTDIAL_SEARCH = "smartdial";

    private CallLogFragment mHistoryFragment;
    private DialpadFragment mDialpadFragment;
    private RegularSearchFragment mRegularSearchFragment;
    private SmartDialSearchFragment mSmartDialSearchFragment;

    private FloatingActionButtonController mFloatingActionButtonController;
    private OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener;

    private boolean mIsDialpadShown;
    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private boolean mClearSearchOnPause;

    private String mDialpadQuery;
    private String mSearchQuery;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        boolean mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        final boolean isLayoutRtl = DialerUtils.isRtl();
        if (mIsLandscape) {
            mSlideIn = AnimationUtils.loadAnimation(getContext(),
                    isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
            mSlideOut = AnimationUtils.loadAnimation(getContext(),
                    isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
        } else {
            mSlideIn = AnimationUtils.loadAnimation(getContext(), R.anim.dialpad_slide_in_bottom);
            mSlideOut = AnimationUtils.loadAnimation(getContext(), R.anim.dialpad_slide_out_bottom);
        }
        mSlideIn.setInterpolator(AnimUtils.EASE_IN);
        mSlideOut.setInterpolator(AnimUtils.EASE_OUT);
        mSlideIn.setAnimationListener(mSlideInListener);
        mSlideOut.setAnimationListener(mSlideOutListener);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mPhoneNumberPickerActionListener = (OnPhoneNumberPickerActionListener) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString()
                    + " must implement PhoneFavoritesFragment.listener");
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.floating_action_button) {
            if (!mIsDialpadShown) {
                showDialpadFragment(true);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.freeme_call_log_fragment, container, false);
        final View floatingActionButtonContainer = view.findViewById(
                R.id.floating_action_button_container);
        ImageButton floatingActionButton = (ImageButton) view.findViewById(
                R.id.floating_action_button);
        floatingActionButton.setOnClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(getActivity(),
                floatingActionButtonContainer, floatingActionButton);
        floatingActionButtonContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer =
                                floatingActionButtonContainer.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return;
                        }
                        observer.removeOnGlobalLayoutListener(this);
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        mFloatingActionButtonController.setScreenWidth(screenWidth);
                        mFloatingActionButtonController.align(
                                FloatingActionButtonController.ALIGN_END, false /* animate */);
                    }
                });

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        mHistoryFragment = (CallLogFragment) getChildFragmentManager()
                .findFragmentByTag(FRAGMENT_TAG_CALL_LOGS);
        if (mHistoryFragment == null) {
            mHistoryFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL);
            transaction.add(R.id.fragment_layout, mHistoryFragment, FRAGMENT_TAG_CALL_LOGS);
        } else {
            mRegularSearchFragment = (RegularSearchFragment) getChildFragmentManager()
                    .findFragmentByTag(FRAGMENT_TAG_REGULAR_SEARCH);
            mSmartDialSearchFragment = (SmartDialSearchFragment) getChildFragmentManager()
                    .findFragmentByTag(FRAGMENT_TAG_SMARTDIAL_SEARCH);
            if (mRegularSearchFragment != null) {
                transaction.hide(mRegularSearchFragment);
            }
            if (mSmartDialSearchFragment != null) {
                transaction.hide(mSmartDialSearchFragment);
            }
        }
        transaction.commit();

        resetDialpadStatus();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getParentFragment() instanceof ListsFragment) {
            ListsFragment fragment = (ListsFragment) getParentFragment();
            setStartedFromNewIntent(fragment.isStartFromNewIntent());
            if (fragment.isStartFromNewIntent()) {
                showDialpadFragment(false);
                fillNumber();
                fragment.setStartedFromNewIntent(false);
            }
        }
    }

    @Override
    public void onAttachFragment(Fragment childFragment) {
        if (childFragment instanceof CallLogFragment) {
            mHistoryFragment = (CallLogFragment) childFragment;
            mHistoryFragment.setAccountFilterState(false);
        } else if (childFragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) childFragment;
            boolean showDialpadOnResume = false;
            if (getParentFragment() instanceof ListsFragment) {
                ListsFragment fragment = (ListsFragment) getParentFragment();
                showDialpadOnResume = fragment.isShowDialpadOnResume();
            }
            if (!mIsDialpadShown && !showDialpadOnResume) {
                final FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.hide(mDialpadFragment);
                transaction.commit();
            }
        } else if (childFragment instanceof RegularSearchFragment) {
            mRegularSearchFragment = (RegularSearchFragment) childFragment;
            mRegularSearchFragment.setOnPhoneNumberPickerActionListener(mPhoneNumberPickerActionListener);
        } else if (childFragment instanceof SmartDialSearchFragment) {
            mSmartDialSearchFragment = (SmartDialSearchFragment) childFragment;
            mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(mPhoneNumberPickerActionListener);
            if (!TextUtils.isEmpty(mDialpadQuery)) {
                mSmartDialSearchFragment.setAddToContactNumber(mDialpadQuery);
            }
        }

        if (childFragment instanceof SearchFragment) {
            ((SearchFragment)childFragment).setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    // Show the FAB when the user touches the lists fragment and the soft
                    // keyboard is hidden.
                    if (!mFloatingActionButtonController.isVisible()) {
                        hideDialpadFragment(true, false);
                    }
                    return false;
                }
            });
        }
    }

    private boolean getStateSaved() {
        boolean isStateSaved = false;
        if (getActivity() instanceof DialtactsActivity) {
            isStateSaved = ((DialtactsActivity) getActivity()).getStateSaved();
        }
        return isStateSaved;
    }

    public void showDialpadFragment(boolean animate) {
        if (mIsDialpadShown || getStateSaved()) {
            if (mIsDialpadShown) {
                fillNumber();
            }
            return;
        }
        mIsDialpadShown = true;

        final FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        if (mDialpadFragment == null) {
            mDialpadFragment = new DialpadFragment();
            ft.add(R.id.dialpad_layout, mDialpadFragment, FRAGMENT_TAG_DIALPAD);
        } else {
            ft.show(mDialpadFragment);
        }
        mDialpadFragment.setAnimate(animate);
        ft.commit();

        if (animate) {
            mFloatingActionButtonController.scaleOut();
        } else {
            mFloatingActionButtonController.setVisible(false);
        }

        fillNumber();
    }

    public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
        if (mDialpadFragment == null || mDialpadFragment.getView() == null) {
            return;
        }
        if (clearDialpad) {
            mDialpadFragment.getDigitsWidget().setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mDialpadFragment.clearDialpad();
            mDialpadFragment.getDigitsWidget().setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        if (!mIsDialpadShown) {
            return;
        }
        mIsDialpadShown = false;
        mDialpadFragment.setAnimate(animate);

        mFloatingActionButtonController.align(FloatingActionButtonController.ALIGN_END, animate);

        if (animate) {
            mDialpadFragment.getView().startAnimation(mSlideOut);
        } else {
            commitDialpadFragmentHide();
        }

        if (isInSearchUi()) {
            if (TextUtils.isEmpty(getSearchQueryStr())) {
                exitSearchUi();
            }
        }
    }

    public void commitDialpadFragmentHide() {
        if (!getStateSaved() && mDialpadFragment != null && !mDialpadFragment.isHidden()) {
            final FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.hide(mDialpadFragment);
            ft.commit();
        }
        mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
    }

    public boolean isInSearchUi() {
        return mInDialpadSearch || mInRegularSearch;
    }

    private void setNotInSearchUi() {
        mInDialpadSearch = false;
        mInRegularSearch = false;
    }

    private String getSearchQueryStr() {
        if (mDialpadFragment != null) {
            return mDialpadFragment.getDigitsWidget().getText().toString();
        } else {
            return "";
        }
    }

    public void enterSearchUi(boolean smartDialSearch, String searchQuery, boolean animate) {
        final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        if (mInDialpadSearch && mSmartDialSearchFragment != null) {
            transaction.remove(mSmartDialSearchFragment);
        } else if (mInRegularSearch && mRegularSearchFragment != null) {
            transaction.remove(mRegularSearchFragment);
        }

        final String tag;
        if (smartDialSearch) {
            tag = FRAGMENT_TAG_SMARTDIAL_SEARCH;
        } else {
            tag = FRAGMENT_TAG_REGULAR_SEARCH;
        }
        mInDialpadSearch = smartDialSearch;
        mInRegularSearch = !smartDialSearch;

        SearchFragment fragment = (SearchFragment) getChildFragmentManager().findFragmentByTag(tag);
        if (animate) {
            transaction.setCustomAnimations(android.R.animator.fade_in, 0);
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_NONE);
        }

        // If switch to a new fragment, it need to set query string to this
        // fragment, otherwise the query result would show nothing. @{
        boolean needToSetQuery = false;
        if (fragment == null) {
            needToSetQuery = true;
            if (smartDialSearch) {
                fragment = new SmartDialSearchFragment();
            } else {
                fragment = ObjectFactory.newRegularSearchFragment();
            }
            transaction.add(R.id.fragment_layout, fragment, tag);
        } else {
            transaction.show(fragment);
        }
        // DialtactsActivity will provide the options menu
        fragment.setHasOptionsMenu(false);
        fragment.setShowEmptyListForNullQuery(true);
        if (!smartDialSearch || needToSetQuery) {
            fragment.setQueryString(searchQuery, false /* delaySelection */);
        }
        transaction.commit();

        setViewPagerIsCanScroll(false);
    }

    private void setViewPagerIsCanScroll(boolean isCallScroll) {
        if (getActivity() instanceof DialtactsActivity) {
            ((DialtactsActivity) getActivity()).getListsFragment()
                    .setViewPagerIsCanScroll(isCallScroll);
        }
    }

    private boolean maybeExitSearchUi() {
        if (isInSearchUi() && isDigitsEmpty()) {
            exitSearchUi();
            DialerUtils.hideInputMethod(getView());
            return true;
        }
        return false;
    }

    /**
     * Hides the search fragment
     */
    public void exitSearchUi() {
        if (mDialpadFragment != null) {
            mDialpadFragment.clearDialpad();
        }

        if (getParentFragment() instanceof ListsFragment) {
            ((ListsFragment) getParentFragment()).cleanPendingSearchViewQuery();
        }

        setNotInSearchUi();

        final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        if (mSmartDialSearchFragment != null) {
            transaction.remove(mSmartDialSearchFragment);
        }
        if (mRegularSearchFragment != null) {
            transaction.remove(mRegularSearchFragment);
        }
        transaction.commit();

        setViewPagerIsCanScroll(true);
    }

    public int getItemCount() {
        if (mHistoryFragment != null) {
            return mHistoryFragment.getItemCount();
        } else {
            return 0;
        }
    }

    private void isDialpadShow(boolean isShow) {
        mIsDialpadShown = isShow;
        if (getActivity() instanceof DialtactsActivity) {
            ((DialtactsActivity) getActivity()).setTitleNumber(mIsDialpadShown);
        }
    }

    private Animation mSlideIn;
    private Animation mSlideOut;

    AnimationListenerAdapter mSlideInListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            isDialpadShow(true);
            if (mDialpadFragment != null) {
                mDialpadFragment.setAnimationing(false);
            }
        }

        @Override
        public void onAnimationStart(Animation animation) {
            super.onAnimationStart(animation);
            if (mDialpadFragment != null) {
                mDialpadFragment.setAnimationing(true);
            }
        }
    };

    /**
     * Listener for after slide out animation completes on dialer fragment.
     */
    AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            commitDialpadFragmentHide();
            isDialpadShow(false);
            if (mDialpadFragment != null) {
                mDialpadFragment.setAnimationing(false);
            }
        }

        @Override
        public void onAnimationStart(Animation animation) {
            super.onAnimationStart(animation);
            if (mDialpadFragment != null) {
                mDialpadFragment.setAnimationing(true);
            }
        }
    };

    public void onDialpadShown() {
        Assert.assertNotNull(mDialpadFragment);
        if (mDialpadFragment.getAnimate()) {
            mDialpadFragment.getView().startAnimation(mSlideIn);
        } else {
            mDialpadFragment.setYFraction(0);
        }
    }

    public void setStartedFromNewIntent(boolean value) {
        if (mDialpadFragment != null) {
            mDialpadFragment.setStartedFromNewIntent(value);
        }
    }

    public boolean isDialpadVisible() {
        if (mDialpadFragment != null) {
            return mDialpadFragment.isVisible();
        }
        return false;
    }

    public boolean onBackPress() {
        if (mIsDialpadShown) {
            if (TextUtils.isEmpty(getSearchQueryStr()) ||
                    (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()
                            && mSmartDialSearchFragment.getAdapter().getCount() == 0)) {
                exitSearchUi();
            }
            hideDialpadFragment(true, false);
            return true;
        }
        return false;
    }

    public int getDialpadHeight() {
        if (mDialpadFragment != null) {
            return mDialpadFragment.getDialpadHeight();
        }
        return 0;
    }

    public boolean isDigitsEmpty() {
        if (mDialpadFragment != null) {
            return mDialpadFragment.isDigitsEmpty();
        }
        return true;
    }

    public boolean isDialpadShow() {
        return mIsDialpadShown;
    }

    public String getSearchQuery() {
        return mSearchQuery;
    }

    public String getDialpadQuery() {
        return mDialpadQuery;
    }

    public void fillNumber() {
        String query = null;
        if (getParentFragment() instanceof ListsFragment) {
            query = ((ListsFragment) getParentFragment()).getPendingSearchViewQuery();
        }
        fillNumber(query);
    }

    private void fillNumber(String query) {
        if (mDialpadFragment != null) {
            mDialpadFragment.fillNumber(query);
        }
    }

    public boolean isShowingPermissionRequest() {
        return mInDialpadSearch && mSmartDialSearchFragment != null
                && mSmartDialSearchFragment.isShowingPermissionRequest();
    }

    public boolean isDialpadSlideOutStarting() {
        return mSlideOut.hasStarted() && !mSlideOut.hasEnded();
    }

    public void hideDialpadAndSearchUi() {
        if (mIsDialpadShown) {
            hideDialpadFragment(false, true);
        } else {
            exitSearchUi();
        }
    }

    public void setClearSearchOnPause(boolean clearSearch) {
        mClearSearchOnPause = clearSearch;
    }

    public boolean getClearSearchOnPause(){
        return mClearSearchOnPause;
    }

    public void onDialpadQueryChanged(String query, String normalizedQuery) {
        mDialpadQuery = query;
        if (mSmartDialSearchFragment != null) {
            mSmartDialSearchFragment.setAddToContactNumber(query);
        }
        if (!TextUtils.equals(mSearchQuery, normalizedQuery)) {

            mSearchQuery = normalizedQuery;

            if (TextUtils.isEmpty(mSearchQuery)) {
                maybeExitSearchUi();
            }

            if (!TextUtils.isEmpty(mSearchQuery)) {
                final boolean sameSearchMode = (mIsDialpadShown && mInDialpadSearch) ||
                        (!mIsDialpadShown && mInRegularSearch);
                if (!sameSearchMode) {
                    enterSearchUi(true, mSearchQuery, false);
                }
            }

            if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
                mSmartDialSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
                mRegularSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            }
        }

        try {
            if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
                mDialpadFragment.process_quote_emergency_unquote(normalizedQuery);
            }
        } catch (Exception ignored) {
            // Skip any exceptions for this piece of code
        }
    }

    public void resetDialpadStatus() {
        if (getParentFragment() instanceof ListsFragment) {
            ListsFragment fragment = (ListsFragment) getParentFragment();
            if (fragment.isFirstLaunch() || fragment.isShowDialpadOnResume()) {
                showDialpadFragment(false);
            } else {
                String query = fragment.getPendingSearchViewQuery();
                if (!TextUtils.isEmpty(query)) {
                    fillNumber(query);
                    isDialpadShow(false);
                }
            }
        }
    }
}
