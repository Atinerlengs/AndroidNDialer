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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.telecom.PhoneAccountHandle;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ClipboardUtils;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.contacts.common.dialog.CallSubjectDialog;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.calllogcache.CallLogCache;
import com.android.dialer.compat.FilteredNumberCompat;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.filterednumber.BlockNumberDialogFragment;
import com.android.dialer.filterednumber.FilteredNumbersUtil;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.service.ExtendedBlockingButtonRenderer;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PhoneNumberUtil;
import com.android.dialer.voicemail.VoicemailPlaybackLayout;
import com.android.dialer.voicemail.VoicemailPlaybackPresenter;
import com.android.dialerbind.ObjectFactory;

import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.DialerFeatureOptions;
import com.mediatek.dialer.util.DialerVolteUtils;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
//*/ freeme.zhaozehong, 05/07/17. for freemeOS
import android.content.ContentUris;
import com.android.contacts.common.util.Constants;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.TelecomUtil;
import com.android.incallui.Call;
import com.freeme.dialer.widgets.FreemeQuickContactBadge;
//*/

/**
 * This is an object containing references to views contained by the call log list item. This
 * improves performance by reducing the frequency with which we need to find views by IDs.
 *
 * This object also contains UI logic pertaining to the view, to isolate it from the CallLogAdapter.
 */
public final class CallLogListItemViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, MenuItem.OnMenuItemClickListener,
        View.OnCreateContextMenuListener {

    /** The root view of the call log list item */
    public final View rootView;
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The primary action view of the entry. */
    public final View primaryActionView;
    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;
    /** The text of the header for a day grouping. */
    public final TextView dayGroupHeader;
    /** The view containing the details for the call log row, including the action buttons. */
    public final CardView callLogEntryView;
    /** The actionable view which places a call to the number corresponding to the call log row. */
    public final ImageView primaryActionButtonView;

    /** The view containing call log item actions.  Null until the ViewStub is inflated. */
    public View actionsView;
    /** The button views below are assigned only when the action section is expanded. */
    public VoicemailPlaybackLayout voicemailPlaybackView;
    public View callButtonView;
    public View videoCallButtonView;
    public View createNewContactButtonView;
    public View addToExistingContactButtonView;
    public View sendMessageView;
    public View detailsButtonView;
    /// M: [IP Dial] For IP Prefix feature
    public View ipDialButtonView;
    /** M: [VoLTE ConfCallLog] the conference call numbers if it was conference call @{ */
    public ArrayList<String> confCallNumbers;
    /** @} */
    public View callWithNoteButtonView;
    public ImageView workIconView;

    /**
     * The row Id for the first call associated with the call log entry.  Used as a key for the
     * map used to track which call log entries have the action button section expanded.
     */
    public long rowId;

    /**
     * The call Ids for the calls represented by the current call log entry.  Used when the user
     * deletes a call log entry.
     */
    public long[] callIds;

    /**
     * The callable phone number for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public String number;

    /**
     * The post-dial numbers that are dialed following the phone number.
     */
    public String postDialDigits;

    /**
     * The formatted phone number to display.
     */
    public String displayNumber;

    /**
     * The phone number presentation for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public int numberPresentation;

    /**
     * The type of the phone number (e.g. main, work, etc).
     */
    public String numberType;

    /**
     * The country iso for the call. Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public String countryIso;

    /**
     * The type of call for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public int callType;

    /**
     * ID for blocked numbers database.
     * Set when context menu is created, if the number is blocked.
     */
    public Integer blockId;

    /**
     * The account for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public PhoneAccountHandle accountHandle;

    /**
     * If the call has an associated voicemail message, the URI of the voicemail message for
     * playback.  Cached here as the voicemail intent is only set when the actions ViewStub is
     * inflated.
     */
    public String voicemailUri;

    /**
     * The name or number associated with the call.  Cached here for use when setting content
     * descriptions on buttons in the actions ViewStub when it is inflated.
     */
    public CharSequence nameOrNumber;

    /**
     * The call type or Location associated with the call. Cached here for use when setting text
     * for a voicemail log's call button
     */
    public CharSequence callTypeOrLocation;

    /**
     * Whether this row is for a business or not.
     */
    public boolean isBusiness;

    /**
     * The contact info for the contact displayed in this list item.
     */
    public ContactInfo info;

    /**
     * Whether the current log entry is a blocked number or not. Used in updatePhoto()
     */
    public boolean isBlocked;

    /**
     * Whether this is the archive tab or not.
     */
    public final boolean isArchiveTab;

    private final Context mContext;
    private final CallLogCache mCallLogCache;
    private final CallLogListItemHelper mCallLogListItemHelper;
    private final VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;
    private final FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;

    private final BlockNumberDialogFragment.Callback mFilteredNumberDialogCallback;

    private final int mPhotoSize;
    private ViewStub mExtendedBlockingViewStub;
    private final ExtendedBlockingButtonRenderer mExtendedBlockingButtonRenderer;

    private View.OnClickListener mExpandCollapseListener;
    private boolean mVoicemailPrimaryActionButtonClicked;

    private CallLogListItemViewHolder(
            Context context,
            ExtendedBlockingButtonRenderer.Listener eventListener,
            View.OnClickListener expandCollapseListener,
            CallLogCache callLogCache,
            CallLogListItemHelper callLogListItemHelper,
            VoicemailPlaybackPresenter voicemailPlaybackPresenter,
            FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler,
            BlockNumberDialogFragment.Callback filteredNumberDialogCallback,
            View rootView,
            QuickContactBadge quickContactView,
            View primaryActionView,
            PhoneCallDetailsViews phoneCallDetailsViews,
            CardView callLogEntryView,
            TextView dayGroupHeader,
            ImageView primaryActionButtonView,
            boolean isArchiveTab) {
        super(rootView);

        mContext = context;
        mExpandCollapseListener = expandCollapseListener;
        mCallLogCache = callLogCache;
        mCallLogListItemHelper = callLogListItemHelper;
        mVoicemailPlaybackPresenter = voicemailPlaybackPresenter;
        mFilteredNumberAsyncQueryHandler = filteredNumberAsyncQueryHandler;
        mFilteredNumberDialogCallback = filteredNumberDialogCallback;

        this.rootView = rootView;
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.callLogEntryView = callLogEntryView;
        this.dayGroupHeader = dayGroupHeader;
        this.primaryActionButtonView = primaryActionButtonView;
        this.workIconView = (ImageView) rootView.findViewById(R.id.work_profile_icon);
        this.isArchiveTab = isArchiveTab;
        Resources resources = mContext.getResources();
        mPhotoSize = mContext.getResources().getDimensionPixelSize(R.dimen.contact_photo_size);

        // Set text height to false on the TextViews so they don't have extra padding.
        phoneCallDetailsViews.nameView.setElegantTextHeight(false);
        phoneCallDetailsViews.callLocationAndDate.setElegantTextHeight(false);
        //*/ freeme.zhaozehong, 03/07/17. for new views
        phoneCallDetailsViews.callCount.setElegantTextHeight(false);
        phoneCallDetailsViews.callDate.setElegantTextHeight(false);
        //*/

        quickContactView.setOverlay(null);
        if (CompatUtils.hasPrioritizedMimeType()) {
            quickContactView.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
        }
        primaryActionButtonView.setOnClickListener(this);
        primaryActionView.setOnClickListener(mExpandCollapseListener);
        primaryActionView.setOnCreateContextMenuListener(this);
        mExtendedBlockingButtonRenderer =
                ObjectFactory.newExtendedBlockingButtonRenderer(mContext, eventListener);
    }

    public static CallLogListItemViewHolder create(
            View view,
            Context context,
            ExtendedBlockingButtonRenderer.Listener eventListener,
            View.OnClickListener expandCollapseListener,
            CallLogCache callLogCache,
            CallLogListItemHelper callLogListItemHelper,
            VoicemailPlaybackPresenter voicemailPlaybackPresenter,
            FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler,
            BlockNumberDialogFragment.Callback filteredNumberDialogCallback,
            boolean isArchiveTab) {

        return new CallLogListItemViewHolder(
                context,
                eventListener,
                expandCollapseListener,
                callLogCache,
                callLogListItemHelper,
                voicemailPlaybackPresenter,
                filteredNumberAsyncQueryHandler,
                filteredNumberDialogCallback,
                view,
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                PhoneCallDetailsViews.fromView(view),
                (CardView) view.findViewById(R.id.call_log_row),
                (TextView) view.findViewById(R.id.call_log_day_group_label),
                (ImageView) view.findViewById(R.id.primary_action_button),
                isArchiveTab);
    }

    @Override
    public void onCreateContextMenu(
            final ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (TextUtils.isEmpty(number)) {
            return;
        }

        /*/ freeme.zhaozehong, 20180905. reset title
        if (callType == CallLog.Calls.VOICEMAIL_TYPE) {
            menu.setHeaderTitle(mContext.getResources().getText(R.string.voicemail));
        } else {
            menu.setHeaderTitle(PhoneNumberUtilsCompat.createTtsSpannable(
                    BidiFormatter.getInstance().unicodeWrap(number, TextDirectionHeuristics.LTR)));
        }
        /*/
        boolean isConferenceCall = confCallNumbers != null && confCallNumbers.size() > 0;
        if (callType == CallLog.Calls.VOICEMAIL_TYPE) {
            menu.setHeaderTitle(mContext.getResources().getText(R.string.voicemail));
        } else if (isConferenceCall) {
            menu.setHeaderTitle(mContext.getResources().getText(R.string.menu_conference_call));
        } else {
            menu.setHeaderTitle(nameOrNumber);
        }
        //*/

        /*/ freeme.zhaozehong, 04/07/17. move copy item after edit_before_call item
        menu.add(ContextMenu.NONE, R.id.context_menu_copy_to_clipboard, ContextMenu.NONE,
                R.string.action_copy_number_text)
                .setOnMenuItemClickListener(this);
        //*/

        //*/ freeme.zhaozehong, 04/07/17. add more menu item
        if (PhoneNumberUtil.canPlaceCallsTo(number, numberPresentation)) {
            menu.add(ContextMenu.NONE, R.id.freeme_menu_item_call, ContextMenu.NONE,
                    mContext.getString(R.string.menu_callNumber, ""))
                    .setOnMenuItemClickListener(this);

            if (!isConferenceCall) {
                menu.add(ContextMenu.NONE, R.id.freeme_menu_item_ipcall, ContextMenu.NONE,
                        R.string.call_ip_dial).setOnMenuItemClickListener(this);
            }

            menu.add(ContextMenu.NONE, R.id.freeme_menu_item_send_sms, ContextMenu.NONE,
                    R.string.send_message).setOnMenuItemClickListener(this);
        }
        //*/

        // The edit number before call does not show up if any of the conditions apply:
        // 1) Number cannot be called
        // 2) Number is the voicemail number
        // 3) Number is a SIP address

        if (PhoneNumberUtil.canPlaceCallsTo(number, numberPresentation)
                && !mCallLogCache.isVoicemailNumber(accountHandle, number)
                //*/ freeme.zhaozehong, 04/07/17. conference Call
                && !isConferenceCall
                //*/
                && !PhoneNumberUtil.isSipNumber(number)) {
            menu.add(ContextMenu.NONE, R.id.context_menu_edit_before_call, ContextMenu.NONE,
                    R.string.action_edit_number_before_call)
                    .setOnMenuItemClickListener(this);
        }

        //*/ freeme.zhaozehong, 04/07/17. copy item
        if (!isConferenceCall) {
            menu.add(ContextMenu.NONE, R.id.context_menu_copy_to_clipboard, ContextMenu.NONE,
                    R.string.action_copy_number_text)
                    .setOnMenuItemClickListener(this);
        }
        //*/

        //*/ freeme.zhaozehong, 04/07/17. create contact
        String lookup = UriUtils.getLookupKeyFromUri(info.lookupUri);
        if (TextUtils.isEmpty(lookup) && !isConferenceCall) {
            menu.add(ContextMenu.NONE, R.id.freeme_menu_item_add_new_contact, ContextMenu.NONE,
                    R.string.search_shortcut_create_new_contact)
                    .setOnMenuItemClickListener(this);

            menu.add(ContextMenu.NONE, R.id.freeme_menu_item_add_exist_contact, ContextMenu.NONE,
                    R.string.search_shortcut_add_to_contact)
                    .setOnMenuItemClickListener(this);
        }
        //*/

        if (callType == CallLog.Calls.VOICEMAIL_TYPE
                && phoneCallDetailsViews.voicemailTranscriptionView.length() > 0) {
            menu.add(ContextMenu.NONE, R.id.context_menu_copy_transcript_to_clipboard,
                    ContextMenu.NONE, R.string.copy_transcript_text)
                    .setOnMenuItemClickListener(this);
        }

        /*/freeme.zhangjunjian, 20171020. reset condition to block phone number
        if (FilteredNumberCompat.canAttemptBlockOperations(mContext)
                && FilteredNumbersUtil.canBlockNumber(mContext, number, countryIso)) {
        /*/
        if (FilteredNumberCompat.canAttemptBlockOperations(mContext)
                && !isConferenceCall
                && PhoneNumberUtil.canPlaceCallsTo(number, numberPresentation)) {
        //*/
            mFilteredNumberAsyncQueryHandler.isBlockedNumber(
                    new FilteredNumberAsyncQueryHandler.OnCheckBlockedListener() {
                        @Override
                        public void onCheckComplete(Integer id) {
                            blockId = id;
                            int blockTitleId = blockId == null ? R.string.action_block_number
                                    : R.string.action_unblock_number;
                            final MenuItem blockItem = menu.add(
                                    ContextMenu.NONE,
                                    R.id.context_menu_block_number,
                                    ContextMenu.NONE,
                                    blockTitleId);
                            blockItem.setOnMenuItemClickListener(
                                    CallLogListItemViewHolder.this);
                        }
                    }, number, countryIso);
        }

        //*/ freeme.zhaozehong, 04/07/17. delete item
        menu.add(ContextMenu.NONE, R.id.freeme_menu_item_delete_calllog, ContextMenu.NONE,
                R.string.delete_from_call_log_list)
                .setOnMenuItemClickListener(this);
        //*/

        Logger.logScreenView(ScreenEvent.CALL_LOG_CONTEXT_MENU, (Activity) mContext);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int resId = item.getItemId();
        if (resId == R.id.context_menu_block_number) {
            /**
             * M:find parent view for block number dialog.
             * Use parent view to avoid current listitem view be recycled during scroling.@{
             * */
            View v = ((Activity) mContext).findViewById(R.id.floating_action_button_container);
            int parentViewId = (v == null) ? ((View)rootView.getParent()).getId()
                    : R.id.floating_action_button_container;
            /**@}*/
            FilteredNumberCompat
                    .showBlockNumberDialogFlow(mContext.getContentResolver(), blockId, number,
                            countryIso, displayNumber,
                            parentViewId/**M:R.id.floating_action_button_container*/,
                            ((Activity) mContext).getFragmentManager(),
                            mFilteredNumberDialogCallback);
            return true;
        } else if (resId == R.id.context_menu_copy_to_clipboard) {
            ClipboardUtils.copyText(mContext, null, number, true);
            return true;
        } else if (resId == R.id.context_menu_copy_transcript_to_clipboard) {
            ClipboardUtils.copyText(mContext, null,
                    phoneCallDetailsViews.voicemailTranscriptionView.getText(), true);
            return true;
        } else if (resId == R.id.context_menu_edit_before_call) {
            final Intent intent = new Intent(
                    Intent.ACTION_DIAL, CallUtil.getCallUri(number));
            intent.setClass(mContext, DialtactsActivity.class);
            DialerUtils.startActivityWithErrorToast(mContext, intent);
            return true;
        }
        //*/ freeme.zhaozehong, 04/07/17. for new item
        switch (resId) {
            case R.id.freeme_menu_item_call:
                call();
                return true;
            case R.id.freeme_menu_item_ipcall: {
                Intent intent = IntentUtil.getCallIntent(IntentUtil.getCallUri(number),
                        Call.LogState.INITIATION_CALL_LOG, Constants.DIAL_NUMBER_INTENT_IP);
                DialerUtils.startActivityWithErrorToast(mContext, intent);
                return true;
            }
            case R.id.freeme_menu_item_send_sms: {
                String smsNumber = number;
                if (confCallNumbers != null && confCallNumbers.size() > 0) {
                    boolean isFirst = true;
                    StringBuffer buffer = new StringBuffer();
                    for (String num : confCallNumbers) {
                        if (!isFirst) {
                            buffer.append(",");
                        }
                        isFirst = false;
                        buffer.append(num);
                    }
                    smsNumber = buffer.toString();
                }
                Intent intent = IntentUtil.getSendSmsIntent(smsNumber);
                DialerUtils.startActivityWithErrorToast(mContext, intent);
                return true;
            }
            case R.id.freeme_menu_item_add_new_contact: {
                Intent intent = IntentUtil.getNewContactIntent(number);
                DialerUtils.startActivityWithErrorToast(mContext, intent);
                return true;
            }
            case R.id.freeme_menu_item_add_exist_contact: {
                Intent intent = IntentUtil.getAddToExistingContactIntent(number);
                DialerUtils.startActivityWithErrorToast(mContext, intent);
                return true;
            }
            case R.id.freeme_menu_item_delete_calllog:
                deleteCallLogs();
                return true;
        }
        //*/
        return false;
    }

    //*/ freeme.zhaozehong, 04/07/17. call
    public void call() {
        if (PhoneNumberUtil.canPlaceCallsTo(number, numberPresentation)) {
            boolean isVoicemailNumber = mCallLogCache.isVoicemailNumber(accountHandle, number);
            IntentProvider provider;
            if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                provider = IntentProvider.getSuggestedReturnCallIntentProvider(
                        number + postDialDigits, accountHandle);
            } else if (isVoicemailNumber) {
                provider = IntentProvider.getReturnVoicemailCallIntentProvider();
            } else {
                provider = IntentProvider.getReturnCallIntentProvider(number + postDialDigits);
            }
            if (DialerFeatureOptions.isImsCallSupport()
                    && PhoneNumberHelper.isUriNumber(number)
                    && PhoneAccountUtils.isSubScriptionAccount(mContext, accountHandle)) {
                provider = IntentProvider.getReturnIMSCallIntentProvider(number + postDialDigits);
            }
            if (confCallNumbers != null && DialerVolteUtils.isVolteConfCallEnable(mContext)) {
                provider = IntentProvider.getReturnVolteConfCallIntentProvider(confCallNumbers);
            }
            Intent intent = provider != null ? provider.getIntent(mContext) : null;
            if (intent != null) {
                DialerUtils.startActivityWithErrorToast(mContext, intent);
            }
        }
    }
    //*/

    //*/ freeme.zhaozehong, 04/07/17. delete call logs
    private void deleteCallLogs() {
        if (!TextUtils.isEmpty(voicemailUri)) {
            CallLogAsyncTaskUtil.deleteVoicemail(mContext, Uri.parse(voicemailUri), null);
            return;
        }

        final StringBuilder callIdsBuffer = new StringBuilder();
        boolean isFirst = true;
        for (Uri callUri : getCallLogEntryUris()) {
            if (!isFirst) {
                callIdsBuffer.append(",");
            }
            isFirst = false;
            callIdsBuffer.append(ContentUris.parseId(callUri));
        }
        CallLogAsyncTaskUtil.deleteCalls(mContext, callIdsBuffer.toString(), null);
    }

    private Uri[] getCallLogEntryUris() {
        final int numIds = callIds == null ? 0 : callIds.length;
        final Uri[] uris = new Uri[numIds];
        for (int index = 0; index < numIds; ++index) {
            if (DialerFeatureOptions.CALL_LOG_UNION_QUERY) {
                uris[index] = ContentUris.withAppendedId(
                        Uri.parse("content://call_log/callsjoindataview").buildUpon()
                                .appendQueryParameter(Calls.ALLOW_VOICEMAILS_PARAM_KEY, "true")
                                .build(),
                        callIds[index]);
            } else {
                uris[index] = ContentUris.withAppendedId(TelecomUtil.getCallLogUri(mContext),
                        callIds[index]);
            }
        }
        return uris;
    }
    //*/

    /**
     * Configures the action buttons in the expandable actions ViewStub. The ViewStub is not
     * inflated during initial binding, so click handlers, tags and accessibility text must be set
     * here, if necessary.
     */
    public void inflateActionViewStub() {
        ViewStub stub = (ViewStub) rootView.findViewById(R.id.call_log_entry_actions_stub);
        if (stub != null) {
            actionsView = stub.inflate();

            voicemailPlaybackView = (VoicemailPlaybackLayout) actionsView
                    .findViewById(R.id.voicemail_playback_layout);
            if (isArchiveTab) {
                voicemailPlaybackView.hideArchiveButton();
            }


            callButtonView = actionsView.findViewById(R.id.call_action);
            callButtonView.setOnClickListener(this);

            videoCallButtonView = actionsView.findViewById(R.id.video_call_action);
            videoCallButtonView.setOnClickListener(this);

            createNewContactButtonView = actionsView.findViewById(R.id.create_new_contact_action);
            createNewContactButtonView.setOnClickListener(this);

            addToExistingContactButtonView =
                    actionsView.findViewById(R.id.add_to_existing_contact_action);
            addToExistingContactButtonView.setOnClickListener(this);

            sendMessageView = actionsView.findViewById(R.id.send_message_action);
            sendMessageView.setOnClickListener(this);

            detailsButtonView = actionsView.findViewById(R.id.details_action);
            detailsButtonView.setOnClickListener(this);

            /** M: [IP Dial] add IP Dial @{ */
            ipDialButtonView = actionsView.findViewById(R.id.ipdial_action);
            ipDialButtonView.setOnClickListener(this);
            /** @} */

            callWithNoteButtonView = actionsView.findViewById(R.id.call_with_note_action);
            callWithNoteButtonView.setOnClickListener(this);

            mExtendedBlockingViewStub =
                    (ViewStub) actionsView.findViewById(R.id.extended_blocking_actions_container);
        }

        bindActionButtons();
    }

    private void updatePrimaryActionButton(boolean isExpanded) {
        if (!TextUtils.isEmpty(voicemailUri)) {
            // Treat as voicemail list item; show play button if not expanded.
            if (!isExpanded) {
                primaryActionButtonView.setImageResource(R.drawable.ic_play_arrow_24dp);
                primaryActionButtonView.setContentDescription(TextUtils.expandTemplate(
                        mContext.getString(R.string.description_voicemail_action),
                        nameOrNumber));
                primaryActionButtonView.setVisibility(View.VISIBLE);
            } else {
                primaryActionButtonView.setVisibility(View.GONE);
            }
        } else {
            // Treat as normal list item; show call button, if possible.
            if (PhoneNumberUtil.canPlaceCallsTo(number, numberPresentation)) {
                boolean isVoicemailNumber =
                        mCallLogCache.isVoicemailNumber(accountHandle, number);
                /// M: [Suggested Account] Supporting suggested account @{
                /// Give all the calls a suggestion first.
                if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                    primaryActionButtonView.setTag(IntentProvider
                            .getSuggestedReturnCallIntentProvider(number + postDialDigits,
                                    accountHandle));
                /// @}
                } else if (isVoicemailNumber) {
                    // Call to generic voicemail number, in case there are multiple accounts.
                    primaryActionButtonView.setTag(
                            IntentProvider.getReturnVoicemailCallIntentProvider());
                } else {
                    primaryActionButtonView.setTag(IntentProvider
                            .getReturnCallIntentProvider(number + postDialDigits));
                }
                /** M: [IMS Call] For Volte IMS call @{ */
                if (DialerFeatureOptions.isImsCallSupport()
                        && PhoneNumberHelper.isUriNumber(number)
                        && PhoneAccountUtils.isSubScriptionAccount(mContext, accountHandle)) {
                    primaryActionButtonView.setTag(
                            IntentProvider.getReturnIMSCallIntentProvider(number + postDialDigits));
                }
                /** @} */

                primaryActionButtonView.setContentDescription(TextUtils.expandTemplate(
                        mContext.getString(R.string.description_call_action),
                        nameOrNumber));
                /*/ freeme.zhaozehong, 03/07/17. reset action icon
                primaryActionButtonView.setImageResource(R.drawable.ic_call_24dp);
                /*/
                primaryActionButtonView.setImageResource(R.drawable.freeme_calllog_details);
                //*/
                primaryActionButtonView.setVisibility(View.VISIBLE);
                /** M: [VoLTE ConfCallLog] For Volte Conference callLog @{ */
                if (confCallNumbers != null) {
                    if (DialerVolteUtils.isVolteConfCallEnable(mContext)) {
                        primaryActionButtonView.setTag(IntentProvider
                                .getReturnVolteConfCallIntentProvider(confCallNumbers));
                    } else {
                        primaryActionButtonView.setTag(null);
                        primaryActionButtonView.setVisibility(View.GONE);
                    }
                }
                /** @} */
            } else {
                primaryActionButtonView.setTag(null);
                primaryActionButtonView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Binds text titles, click handlers and intents to the voicemail, details and callback action
     * buttons.
     */
    private void bindActionButtons() {
        /** M: [VoLTE ConfCallLog] Only show call detail button for conference callLog @{ */
        if (confCallNumbers != null) {
            callButtonView.setVisibility(View.GONE);
            videoCallButtonView.setVisibility(View.GONE);
            voicemailPlaybackView.setVisibility(View.GONE);
            createNewContactButtonView.setVisibility(View.GONE);
            addToExistingContactButtonView.setVisibility(View.GONE);
            sendMessageView.setVisibility(View.GONE);
            ipDialButtonView.setVisibility(View.GONE);
            callWithNoteButtonView.setVisibility(View.GONE);
            detailsButtonView.setVisibility(View.VISIBLE);
            detailsButtonView.setTag(
                    IntentProvider.getCallDetailIntentProvider(
                            rowId, callIds, null, true));
            return;
        } else {
            // Other buttons will change its visible at below code of this
            // method except sendMessageView. So, make it visible.
            sendMessageView.setVisibility(View.VISIBLE);
        }
        /** @} */

        boolean canPlaceCallToNumber =
                PhoneNumberUtil.canPlaceCallsTo(number, numberPresentation);

        if (!TextUtils.isEmpty(voicemailUri) && canPlaceCallToNumber) {
            /// M: [Suggested Account] Supporting suggested account @{
            if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                callButtonView.setTag(IntentProvider
                        .getSuggestedReturnCallIntentProvider(number,
                                accountHandle));
            } else {
                callButtonView.setTag(IntentProvider
                        .getReturnCallIntentProvider(number));
            }
            /// @}
            ((TextView) callButtonView.findViewById(R.id.call_action_text))
                    .setText(TextUtils.expandTemplate(
                            mContext.getString(R.string.call_log_action_call),
                            nameOrNumber));
            TextView callTypeOrLocationView = ((TextView) callButtonView.findViewById(
                    R.id.call_type_or_location_text));
            if (callType == Calls.VOICEMAIL_TYPE && !TextUtils.isEmpty(callTypeOrLocation)) {
                callTypeOrLocationView.setText(callTypeOrLocation);
                callTypeOrLocationView.setVisibility(View.VISIBLE);
            } else {
                callTypeOrLocationView.setVisibility(View.GONE);
            }
            callButtonView.setVisibility(View.VISIBLE);
        } else {
            callButtonView.setVisibility(View.GONE);
        }

        // If one of the calls had video capabilities, show the video call button.
        // if (mCallLogCache.isVideoEnabled() && canPlaceCallToNumber &&
        //        phoneCallDetailsViews.callTypeIcons.isVideoShown()) {
        /// M: using plugin API to override the video button showing logic.
        /// NOTE: the parameters should be passed as the index defined in ICallLogExtension
        boolean isVideoButtonEnabled =
                ExtensionManager.getInstance().getCallLogExtension().isVideoButtonEnabled(
                mCallLogCache.isVideoEnabled(), canPlaceCallToNumber,
                phoneCallDetailsViews.callTypeIcons.isVideoShown(),
                phoneCallDetailsViews.callTypeIcons,
                number, info != null ? info.lookupUri : null);
        if (isVideoButtonEnabled) {
            /// M: [Suggested Account] Supporting suggested account @{
            if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                videoCallButtonView.setTag(IntentProvider
                        .getSuggestedReturnVideoCallIntentProvider(number,
                                accountHandle));
            } else {
                videoCallButtonView.setTag(IntentProvider
                        .getReturnVideoCallIntentProvider(number));
            }
            /// @}
            videoCallButtonView.setVisibility(View.VISIBLE);
        } else {
            videoCallButtonView.setVisibility(View.GONE);
        }

        // For voicemail calls, show the voicemail playback layout; hide otherwise.
        if (callType == Calls.VOICEMAIL_TYPE && mVoicemailPlaybackPresenter != null
                && !TextUtils.isEmpty(voicemailUri)) {
            voicemailPlaybackView.setVisibility(View.VISIBLE);

            Uri uri = Uri.parse(voicemailUri);
            mVoicemailPlaybackPresenter.setPlaybackView(
                    voicemailPlaybackView, uri, mVoicemailPrimaryActionButtonClicked);
            mVoicemailPrimaryActionButtonClicked = false;
            // Only mark voicemail as read when not in archive tab
            if (!isArchiveTab) {
                CallLogAsyncTaskUtil.markVoicemailAsRead(mContext, uri);
            }
        } else {
            voicemailPlaybackView.setVisibility(View.GONE);
        }

        if (callType == Calls.VOICEMAIL_TYPE) {
            detailsButtonView.setVisibility(View.GONE);
        } else {
            detailsButtonView.setVisibility(View.VISIBLE);
            detailsButtonView.setTag(
                    IntentProvider.getCallDetailIntentProvider(rowId, callIds, null));
        }

        if (info != null && UriUtils.isEncodedContactUri(info.lookupUri)) {
            createNewContactButtonView.setTag(IntentProvider.getAddContactIntentProvider(
                    info.lookupUri, info.name, info.number, info.type, true /* isNewContact */));
            createNewContactButtonView.setVisibility(View.VISIBLE);

            addToExistingContactButtonView.setTag(IntentProvider.getAddContactIntentProvider(
                    info.lookupUri, info.name, info.number, info.type, false /* isNewContact */));
            addToExistingContactButtonView.setVisibility(View.VISIBLE);
        } else {
            createNewContactButtonView.setVisibility(View.GONE);
            addToExistingContactButtonView.setVisibility(View.GONE);
        }

        if (canPlaceCallToNumber) {
            sendMessageView.setTag(IntentProvider.getSendSmsIntentProvider(number));
            sendMessageView.setVisibility(View.VISIBLE);
        } else {
            sendMessageView.setVisibility(View.GONE);
        }

        /** M: [IP Dial] Add IP Dial @{ */
        if (DialerFeatureOptions.IP_PREFIX && canPlaceCallToNumber
                && !PhoneNumberHelper.isUriNumber(number)) {
            /// M: [Suggested Account] Supporting suggested account @{
            if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                ipDialButtonView.setTag(IntentProvider
                        .getSuggestedIpDialCallIntentProvider(number, accountHandle));
            } else {
                ipDialButtonView.setTag(IntentProvider
                        .getIpDialCallIntentProvider(number));
            }
            /// @}
            ipDialButtonView.setVisibility(View.VISIBLE);
        } else {
            ipDialButtonView.setVisibility(View.GONE);
        }
        /** @} */
        mCallLogListItemHelper.setActionContentDescriptions(this);

        boolean supportsCallSubject =
                mCallLogCache.doesAccountSupportCallSubject(accountHandle);
        boolean isVoicemailNumber =
                mCallLogCache.isVoicemailNumber(accountHandle, number);
        callWithNoteButtonView.setVisibility(
                supportsCallSubject && !isVoicemailNumber ? View.VISIBLE : View.GONE);

        if(mExtendedBlockingButtonRenderer != null){
            List<View> completeLogListItems = Lists.newArrayList(
                    createNewContactButtonView,
                    addToExistingContactButtonView,
                    sendMessageView,
                    callButtonView,
                    callWithNoteButtonView,
                    detailsButtonView,
                    voicemailPlaybackView);

            List<View> blockedNumberVisibleViews = Lists.newArrayList(detailsButtonView);
            List<View> extendedBlockingVisibleViews = Lists.newArrayList(detailsButtonView);

            ExtendedBlockingButtonRenderer.ViewHolderInfo viewHolderInfo =
                    new ExtendedBlockingButtonRenderer.ViewHolderInfo(
                            completeLogListItems,
                            extendedBlockingVisibleViews,
                            blockedNumberVisibleViews,
                            number,
                            countryIso,
                            nameOrNumber.toString(),
                            displayNumber);
            mExtendedBlockingButtonRenderer.setViewHolderInfo(viewHolderInfo);

            mExtendedBlockingButtonRenderer.render(mExtendedBlockingViewStub);
        }

       ///M: Plug-in call to customize Action buttons
        ExtensionManager.getInstance().getCallLogExtension().
                                           customizeBindActionButtons(this);
    }

    /**
     * Show or hide the action views, such as voicemail, details, and add contact.
     *
     * If the action views have never been shown yet for this view, inflate the view stub.
     */
    public void showActions(boolean show) {
        showOrHideVoicemailTranscriptionView(show);

        if (show) {
            // Inflate the view stub if necessary, and wire up the event handlers.
            inflateActionViewStub();

            actionsView.setVisibility(View.VISIBLE);
            actionsView.setAlpha(1.0f);
        } else {
            // When recycling a view, it is possible the actionsView ViewStub was previously
            // inflated so we should hide it in this case.
            if (actionsView != null) {
                actionsView.setVisibility(View.GONE);
            }
        }

        updatePrimaryActionButton(show);
    }

    public void showOrHideVoicemailTranscriptionView(boolean isExpanded) {
        if (callType != Calls.VOICEMAIL_TYPE) {
            return;
        }

        final TextView view = phoneCallDetailsViews.voicemailTranscriptionView;
        if (!isExpanded || TextUtils.isEmpty(view.getText())) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
    }

    public void updatePhoto() {
        quickContactView.assignContactUri(info.lookupUri);

        final boolean isVoicemail = mCallLogCache.isVoicemailNumber(accountHandle, number);
        int contactType = ContactPhotoManager.TYPE_DEFAULT;
        if (isVoicemail) {
            contactType = ContactPhotoManager.TYPE_VOICEMAIL;
        } else if (isBusiness) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        }
        /// M: [VoLTE ConfCallLog] Show Volte Conference contact icon @{
        final boolean isConference = confCallNumbers != null;
        if (isConference) {
            contactType = ContactPhotoManager.TYPE_CONFERENCE_CALL;
            quickContactView.assignContactUri(null);
        }
        /// @}

        final String lookupKey = isConference ? null : UriUtils.getLookupKeyFromUri(info.lookupUri);
        //*/ freeme.zhaozehong, 05/07/17. set phone number for click avatar
        if (quickContactView instanceof FreemeQuickContactBadge) {
            ((FreemeQuickContactBadge) quickContactView).assignPhoneNumber(
                    isConference || !TextUtils.isEmpty(info.name) ? null : number);
        }
        //*/
        final String displayName = TextUtils.isEmpty(info.name) ? displayNumber : info.name;
        final DefaultImageRequest request = new DefaultImageRequest(
                displayName, lookupKey, contactType, true /* isCircular */);

        /// M: if this contact is SIM contact,call log need show SIM icon @{
        if (info != null) {
            request.subId = info.contactSimId;
            request.photoId = info.isSdnContact;
        }
        /// @}

        /// M: if is conference call, just show conference contact photo
        if (info.photoId == 0 && info.photoUri != null && !isConference) {
            ContactPhotoManager.getInstance(mContext).loadPhoto(quickContactView, info.photoUri,
                    mPhotoSize, false /* darkTheme */, true /* isCircular */, request);
        } else {
            ContactPhotoManager.getInstance(mContext).loadThumbnail(quickContactView,
                    isConference ? 0 : info.photoId,
                    false /* darkTheme */, true /* isCircular */, request);
        }

        if (mExtendedBlockingButtonRenderer != null) {
            mExtendedBlockingButtonRenderer.updatePhotoAndLabelIfNecessary(
                    number,
                    countryIso,
                    quickContactView,
                    phoneCallDetailsViews.callLocationAndDate);
        }
    }

    //*/ freeme.zhangjunjian, 17-7-28, add for call log
    public int callLogsFilter;
    //*/
    @Override
    public void onClick(View view) {
        //*/ freeme.zhaozehong, 03/07/17. show call detail
        if (view.getId() == R.id.primary_action_button) {
            IntentProvider provider = IntentProvider.getCallDetailIntentProvider(
                    new String[]{number}, callIds,
                    confCallNumbers != null && DialerVolteUtils.isVolteConfCallEnable(mContext),
                    callLogsFilter);
            final Intent intent = provider.getIntent(mContext);
            if (intent != null) {
                DialerUtils.startActivityWithErrorToast(mContext, intent);
            }
        } else
        //*/
        if (view.getId() == R.id.primary_action_button && !TextUtils.isEmpty(voicemailUri)) {
            mVoicemailPrimaryActionButtonClicked = true;
            mExpandCollapseListener.onClick(primaryActionView);
        } else if (view.getId() == R.id.call_with_note_action) {
            CallSubjectDialog.start(
                    (Activity) mContext,
                    info.photoId,
                    info.photoUri,
                    info.lookupUri,
                    (String) nameOrNumber /* top line of contact view in call subject dialog */,
                    isBusiness,
                    number,
                    TextUtils.isEmpty(info.name) ? null : displayNumber, /* second line of contact
                                                                           view in dialog. */
                    numberType, /* phone number type (e.g. mobile) in second line of contact view */
                    accountHandle);
        } else {
            final IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                final Intent intent = intentProvider.getIntent(mContext);

                /// M: for Op01 Plug-in about callog CDD @{
                ExtensionManager.getInstance().getCallLogExtension()
                            .changeIntentContent(intent, callType);
                /// @}

                // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
                if (intent != null) {
                    DialerUtils.startActivityWithErrorToast(mContext, intent);
                }
            }
        }
    }

    @NeededForTesting
    public static CallLogListItemViewHolder createForTest(Context context) {
        Resources resources = context.getResources();
        CallLogCache callLogCache =
                CallLogCache.getCallLogCache(context);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                context, resources, callLogCache);

        CallLogListItemViewHolder viewHolder = new CallLogListItemViewHolder(
                context,
                null,
                null /* expandCollapseListener */,
                callLogCache,
                new CallLogListItemHelper(phoneCallDetailsHelper, resources, callLogCache),
                null /* voicemailPlaybackPresenter */,
                null /* filteredNumberAsyncQueryHandler */,
                null /* filteredNumberDialogCallback */,
                new View(context),
                /*/ freeme.zhaozehong, 05/07/17. reset avatar view
                new QuickContactBadge(context),
                /*/
                new FreemeQuickContactBadge(context),
                //*/
                new LinearLayout(context) /*M: primaryActionView */,
                PhoneCallDetailsViews.createForTest(context),
                new CardView(context),
                new TextView(context),
                new ImageView(context),
                false);
        viewHolder.detailsButtonView = new TextView(context);
        viewHolder.actionsView = new View(context);
        viewHolder.voicemailPlaybackView = new VoicemailPlaybackLayout(context);
        viewHolder.workIconView = new ImageButton(context);
        return viewHolder;
    }
}
