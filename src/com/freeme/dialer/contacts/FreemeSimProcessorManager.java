package com.freeme.dialer.contacts;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import com.android.contacts.common.vcard.ProcessorBase;
import com.mediatek.contacts.simservice.SimServiceUtils;
import com.mediatek.contacts.util.Log;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by zhaozehong on 13/07/17.
 */

public class FreemeSimProcessorManager {
    private final String TAG = this.getClass().getSimpleName();

    public interface ProcessorManagerListener {
        public void addProcessor(long scheduleTime, ProcessorBase processor);

        public void onAllProcessorsFinished();
    }

    public interface ProcessorCompleteListener {
        public void onProcessorCompleted(Intent intent);
    }

    private ProcessorManagerListener mListener;
    private Handler mHandler;
    private ConcurrentHashMap<Integer, FreemeSimProcessorBase> mEditDeleteProcessors;

    private static final int MSG_SEND_STOP_SERVICE = 1;

    // Out of 200ms hasn't new tasks and all tasks have completed, will stop service.
    // The Backgroud broast will delayed by backgroudService,so if nothing to do,
    // should stop service soon
    private static final int DELAY_MILLIS_STOP_SEVICE = 200;

    public FreemeSimProcessorManager(Context context, ProcessorManagerListener listener) {
        Log.i(TAG, "[FreemeSimProcessorManager] new...");
        mListener = listener;
        mEditDeleteProcessors = new ConcurrentHashMap<>();
        mHandler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SEND_STOP_SERVICE:
                        callStopService();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    public void handleProcessor(Context context, int subId, Intent intent) {
        Log.i(TAG, "[handleProcessor] subId=" + subId + ",time=" + System.currentTimeMillis());
        FreemeSimProcessorBase processor = createProcessor(context, subId, intent);
        if (processor != null && mListener != null) {
            Log.d(TAG, "[handleProcessor]Add processor [subId=" + subId + "] to threadPool.");
            mListener.addProcessor(/* 1000 + slotId * 300 */0, processor);
        }
    }

    private FreemeSimProcessorBase createProcessor(Context context, int subId, Intent intent) {
        Log.d(TAG, "[createProcessor]subId = " + subId);
        synchronized (mProcessorRemoveLock) {
            FreemeSimProcessorBase processor = new FreemeSimDeleteProcessor(
                    context, subId, intent, mProcessoListener);
            mEditDeleteProcessors.put(subId, processor);
            return processor;
        }
    }

    private ProcessorCompleteListener mProcessoListener = new ProcessorCompleteListener() {

        @Override
        public void onProcessorCompleted(Intent intent) {
            if (intent != null) {
                int subId = intent.getIntExtra(SimServiceUtils.SERVICE_SUBSCRIPTION_KEY, 0);
                int workType = intent.getIntExtra(SimServiceUtils.SERVICE_WORK_TYPE, -1);
                Log.d(TAG, "[onProcessorCompleted] subId = " + subId + ",time="
                        + System.currentTimeMillis() + ", workType = " + workType);

                synchronized (mProcessorRemoveLock) {
                    if (mEditDeleteProcessors.containsKey(subId)) {
                        Log.d(TAG, "[onProcessorCompleted] remove other processor subId=" + subId);
                        /**
                         * when we're going to remove the
                         * processor, in seldom condition, it might have already
                         * removed and replaced with another processor. in this
                         * case, we should not remove it any more.
                         */
                        if (mEditDeleteProcessors.get(subId).identifyIntent(intent)) {
                            mEditDeleteProcessors.remove(subId);
                            checkStopService();
                        } else {
                            Log.w(TAG,
                                    "[onProcessorCompleted] race condition2");
                        }
                    } else {
                        Log.w(TAG, "[onProcessorCompleted] slotId processor not found");
                    }
                }
            }
        }
    };

    private void checkStopService() {
        Log.v(TAG, "[checkStopService]...");
        if (mEditDeleteProcessors.size() == 0) {
            if (mHandler != null) {
                Log.v(TAG, "[checkStopService] send stop service message.");
                mHandler.removeMessages(MSG_SEND_STOP_SERVICE);
                mHandler.sendEmptyMessageDelayed(MSG_SEND_STOP_SERVICE, DELAY_MILLIS_STOP_SEVICE);
            }
        }
    }

    private void callStopService() {
        Log.d(TAG, "[callStopService]...");
        if (mListener != null && mEditDeleteProcessors.size() == 0) {
            mListener.onAllProcessorsFinished();
        }
    }

    /**
     * the lock for synchronized
     */
    private final Object mProcessorRemoveLock = new Object();

}
