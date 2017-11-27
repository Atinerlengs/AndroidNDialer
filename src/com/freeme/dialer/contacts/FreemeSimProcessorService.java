package com.freeme.dialer.contacts;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.android.contacts.common.vcard.ProcessorBase;
import com.mediatek.contacts.simservice.SimServiceUtils;
import com.mediatek.contacts.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by zhaozehong on 13/07/17.
 */

public class FreemeSimProcessorService extends Service {

    private final String TAG = this.getClass().getSimpleName();

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 10;
    private static final int KEEP_ALIVE_TIME = 10; // 10 seconds

    private FreemeSimProcessorManager mProcessorManager;
    private AtomicInteger mNumber = new AtomicInteger();
    private final ExecutorService mExecutorService = createThreadPool(CORE_POOL_SIZE);

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "[onCreate]...");
        mProcessorManager = new FreemeSimProcessorManager(this, mListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        super.onStartCommand(intent, flags, id);
        processIntent(intent);
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "[onDestroy]...");
    }

    private void processIntent(Intent intent) {
        if (intent == null) {
            Log.w(TAG, "[processIntent] intent is null.");
            return;
        }
        int subId = intent.getIntExtra(SimServiceUtils.SERVICE_SUBSCRIPTION_KEY, 0);
        mProcessorManager.handleProcessor(getApplicationContext(), subId, intent);
    }

    private FreemeSimProcessorManager.ProcessorManagerListener mListener =
            new FreemeSimProcessorManager.ProcessorManagerListener() {
                @Override
                public void addProcessor(long scheduleTime, ProcessorBase processor) {
                    if (processor != null) {
                        try {
                            mExecutorService.execute(processor);
                        } catch (RejectedExecutionException e) {
                            Log.e(TAG, "[addProcessor] RejectedExecutionException", e);
                        }
                    }
                }

                @Override
                public void onAllProcessorsFinished() {
                    Log.d(TAG, "[onAllProcessorsFinished]...");
                    stopSelf();
                    mExecutorService.shutdown();
                }
            };

    private ExecutorService createThreadPool(int initPoolSize) {
        return new ThreadPoolExecutor(initPoolSize, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                String threadName = "SIM Service - " + mNumber.getAndIncrement();
                Log.d(TAG, "[createThreadPool]thread name:" + threadName);
                return new Thread(r, threadName);
            }
        });
    }
}
