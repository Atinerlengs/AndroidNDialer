package com.mediatek.incallui.ext;

/**
 * Default implementation for IVideoCallExt.
 */
public class DefaultVideoCallExt implements IVideoCallExt {

    private static final int DEFAULT_COUNT_DOWN_SECONDS = 20;

    @Override
    public void onCallSessionEvent(Object call, int event) {
        // do nothing.
    }

    @Override
    public int getDeclineTimer() {
        return DEFAULT_COUNT_DOWN_SECONDS;
    }
}
