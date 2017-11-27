package com.freeme.incallui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.incallui.AnswerFragment;
import com.android.incallui.ContactInfoCache;
import com.android.incallui.GlowPadWrapper;
import com.android.incallui.InCallPresenter;
import com.android.incallui.Log;
import com.freeme.contacts.common.utils.FreemeBitmapUtils;
import com.freeme.contacts.common.utils.FreemeNavigationBarUtils;
import com.mediatek.incallui.ext.ExtensionManager;

/**
 * Created by zhaozehong on 19/07/17.
 */

public class FreemeGlowPadAnswerFragment extends AnswerFragment implements View.OnClickListener {

    private GlowPadWrapper mGlowpad;
    private View mAvatarContainer;
    private ImageView mAvatarImg;
    private TextView mMuteBtn;
    private TextView mDeclineText;
    private ImageView mAnswerVideo;
    private ImageView mAnswerAudio;
    private boolean isUpgradeToVideo;
    private View mBottomLayout;

    public FreemeGlowPadAnswerFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.freeme_answer_fragment, container, false);
        mGlowpad = (GlowPadWrapper) view.findViewById(R.id.glow_pad_view);
        mGlowpad.setAnswerFragment(this);

        ExtensionManager.getVilteAutoTestHelperExt().registerReceiverForAcceptAndRejectUpgrade(
                getActivity(), InCallPresenter.getInstance().getAnswerPresenterByFreeme());

        mAvatarContainer = view.findViewById(R.id.contact_photo_container);
        mAvatarImg = (ImageView) view.findViewById(R.id.contact_photo_img);
        mMuteBtn = (TextView) view.findViewById(R.id.mute_btn);
        mMuteBtn.setOnClickListener(this);
        mDeclineText = (TextView) view.findViewById(R.id.decline_via_text_btn);
        mDeclineText.setOnClickListener(this);

        view.findViewById(R.id.incoming_call_decline).setOnClickListener(this);
        mAnswerVideo = (ImageView) view.findViewById(R.id.incoming_call_answer_video);
        mAnswerVideo.setOnClickListener(this);
        mAnswerAudio = (ImageView) view.findViewById(R.id.incoming_call_answer_audio);
        mAnswerAudio.setOnClickListener(this);

        mBottomLayout = view.findViewById(R.id.bottom_layout);

        initNavBarUtils();

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.mute_btn:
                TelecomManager tm = (TelecomManager) getActivity().getSystemService(Context.TELECOM_SERVICE);
                tm.silenceRinger();
                mMuteBtn.setSelected(true);
                break;
            case R.id.decline_via_text_btn:
                onText();
                break;
            case R.id.incoming_call_decline:
                if (isUpgradeToVideo) {
                    onDeclineUpgradeRequest(getContext());
                } else {
                    onDecline(getContext());
                }
                break;
            case R.id.incoming_call_answer_video:
                onAnswer(mGlowpad.getVideoState(), getContext());
                break;
            case R.id.incoming_call_answer_audio:
                if (isUpgradeToVideo) {
                    onAnswer(mGlowpad.getVideoState(), getContext());
                } else {
                    onAnswer(VideoProfile.STATE_AUDIO_ONLY, getContext());
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mGlowpad.requestFocus();
        Drawable drawable = ContactInfoCache.getInstance(getActivity()).getContactPhotoDrawable();
        if (drawable != null) {
            showContactPhoto(drawable);
        }
    }

    @Override
    public void onDestroyView() {
        Log.d(this, "onDestroyView");
        if (mGlowpad != null) {
            mGlowpad.stopPing();
            mGlowpad = null;
        }
        ExtensionManager.getVilteAutoTestHelperExt().unregisterReceiverForAcceptAndRejectUpgrade();
        mNavBarUtils.onDestroy(getActivity());
        super.onDestroyView();
    }

    @Override
    public void onShowAnswerUi(boolean shown) {
        if (mListener != null) {
            mListener.onCallButtonShow(!shown);
            mListener.onCallCardShowPhoto(shown);
        }
        Log.d(this, "Show answer UI: " + shown);
        int visibility = shown ? View.VISIBLE : View.GONE;
        mAvatarContainer.setVisibility(visibility);
        if (shown) {
            mGlowpad.startPing();
        } else {
            mGlowpad.stopPing();
        }
    }

    /**
     * Sets targets on the glowpad according to target set identified by the parameter.
     *
     * @param targetSet Integer identifying the set of targets to use.
     */
    public void showTargets(int targetSet) {
        showTargets(targetSet, VideoProfile.STATE_BIDIRECTIONAL);
    }

    /**
     * Sets targets on the glowpad according to target set identified by the parameter.
     *
     * @param targetSet Integer identifying the set of targets to use.
     */
    @Override
    public void showTargets(int targetSet, int videoState) {
        final int targetResourceId;
        final int targetDescriptionsResourceId;
        final int directionDescriptionsResourceId;
        mGlowpad.setVideoState(videoState);

        switch (targetSet) {
            case TARGET_SET_FOR_AUDIO_WITH_SMS:
                targetResourceId =
                        R.array.incoming_call_widget_audio_with_sms_targets;
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_audio_with_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_audio_with_sms_direction_descriptions;
                mAnswerVideo.setVisibility(View.GONE);
                break;

            case TARGET_SET_FOR_VIDEO_WITHOUT_SMS:
                targetResourceId =
                        R.array.incoming_call_widget_video_without_sms_targets;
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_video_without_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_video_without_sms_direction_descriptions;
                mAnswerVideo.setVisibility(View.VISIBLE);
                break;

            case TARGET_SET_FOR_VIDEO_WITH_SMS:
                targetResourceId =
                        R.array.incoming_call_widget_video_with_sms_targets;
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_video_with_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_video_with_sms_direction_descriptions;
                mAnswerVideo.setVisibility(View.VISIBLE);
                break;

            case TARGET_SET_FOR_VIDEO_ACCEPT_REJECT_REQUEST:
                targetResourceId =
                        R.array.incoming_call_widget_video_request_targets;
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_video_request_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_video_request_target_direction_descriptions;
                isUpgradeToVideo = true;
                mAnswerVideo.setVisibility(View.GONE);
                mAnswerAudio.setImageResource(R.drawable.freeme_incoming_call_answer_video_selector);
                mDeclineText.setVisibility(View.INVISIBLE);
                mMuteBtn.setVisibility(View.INVISIBLE);
                break;

            // [video call]3G Video call doesn't support answer as audio, and reject via SMS
            case TARGET_SET_FOR_VIDEO_WITHOUT_SMS_AUDIO:
                targetResourceId =
                        R.array.mtk_incoming_call_widget_video_without_sms_audio_targets;
                targetDescriptionsResourceId =
                        R.array.mtk_incoming_call_widget_video_without_sms_audio_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.mtk_incoming_call_widget_video_without_sms_audio_direction_descriptions;
                mAnswerVideo.setVisibility(View.VISIBLE);
                break;

            case TARGET_SET_FOR_AUDIO_WITHOUT_SMS:
            default:
                targetResourceId =
                        R.array.incoming_call_widget_audio_without_sms_targets;
                targetDescriptionsResourceId =
                        R.array.incoming_call_widget_audio_without_sms_target_descriptions;
                directionDescriptionsResourceId =
                        R.array.incoming_call_widget_audio_without_sms_direction_descriptions;
                mAnswerVideo.setVisibility(View.GONE);
                break;
        }

        if (targetResourceId != mGlowpad.getTargetResourceId()) {
            mGlowpad.setTargetResources(targetResourceId);
            mGlowpad.setTargetDescriptionsResourceId(targetDescriptionsResourceId);
            mGlowpad.setDirectionDescriptionsResourceId(directionDescriptionsResourceId);
            mGlowpad.reset(false);
            mGlowpad.requestLayout();
        }
    }

    @Override
    protected void onMessageDialogCancel() {
        if (mGlowpad != null) {
            mGlowpad.startPing();
        }
    }

    public void showContactPhoto(Drawable photo) {
        if (mAvatarImg != null) {
            if (photo != null
                    && photo != ContactInfoCache.getInstance(getActivity())
                    .getDefaultContactPhotoDrawable()) {
                Bitmap avatar = ((BitmapDrawable) photo).getBitmap();
                avatar = FreemeBitmapUtils.getRoundedCornerBitmap(avatar, 0, 0,
                        avatar.getWidth(), avatar.getHeight(),
                        avatar.getWidth() / 2, avatar.getHeight() / 2);
                mAvatarImg.setImageBitmap(avatar);
            } else {
                mAvatarImg.setImageBitmap(null);
            }
        }
    }

    private InComingUIShowListener mListener;

    public interface InComingUIShowListener {
        void onCallButtonShow(boolean show);

        void onCallCardShowPhoto(boolean show);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof InComingUIShowListener) {
            mListener = (InComingUIShowListener) context;
        }
    }

    @Override
    public void onVisibilityChanged(boolean isShow) {
        super.onVisibilityChanged(isShow);
        if (isShow) {
            initSmartController(getActivity());
        } else {
            destroySmartController();
        }
    }

    private FreemeNavigationBarUtils mNavBarUtils;

    private void initNavBarUtils() {
        mNavBarUtils = new FreemeNavigationBarUtils(getActivity(),
                new FreemeNavigationBarUtils.INavigationBarShowOrHide() {
                    @Override
                    public void onNavShow(boolean isShow, int navHeight) {
                        updateNavgationBar(isShow, navHeight);
                    }
                });
    }

    private void updateNavgationBar(boolean isShow, int navHeight) {
        if (mBottomLayout != null) {
            mBottomLayout.setPadding(
                    mBottomLayout.getPaddingLeft(),
                    mBottomLayout.getPaddingTop(),
                    mBottomLayout.getPaddingRight(),
                    isShow ? navHeight : 0);
        }
    }
}
