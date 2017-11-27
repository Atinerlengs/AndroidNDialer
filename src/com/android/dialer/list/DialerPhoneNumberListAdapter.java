package com.android.dialer.list;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DialerSearch;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.format.TextHighlighter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.R;
import com.android.dialer.calllog.CallTypeIconsView;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.calllog.PhoneNumberDisplayUtil;
import com.android.dialer.calllog.calllogcache.CallLogCache;
import com.android.dialer.util.PhoneNumberUtil;

import com.mediatek.common.MPlugin;
import com.mediatek.common.telephony.ICallerInfoExt;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.DialerFeatureOptions;
import com.mediatek.dialer.util.DialerSearchUtils;
//*/ freeme.zhaozehong, 06/07/17. for freemeOS
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import com.freeme.contacts.common.utils.FreemeYellowPageUtils;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
//*/

/**
 * {@link PhoneNumberListAdapter} with the following added shortcuts, that are displayed as list
 * items:
 * 1) Directly calling the phone number query
 * 2) Adding the phone number query to a contact
 *
 * These shortcuts can be enabled or disabled to toggle whether or not they show up in the
 * list.
 */
public class DialerPhoneNumberListAdapter extends PhoneNumberListAdapter {

    private String mFormattedQueryString;
    private String mCountryIso;

    public final static int SHORTCUT_INVALID = -1;
    public final static int SHORTCUT_DIRECT_CALL = 0;
    public final static int SHORTCUT_CREATE_NEW_CONTACT = 1;
    public final static int SHORTCUT_ADD_TO_EXISTING_CONTACT = 2;
    public final static int SHORTCUT_SEND_SMS_MESSAGE = 3;
    public final static int SHORTCUT_MAKE_VIDEO_CALL = 4;
    public final static int SHORTCUT_BLOCK_NUMBER = 5;
    ///M: [IMS Call] For IMS Call
    public final static int SHORTCUT_MAKE_IMS_CALL = 6;

    ///M: [IMS Call] Add IMS Call
    public final static int SHORTCUT_COUNT = 7;

    private final boolean[] mShortcutEnabled = new boolean[SHORTCUT_COUNT];

    private final BidiFormatter mBidiFormatter = BidiFormatter.getInstance();
    private boolean mVideoCallingEnabled = false;

    public DialerPhoneNumberListAdapter(Context context) {
        super(context);

        mCountryIso = GeoUtil.getCurrentCountryIso(context);

        /// M: [MTK Dialer Search] @{
        mPhoneNumberUtils = CallLogCache.getCallLogCache(context);
        if (DialerFeatureOptions.isDialerSearchEnabled()) {
            initResources(context);
        }
        /// @}
        mVideoCallingEnabled = CallUtil.isVideoEnabled(context);
    }

    @Override
    public int getCount() {
        return super.getCount() + getShortcutCount();
    }

    /**
     * @return The number of enabled shortcuts. Ranges from 0 to a maximum of SHORTCUT_COUNT
     */
    public int getShortcutCount() {
        int count = 0;
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            if (mShortcutEnabled[i]) count++;
        }
        return count;
    }

    public void disableAllShortcuts() {
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            mShortcutEnabled[i] = false;
        }
    }

    @Override
    public int getItemViewType(int position) {
        //*/ freeme.zhaozehong, 05/07/17. for show freeme yellow page data
        if (isFreemeYellowPageTitle(position)) {
            return VIEW_TYPE_CALL_YELLOW_TITLE;
        }
        //*/
        final int shortcut = getShortcutTypeFromPosition(position);
        if (shortcut >= 0) {
            // shortcutPos should always range from 1 to SHORTCUT_COUNT
            return super.getViewTypeCount() + shortcut;
        } else {
            return super.getItemViewType(position);
        }
    }

    @Override
    public int getViewTypeCount() {
        // Number of item view types in the super implementation + 2 for the 2 new shortcuts
        return super.getViewTypeCount() + SHORTCUT_COUNT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        //*/ freeme.zhaozehong, 06/07/17. show freem yellowpage title label
        if (isFreemeYellowPageTitle(position)) {
            if (convertView == null) {
                convertView = View.inflate(mContext, R.layout.freeme_dialer_search_space, null);
            }
            return convertView;
        }
        //*/
        final int shortcutType = getShortcutTypeFromPosition(position);
        if (shortcutType >= 0) {
            if (convertView != null) {
                assignShortcutToView((ContactListItemView) convertView, shortcutType);
                return convertView;
            } else {
                final ContactListItemView v = new ContactListItemView(getContext(), null,
                        mVideoCallingEnabled);
                assignShortcutToView(v, shortcutType);
                return v;
            }
        } else {
            return super.getView(position, convertView, parent);
        }
    }

    /**
     * @param position The position of the item
     * @return The enabled shortcut type matching the given position if the item is a
     * shortcut, -1 otherwise
     */
    public int getShortcutTypeFromPosition(int position) {
        int shortcutCount = position - super.getCount();
        if (shortcutCount >= 0) {
            // Iterate through the array of shortcuts, looking only for shortcuts where
            // mShortcutEnabled[i] is true
            for (int i = 0; shortcutCount >= 0 && i < mShortcutEnabled.length; i++) {
                if (mShortcutEnabled[i]) {
                    shortcutCount--;
                    if (shortcutCount < 0) return i;
                }
            }
            throw new IllegalArgumentException("Invalid position - greater than cursor count "
                    + " but not a shortcut.");
        }
        return SHORTCUT_INVALID;
    }

    @Override
    public boolean isEmpty() {
        return getShortcutCount() == 0 && super.isEmpty();
    }

    @Override
    public boolean isEnabled(int position) {
        //*/ freeme.zhaozehong, 06/07/17. freeme yellow page title clickable false
        if (isFreemeYellowPageTitle(position)){
            return false;
        }
        //*/
        final int shortcutType = getShortcutTypeFromPosition(position);
        if (shortcutType >= 0) {
            return true;
        } else {
            return super.isEnabled(position);
        }
    }

    private void assignShortcutToView(ContactListItemView v, int shortcutType) {
        CharSequence text;
        final int drawableId;
        final Resources resources = getContext().getResources();
        final String number = getFormattedQueryString();
        switch (shortcutType) {
            case SHORTCUT_DIRECT_CALL:
                text = ContactDisplayUtils.getTtsSpannedPhoneNumber(resources,
                        R.string.search_shortcut_call_number,
                        mBidiFormatter.unicodeWrap(number, TextDirectionHeuristics.LTR));
                drawableId = R.drawable.ic_search_phone;
                /// M: To indetify SIP call
                if (PhoneNumberHelper.isUriNumber(number)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(text);
                    sb.append("(");
                    sb.append(resources.getString(R.string.dialer_call_type_label_sip));
                    sb.append(")");
                    text = sb.toString();
                }
                break;
            case SHORTCUT_CREATE_NEW_CONTACT:
                text = resources.getString(R.string.search_shortcut_create_new_contact);
                drawableId = R.drawable.ic_search_add_contact;
                break;
            case SHORTCUT_ADD_TO_EXISTING_CONTACT:
                text = resources.getString(R.string.search_shortcut_add_to_contact);
                /*/ freeme.zhangjunjian, 17-6-22, changed drawable of Contact_adding
                drawableId = R.drawable.ic_person_24dp;
                /*/
                drawableId = R.drawable.ic_person_add_24dp;
                //*/
                break;
            case SHORTCUT_SEND_SMS_MESSAGE:
                text = resources.getString(R.string.search_shortcut_send_sms_message);
                drawableId = R.drawable.ic_message_24dp;
                break;
            case SHORTCUT_MAKE_VIDEO_CALL:
                text = resources.getString(R.string.search_shortcut_make_video_call);
                drawableId = R.drawable.ic_videocam;
                break;
            case SHORTCUT_BLOCK_NUMBER:
                text = resources.getString(R.string.search_shortcut_block_number);
                drawableId = R.drawable.ic_not_interested_googblue_24dp;
                break;
            ///M: [IMS Call] For IMS Call @{
            case SHORTCUT_MAKE_IMS_CALL:
                StringBuilder sb = new StringBuilder();
                text = resources.getString(
                        R.string.search_shortcut_call_number,
                        mBidiFormatter.unicodeWrap(number, TextDirectionHeuristics.LTR));
                sb.append(text);
                sb.append("(");
                sb.append(resources.getString(R.string.imsCallLabelsGroup));
                sb.append(")");
                drawableId = R.drawable.ic_search_phone;
                text = sb.toString();
                break;
            /// @}
            default:
                throw new IllegalArgumentException("Invalid shortcut type");
        }
        v.setDrawableResource(drawableId);
        v.setDisplayName(text);
        v.setPhotoPosition(super.getPhotoPosition());
        v.setAdjustSelectionBoundsEnabled(false);
    }

    /**
     * @return True if the shortcut state (disabled vs enabled) was changed by this operation
     */
    public boolean setShortcutEnabled(int shortcutType, boolean visible) {
        final boolean changed = mShortcutEnabled[shortcutType] != visible;
        mShortcutEnabled[shortcutType] = visible;
        return changed;
    }

    public String getFormattedQueryString() {
        return mFormattedQueryString;
    }

    @Override
    public void setQueryString(String queryString) {
        mFormattedQueryString = PhoneNumberUtils.formatNumber(
                PhoneNumberUtils.normalizeNumber(queryString), mCountryIso);
        super.setQueryString(queryString);
    }

    /// M: [MTK Dialer Search] @{
    private final String TAG = "DialerPhoneNumberListAdapter";

    private final int VIEW_TYPE_UNKNOWN = -1;
    private final int VIEW_TYPE_CONTACT = 0;
    private final int VIEW_TYPE_CALL_LOG = 1;
    //*/ freeme.zhaozehong, 05/07/17. for show freeme yellow page data
    private final int VIEW_TYPE_CALL_YELLOW = 2;
    private final int VIEW_TYPE_CALL_YELLOW_TITLE = 3;
    //*/

    private final int NUMBER_TYPE_NORMAL = 0;
    private final int NUMBER_TYPE_UNKNOWN = 1;
    private final int NUMBER_TYPE_VOICEMAIL = 2;
    private final int NUMBER_TYPE_PRIVATE = 3;
    private final int NUMBER_TYPE_PAYPHONE = 4;
    private final int NUMBER_TYPE_EMERGENCY = 5;

    private final int DS_MATCHED_DATA_INIT_POS    = 3;
    private final int DS_MATCHED_DATA_DIVIDER     = 3;

    public final int NAME_LOOKUP_ID_INDEX        = 0;
    public final int CONTACT_ID_INDEX            = 1;
    public final int DATA_ID_INDEX               = 2;
    public final int CALL_LOG_DATE_INDEX         = 3;
    public final int CALL_LOG_ID_INDEX           = 4;
    public final int CALL_TYPE_INDEX             = 5;
    public final int CALL_GEOCODED_LOCATION_INDEX = 6;
    public final int PHONE_ACCOUNT_ID_INDEX                = 7;
    public final int PHONE_ACCOUNT_COMPONENT_NAME_INDEX     = 8;
    public final int PRESENTATION_INDEX          = 9;
    public final int INDICATE_PHONE_SIM_INDEX    = 10;
    public final int CONTACT_STARRED_INDEX       = 11;
    public final int PHOTO_ID_INDEX              = 12;
    public final int SEARCH_PHONE_TYPE_INDEX     = 13;
    public final int SEARCH_PHONE_LABEL_INDEX    = 14;
    public final int NAME_INDEX                  = 15;
    public final int SEARCH_PHONE_NUMBER_INDEX   = 16;
    public final int CONTACT_NAME_LOOKUP_INDEX   = 17;
    public final int IS_SDN_CONTACT              = 18;
    public final int DS_MATCHED_DATA_OFFSETS     = 19;
    public final int DS_MATCHED_NAME_OFFSETS     = 20;

    private ContactPhotoManager mContactPhotoManager;
    private final CallLogCache mPhoneNumberUtils;
    private PhoneNumberDisplayUtil mPhoneNumberHelper;

    private String mUnknownNumber;
    private String mPrivateNumber;
    private String mPayphoneNumber;

    private String mEmergency;

    private String mVoiceMail;

    private HashMap<Integer, Drawable> mCallTypeDrawables = new HashMap<Integer, Drawable>();

    private TextHighlighter mTextHighlighter;

    /**
     * M: bind view for mediatek's search UI.
     * @see com.android.contacts.common.list.PhoneNumberListAdapter
     * #bindView(android.view.View, int, android.database.Cursor, int)
     */
    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        if (this instanceof RegularSearchListAdapter) {
            super.bindView(itemView, partition, cursor, position);
            return;
        }

        final int viewType = getViewType(cursor);
        switch (viewType) {
        case VIEW_TYPE_CONTACT:
            bindContactView(itemView, getContext(), cursor);
            break;
        case VIEW_TYPE_CALL_LOG:
            bindCallLogView(itemView, getContext(), cursor);
            break;
            //*/ freeme.zhaozehong, 05/07/17. for show freeme yellow page data
            case VIEW_TYPE_CALL_YELLOW:
                bindFreemeYellowView(itemView, getContext(), cursor);
                break;
            //*/
        default:
            break;
        }
    }

    /**
     * M: create item view for this feature
     * @see com.android.contacts.common.list.PhoneNumberListAdapter
     * #newView(android.content.Context, int, android.database.Cursor, int, android.view.ViewGroup)
     */
    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        if (this instanceof RegularSearchListAdapter) {
            final ContactListItemView view = (ContactListItemView) super.newView(context, partition,
                    cursor, position, parent);

            /// M: [N Conflict Change] N add Video Call Icon feature
            view.setSupportVideoCallIcon(mVideoCallingEnabled);
            return view;
        }

        View view = View.inflate(context, R.layout.mtk_dialer_search_item_view, null);
        TypedArray a = context.obtainStyledAttributes(null, R.styleable.ContactListItemView);

        view.setPadding(a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_left, 0),
        a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_top, 0),
        a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_right, 0),
        a.getDimensionPixelOffset(
                R.styleable.ContactListItemView_list_item_padding_bottom, 0));

        ViewHolder viewHolder = new ViewHolder();

        viewHolder.quickContactBadge = (QuickContactBadge) view
                .findViewById(R.id.quick_contact_photo);
        viewHolder.name = (TextView) view.findViewById(R.id.name);
        viewHolder.labelAndNumber = (TextView) view.findViewById(R.id.labelAndNumber);
        viewHolder.callInfo = (View) view.findViewById(R.id.call_info);
        viewHolder.callType = (ImageView) view.findViewById(R.id.callType);
        viewHolder.address = (TextView) view.findViewById(R.id.address);
        viewHolder.date = (TextView) view.findViewById(R.id.date);

        viewHolder.accountLabel = (TextView) view.findViewById(R.id.call_account_label);

        view.setTag(viewHolder);
        return view;
    }

    /**
     * M: init UI resources
     * @param context
     */
    private void initResources(Context context) {
        mContactPhotoManager = ContactPhotoManager.getInstance(context);
        mPhoneNumberHelper = new PhoneNumberDisplayUtil();

        mEmergency = context.getResources().getString(R.string.emergencycall);
        mVoiceMail = context.getResources().getString(R.string.voicemail);
        mPrivateNumber = context.getResources().getString(R.string.private_num);
        mPayphoneNumber = context.getResources().getString(R.string.payphone);
        mUnknownNumber = context.getResources().getString(R.string.unknown);

        // 1. incoming 2. outgoing 3. missed 4.voicemail
        // Align drawables of result items in dialer search to AOSP style.
        CallTypeIconsView.Resources resources = new CallTypeIconsView.Resources(context);
        mCallTypeDrawables.put(Calls.INCOMING_TYPE, resources.incoming);
        mCallTypeDrawables.put(Calls.OUTGOING_TYPE, resources.outgoing);
        mCallTypeDrawables.put(Calls.MISSED_TYPE, resources.missed);
        mCallTypeDrawables.put(Calls.VOICEMAIL_TYPE, resources.voicemail);
        /// M: Add reject icon
        mCallTypeDrawables.put(Calls.REJECTED_TYPE, resources.missed);
        /// M: for Plug-in add auto reject icon for dialer search @{
        ExtensionManager.getInstance().getCallLogExtension().addResourceForDialerSearch(
                mCallTypeDrawables);
        /// @}
    }

    /**
     * M: calculate view's type from cursor
     * @param cursor
     * @return type number
     */
    private int getViewType(Cursor cursor) {
        //*/ freeme.zhaozehong, 05/07/17. for show freeme yellow page data
        String label = cursor.getString(SEARCH_PHONE_LABEL_INDEX);
        if (FreemeYellowPageUtils.FREEME_YELLOWPAGE_TITLE_LABEL.equals(label)) {
            return VIEW_TYPE_CALL_YELLOW_TITLE;
        } else if (FreemeYellowPageUtils.FREEME_YELLOWPAGE_DATA_LABEL.equals(label)) {
            return VIEW_TYPE_CALL_YELLOW;
        }
        //*/
        int retval = VIEW_TYPE_UNKNOWN;
        final int contactId = cursor.getInt(CONTACT_ID_INDEX);
        final int callLogId = cursor.getInt(CALL_LOG_ID_INDEX);

        Log.d(TAG, "getViewType: contactId: " + contactId + " ,callLogId: " + callLogId);

        if (contactId > 0) {
            retval = VIEW_TYPE_CONTACT;
        } else if (callLogId > 0) {
            retval = VIEW_TYPE_CALL_LOG;
        }

        return retval;
    }

    /**
     * M: bind contact view from cursor data
     * @param view
     * @param context
     * @param cursor
     */
    private void bindContactView(View view, Context context, Cursor cursor) {

        final ViewHolder viewHolder = (ViewHolder) view.getTag();

        viewHolder.labelAndNumber.setVisibility(View.VISIBLE);
        viewHolder.callInfo.setVisibility(View.GONE);
        viewHolder.accountLabel.setVisibility(View.GONE);

        final String number = cursor.getString(SEARCH_PHONE_NUMBER_INDEX);
        String formatNumber = numberLeftToRight(number);
        if (formatNumber == null) {
            formatNumber = number;
        }

        final int presentation = cursor.getInt(PRESENTATION_INDEX);
        final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
                cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME_INDEX),
                cursor.getString(PHONE_ACCOUNT_ID_INDEX));

        final int numberType = getNumberType(accountHandle, number, presentation);

        final int labelType = cursor.getInt(SEARCH_PHONE_TYPE_INDEX);
        CharSequence label = cursor.getString(SEARCH_PHONE_LABEL_INDEX);
        int subId = cursor.getInt(INDICATE_PHONE_SIM_INDEX);
        // Get type label only if it will not be "Custom" because of an empty label.
        // So IMS contacts search item don't show lable as "Custom".
        if (!(labelType == Phone.TYPE_CUSTOM && TextUtils.isEmpty(label))) {
            /// M: Using new API for AAS phone number label lookup
            label = Phone.getTypeLabel(mContext, labelType, label);
        }
        final CharSequence displayName = cursor.getString(NAME_INDEX);

        Uri contactUri = getContactUri(cursor);
        Log.d(TAG, "bindContactView, contactUri: " + contactUri);

        long photoId = cursor.getLong(PHOTO_ID_INDEX);

        if (numberType == NUMBER_TYPE_VOICEMAIL || numberType == NUMBER_TYPE_EMERGENCY) {
            photoId = 0;
            viewHolder.quickContactBadge.assignContactUri(null);
        } else {
            viewHolder.quickContactBadge.assignContactUri(contactUri);
        }
        viewHolder.quickContactBadge.setOverlay(null);

        if (photoId > 0) {
            mContactPhotoManager.loadThumbnail(viewHolder.quickContactBadge, photoId, false, true,
                    null);
        } else {
            String identifier = cursor.getString(CONTACT_NAME_LOOKUP_INDEX);
            DefaultImageRequest request = new DefaultImageRequest((String) displayName, identifier,
                    true);
            if (subId > 0) {
                request.subId = subId;
                request.photoId = cursor.getInt(IS_SDN_CONTACT);
            }
            mContactPhotoManager.loadThumbnail(viewHolder.quickContactBadge, photoId, false, true,
                    request);
        }

        if (isSpecialNumber(numberType)) {
            if (numberType == NUMBER_TYPE_VOICEMAIL || numberType == NUMBER_TYPE_EMERGENCY) {
                if (numberType == NUMBER_TYPE_VOICEMAIL) {
                    viewHolder.name.setText(mVoiceMail);
                } else {
                    viewHolder.name.setText(mEmergency);
                }

                viewHolder.labelAndNumber.setVisibility(View.VISIBLE);
                String highlight = getNumberHighlight(cursor);
                if (!TextUtils.isEmpty(highlight)) {
                    SpannableStringBuilder style = highlightHyphen(highlight, formatNumber, number);
                    viewHolder.labelAndNumber.setText(style);
               } else {
                   viewHolder.labelAndNumber.setText(formatNumber);
                }
            } else {
                final String convert = specialNumberToString(numberType);
                viewHolder.name.setText(convert);
            }
        } else {
            // empty name ?
            if (!TextUtils.isEmpty(displayName)) {
                // highlight name
                String highlight = getNameHighlight(cursor);
                if (!TextUtils.isEmpty(highlight)) {
                    SpannableStringBuilder style = highlightString(highlight, displayName);
                    viewHolder.name.setText(style);
                    if (isRegularSearch(cursor)) {
                        viewHolder.name.setText(highlightName(highlight, displayName));
                    }
                } else {
                    viewHolder.name.setText(displayName);
                }
                // highlight number
                if (!TextUtils.isEmpty(formatNumber)) {
                    highlight = getNumberHighlight(cursor);
                    if (!TextUtils.isEmpty(highlight)) {
                        SpannableStringBuilder style = highlightHyphen(highlight, formatNumber,
                                number);
                        setLabelAndNumber(viewHolder.labelAndNumber, label, style);
                    } else {
                        setLabelAndNumber(viewHolder.labelAndNumber, label,
                                new SpannableStringBuilder(formatNumber));
                    }
                } else {
                    viewHolder.labelAndNumber.setVisibility(View.GONE);
                }
            } else {
                viewHolder.labelAndNumber.setVisibility(View.GONE);

                // highlight number and set number to name text view
                if (!TextUtils.isEmpty(formatNumber)) {
                    final String highlight = getNumberHighlight(cursor);
                    if (!TextUtils.isEmpty(highlight)) {
                        SpannableStringBuilder style = highlightHyphen(highlight, formatNumber,
                                number);
                        viewHolder.name.setText(style);
                    } else {
                        viewHolder.name.setText(formatNumber);
                    }
                } else {
                    viewHolder.name.setVisibility(View.GONE);
                }
            }
        }

        /// M: add for plug-in @{
        ExtensionManager.getInstance().getDialerSearchExtension()
                .removeCallAccountForDialerSearch(context, view);
        /// @}
    }

    /**
     * M: Bind call log view by cursor data
     * @param view
     * @param context
     * @param cursor
     */
    private void bindCallLogView(View view, Context context, Cursor cursor) {
        final ViewHolder viewHolder = (ViewHolder) view.getTag();

        viewHolder.callInfo.setVisibility(View.VISIBLE);
        viewHolder.labelAndNumber.setVisibility(View.GONE);

        final String number = cursor.getString(SEARCH_PHONE_NUMBER_INDEX);
        String formattedNumber = numberLeftToRight(number);
        if (TextUtils.isEmpty(formattedNumber)) {
            formattedNumber = number;
        }

        final int presentation = cursor.getInt(PRESENTATION_INDEX);
        final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
                cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME_INDEX),
                cursor.getString(PHONE_ACCOUNT_ID_INDEX));

        final int numberType = getNumberType(accountHandle, number, presentation);

        final int type = cursor.getInt(CALL_TYPE_INDEX);
        final long date = cursor.getLong(CALL_LOG_DATE_INDEX);
        final int indicate = cursor.getInt(INDICATE_PHONE_SIM_INDEX);
        String geocode = cursor.getString(CALL_GEOCODED_LOCATION_INDEX);

        // create a temp contact uri for quick contact view.
        Uri contactUri = null;
        if (!TextUtils.isEmpty(number)) {
            contactUri = ContactInfoHelper.createTemporaryContactUri(number);
        }

        int contactType = ContactPhotoManager.TYPE_DEFAULT;
        if (numberType == NUMBER_TYPE_VOICEMAIL) {
            contactType = ContactPhotoManager.TYPE_VOICEMAIL;
            contactUri = null;
        }

        viewHolder.quickContactBadge.assignContactUri(contactUri);
        viewHolder.quickContactBadge.setOverlay(null);

        /// M: [ALPS01963857] keep call log and smart search's avatar in same color @{
        boolean isVoiceNumber = mPhoneNumberUtils.isVoicemailNumber(accountHandle, number);
        String nameForDefaultImage = mPhoneNumberHelper.getDisplayNumber(context, number,
                /// M: [N Conflict Change]TODO:: POST_DIAL_DIGITS colum maybe need add in
                // DialerSearch table. here just set "" for build pass.
                presentation, number, "", isVoiceNumber).toString();
        /// @}

        String identifier = cursor.getString(CONTACT_NAME_LOOKUP_INDEX);
        DefaultImageRequest request = new DefaultImageRequest(nameForDefaultImage, identifier,
                contactType, true);
        mContactPhotoManager.loadThumbnail(viewHolder.quickContactBadge, 0, false, true, request);

        viewHolder.address.setText(geocode);

        if (isSpecialNumber(numberType)) {
            if (numberType == NUMBER_TYPE_VOICEMAIL || numberType == NUMBER_TYPE_EMERGENCY) {
                if (numberType == NUMBER_TYPE_VOICEMAIL) {
                    viewHolder.name.setText(mVoiceMail);
                } else {
                    viewHolder.name.setText(mEmergency);
                }

                String highlight = getNumberHighlight(cursor);
                if (!TextUtils.isEmpty(highlight)) {
                    SpannableStringBuilder style = highlightHyphen(highlight, formattedNumber,
                            number);
                    viewHolder.address.setText(style);
                } else {
                    viewHolder.address.setText(formattedNumber);
                }
            } else {
                final String convert = specialNumberToString(numberType);
                viewHolder.name.setText(convert);
            }
        } else {
            if (!TextUtils.isEmpty(formattedNumber)) {
                String highlight = getNumberHighlight(cursor);
                if (!TextUtils.isEmpty(highlight)) {
                    SpannableStringBuilder style = highlightHyphen(highlight, formattedNumber,
                            number);
                    viewHolder.name.setText(style);
                } else {
                    viewHolder.name.setText(formattedNumber);
                }
            }
        }

        java.text.DateFormat dateFormat = DateFormat.getTimeFormat(context);
        String dateString = dateFormat.format(date);
        viewHolder.date.setText(dateString);

        viewHolder.callType.setImageDrawable(mCallTypeDrawables.get(type));

        final String accountLabel = PhoneAccountUtils.getAccountLabel(context, accountHandle);

        if (!TextUtils.isEmpty(accountLabel)) {
            viewHolder.accountLabel.setText(accountLabel);
            /// M: [ALPS02038899] set visible in case of gone
            viewHolder.accountLabel.setVisibility(View.VISIBLE);
            // Set text color for the corresponding account.
            int color = PhoneAccountUtils.getAccountColor(context, accountHandle);
            if (color == PhoneAccount.NO_HIGHLIGHT_COLOR) {
                int defaultColor = R.color.dialtacts_secondary_text_color;
                viewHolder.accountLabel.setTextColor(context.getResources().getColor(defaultColor));
            } else {
                viewHolder.accountLabel.setTextColor(color);
            }
        } else {
            viewHolder.accountLabel.setVisibility(View.GONE);
        }

        /// M: add for plug-in @{
        ExtensionManager.getInstance().getDialerSearchExtension()
                .setCallAccountForDialerSearch(context, view, accountHandle);
        /// @}
    }

    private int getNumberType(PhoneAccountHandle accountHandle, CharSequence number,
            int presentation) {
        int type = NUMBER_TYPE_NORMAL;
        if (presentation == Calls.PRESENTATION_UNKNOWN) {
            type = NUMBER_TYPE_UNKNOWN;
        } else if (presentation == Calls.PRESENTATION_RESTRICTED) {
            type = NUMBER_TYPE_PRIVATE;
        } else if (presentation == Calls.PRESENTATION_PAYPHONE) {
            type = NUMBER_TYPE_PAYPHONE;
        } else if (mPhoneNumberUtils.isVoicemailNumber(accountHandle, number)) {
            type = NUMBER_TYPE_VOICEMAIL;
        }
        if (PhoneNumberUtil.isLegacyUnknownNumbers(number)) {
            type = NUMBER_TYPE_UNKNOWN;
        }
        return type;
    }

    private Uri getContactUri(Cursor cursor) {
        final String lookup = cursor.getString(CONTACT_NAME_LOOKUP_INDEX);
        final int contactId = cursor.getInt(CONTACT_ID_INDEX);
        return Contacts.getLookupUri(contactId, lookup);
    }

    private boolean isSpecialNumber(int type) {
        return type != NUMBER_TYPE_NORMAL;
    }

    /**
     * M: highlight search result string
     * @param highlight
     * @param target
     * @return
     */
    private SpannableStringBuilder highlightString(String highlight, CharSequence target) {
        SpannableStringBuilder style = new SpannableStringBuilder(target);
        int length = highlight.length();
        final int styleLength = style.length();
        int start = -1;
        int end = -1;
        for (int i = DS_MATCHED_DATA_INIT_POS; i + 1 < length; i += DS_MATCHED_DATA_DIVIDER) {
            start = (int) highlight.charAt(i);
            end = (int) highlight.charAt(i + 1) + 1;
            /// M: If highlight area is invalid, just skip it.
            if (start > styleLength || end > styleLength || start > end) {
                Log.d(TAG, "highlightString, start: " + start + " ,end: " + end
                        + " ,styleLength: " + styleLength);
                break;
            }
            style.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return style;
    }

    /**
     * M: highlight searched result name
     * @param highlight
     * @param target
     * @return
     */
    private CharSequence highlightName(String highlight, CharSequence target) {
        String highlightedPrefix = getUpperCaseQueryString();
        if (highlightedPrefix != null) {
            mTextHighlighter = new TextHighlighter(Typeface.BOLD);
            target =  mTextHighlighter.applyPrefixHighlight(target, highlightedPrefix);
        }
        return target;
    }

    /**
     * M: highlight search result hyphen
     * @param highlight
     * @param target
     * @param origin
     * @return
     */
    private SpannableStringBuilder highlightHyphen(String highlight, String target, String origin) {
        if (target == null) {
            Log.w(TAG, "highlightHyphen target is null");
            return null;
        }
        SpannableStringBuilder style = new SpannableStringBuilder(target);
        ArrayList<Integer> numberHighlightOffset = DialerSearchUtils
                .adjustHighlitePositionForHyphen(target, highlight
                        .substring(DS_MATCHED_DATA_INIT_POS), origin);
        if (numberHighlightOffset != null && numberHighlightOffset.size() > 1) {
            /*/ freeme.zhaozehong, 06/07/17. reset high light
            style.setSpan(new StyleSpan(Typeface.BOLD), numberHighlightOffset.get(0),
                    numberHighlightOffset.get(1) + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            /*/
            int highLightColor = mContext.getResources().getColor(R.color.freeme_color_accent);
            style.setSpan(new ForegroundColorSpan(highLightColor), numberHighlightOffset.get(0),
                    numberHighlightOffset.get(1) + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            //*/
        }
        return style;
    }

    private String getNameHighlight(Cursor cursor) {
        final int index = cursor.getColumnIndex(DialerSearch.MATCHED_NAME_OFFSET);
        return index != -1 ? cursor.getString(index) : null;
    }

    private boolean isRegularSearch(Cursor cursor) {
        final int index = cursor.getColumnIndex(DialerSearch.MATCHED_DATA_OFFSET);
        String regularSearch = (index != -1 ? cursor.getString(index) : null);
        Log.d(TAG, "" + regularSearch);

        return Boolean.valueOf(regularSearch);
    }

    private String getNumberHighlight(Cursor cursor) {
        final int index = cursor.getColumnIndex(DialerSearch.MATCHED_DATA_OFFSET);
        return index != -1 ? cursor.getString(index) : null;
    }

    /**
     * M: set label and number to view
     * @param view
     * @param label
     * @param number
     */
    private void setLabelAndNumber(TextView view, CharSequence label,
            SpannableStringBuilder number) {
        if (PhoneNumberUtils.isUriNumber(number.toString())) {
            view.setText(number);
            return;
        }
        if (TextUtils.isEmpty(label)) {
            view.setText(number);
        } else if (TextUtils.isEmpty(number)) {
            view.setText(label);
        } else {
            number.insert(0, label + " ");
            view.setText(number);
        }
    }

    private String specialNumberToString(int type) {
        switch (type) {
            case NUMBER_TYPE_UNKNOWN:
                return mUnknownNumber;
            case NUMBER_TYPE_PRIVATE:
                return mPrivateNumber;
            case NUMBER_TYPE_PAYPHONE:
                return mPayphoneNumber;
            default:
                break;
        }
        return null;
    }

    private class ViewHolder {
        public QuickContactBadge quickContactBadge;
        public TextView name;
        public TextView labelAndNumber;
        public View callInfo;
        public ImageView callType;
        public TextView address;
        public TextView date;
        public TextView accountLabel;
    }

    /**
     * M: Fix ALPS01398152, Support RTL display for Arabic/Hebrew/Urdu
     * @param origin
     * @return
     */
    private String numberLeftToRight(String origin) {
        return TextUtils.isEmpty(origin) ? origin : '\u202D' + origin + '\u202C';
    }
    /// @}

    /**
     * M: [Suggested Account] get PhoneAccountHandle via position.
     */
    public PhoneAccountHandle getSuggestPhoneAccountHandle(int position) {
        final Cursor cursor = (Cursor)getItem(position);
        PhoneAccountHandle phoneAccountHandle = null;
        if (cursor != null) {
            phoneAccountHandle = PhoneAccountUtils.getAccount(
                    cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME_INDEX),
                    cursor.getString(PHONE_ACCOUNT_ID_INDEX));

            long id = cursor.getLong(DATA_ID_INDEX);

            /// M: Design change for data_id(DATA_ID_INDEX).
            /// For phone number stored as contact, its data_id will be bigger than 0;
            /// For phone number not stored as contact, its data_id will be always 0.
            /// We should filter out these whose phone number stored as contacts.
            if (id > 0) {
                phoneAccountHandle = null;
            }
            return phoneAccountHandle;
        } else {
            Log.w(TAG, "Cursor was null in getPhoneAccountHandle(), return null");
            return null;
        }
    }

    /**
     * M: use this method instead of CallUtil.isVideoEnable, give a chance to modify
     * the value. such as plug-in customization.
     * @return
     */
    boolean isVideoEnabled(String query) {
        return ExtensionManager.getInstance().getDialPadExtension().isVideoButtonEnabled(
                CallUtil.isVideoEnabled(getContext()), PhoneNumberUtils.normalizeNumber(query),
                null);
    }

    //*/ freeme.zhaozehong, 05/07/17. show freeme yellow page data
    private boolean isFreemeYellowPageTitle(int position) {
        Cursor cursor = (Cursor) getItem(position);
        if (cursor != null && !cursor.isNull(SEARCH_PHONE_LABEL_INDEX)) {
            return FreemeYellowPageUtils.FREEME_YELLOWPAGE_TITLE_LABEL.equals(
                    cursor.getString(SEARCH_PHONE_LABEL_INDEX));
        }
        return false;
    }

    private FreemeYellowPageUtils freemeYellowPageUtils = new FreemeYellowPageUtils();
    private void bindFreemeYellowView(View view, Context context, Cursor cursor) {
        int highLightColor = context.getResources().getColor(R.color.freeme_color_accent);
        String name = cursor.getString(NAME_INDEX);
        String number = cursor.getString(SEARCH_PHONE_NUMBER_INDEX);
        String fullPin = cursor.getString(DS_MATCHED_DATA_OFFSETS);
        String jianPin = cursor.getString(DS_MATCHED_NAME_OFFSETS);
        final ViewHolder viewHolder = (ViewHolder) view.getTag();
        viewHolder.accountLabel.setVisibility(View.GONE);
        viewHolder.labelAndNumber.setVisibility(View.VISIBLE);
        viewHolder.callInfo.setVisibility(View.GONE);
        viewHolder.name.setText(name);
        String query = getQueryString().replace(" ", "");
        SpannableString span = new SpannableString(number);
        if (number.startsWith(query)) {
            span.setSpan(new ForegroundColorSpan(highLightColor),
                    0, query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            viewHolder.labelAndNumber.setText(span);
        } else {
            viewHolder.labelAndNumber.setText(number);
        }
        int span_start = 0;
        int span_end = 0;
        if (fullPin.indexOf(query) >= 0) {
            String[] chineseHighLight = getFullPinQuery(query, name, fullPin);
            span_start = Integer.parseInt(chineseHighLight[0]);
            span_end = span_start + chineseHighLight[1].length();
        } else if (jianPin.indexOf(query) >= 0) {
            String[] chineseHighLight = getJianPinQuery(query, jianPin);
            span_start = Integer.parseInt(chineseHighLight[0]);
            span_end = span_start + chineseHighLight[1].length();
        }
        SpannableStringBuilder hightLightSpan = new SpannableStringBuilder(name);
        hightLightSpan.setSpan(new ForegroundColorSpan(highLightColor),
                span_start, span_end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.name.setText(hightLightSpan);

        freemeYellowPageUtils.showFreemeYellowPageNameAndLogo(context, number,
                viewHolder.quickContactBadge, null);

        viewHolder.quickContactBadge.assignContactUri(null);
    }

    public static String[] getFullPinQuery(String query, String chinese, String full_pin){
        int queryLength = query.length();
        int index = full_pin.indexOf(query);
        int currentCount = 0;
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        int start = 0;
        char[] nameChar = chinese.toCharArray();
        for (int i = 0; i < nameChar.length; i++) {
            if (nameChar[i] > 128) {
                try {
                    String s = PinyinHelper.toHanyuPinyinStringArray(nameChar[i], defaultFormat)[0];
                    currentCount += s.length();
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    e.printStackTrace();
                }
            }else{
                currentCount += 1;
            }
            if(currentCount > index){
                start = i;
                break;
            } else if(currentCount == index){
                start = i + 1;
                break;
            }
        }

        int currentCount1 = 0;
        String s1 = chinese.substring(start);
        int ll =0;
        char[] s1Char = s1.toCharArray();
        for(int j =0; j<s1Char.length;j++){
            if (s1Char[j] > 128) {
                try {
                    String s = PinyinHelper.toHanyuPinyinStringArray(s1Char[j], defaultFormat)[0];
                    currentCount1 += s.length();
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    e.printStackTrace();
                }
            }else{
                currentCount1 += 1;
            }

            if(currentCount1 >= queryLength){
                ll = j+1;
                break;
            }
        }

        return new String[]{String.valueOf(start), s1.substring(0, ll)};
    }

    public static String[] getJianPinQuery(String query, String jian_pin){
        int index = jian_pin.indexOf(query);
        return new String[]{String.valueOf(index), query};
    }
    //*/
}
