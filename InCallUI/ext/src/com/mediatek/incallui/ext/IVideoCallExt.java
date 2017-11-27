package com.mediatek.incallui.ext;

/**
 * Plugin APIs for video call.
 */
public interface IVideoCallExt {

    /**
     * called to show toast when call downgraded.
     * @param call  call object
     * @param event to notify call downgrade
     */
    void onCallSessionEvent(Object call, int event);

    /**
     * called to change video call decline timer.
     * @return duration
     */
    int getDeclineTimer();
}
