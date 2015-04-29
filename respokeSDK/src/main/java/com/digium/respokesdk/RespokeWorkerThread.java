/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdk;

import android.os.Handler;
import android.os.HandlerThread;

/**
 * Implements a worker thread for queueing and processing socket transactions with the Respoke service
 */
public class RespokeWorkerThread extends HandlerThread {

    private Handler workerHandler;


    public RespokeWorkerThread(String name) {
        super(name);
    }


    public void postTask(Runnable task){
        workerHandler.post(task);
    }


    public void postTaskDelayed(Runnable task, long delayMillis){
        workerHandler.postDelayed(task, delayMillis);
    }


    public void prepareHandler(){
        workerHandler = new Handler(getLooper());
    }


    public void cancelAllTasks() {
        // Cancel all pending tasks and callbacks, but leave the thread ready to run new tasks
        workerHandler.removeCallbacksAndMessages(null);
    }
}
