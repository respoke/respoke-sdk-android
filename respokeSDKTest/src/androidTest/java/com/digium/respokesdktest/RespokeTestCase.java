/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdktest;

import android.content.Context;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.util.Log;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeClient;

/**
 * A test case base class to provide commonly used methods
 */
public abstract class RespokeTestCase extends AndroidTestCase {

    public final static String TEST_RESPOKE_BASE_URL = "https://api.respoke.io";
    public final static String TEST_APP_ID = "REPLACE_ME_WITH_YOUR_APP_ID";
    public static final String TEST_MESSAGE = "This is a test message!";
    public static int TEST_TIMEOUT = 60;  // Timeout in seconds
    public static int CALL_TEST_TIMEOUT = 60; // Timeout in seconds for calling tests (which take longer to setup)
    public boolean asyncTaskDone;

    private static final String TAG = "RespokeTestCase";


    public static String getTestBotEndpointId(Context context) {
        return "testbot-" + context.getResources().getText(R.string.TEST_BOT_SUFFIX);
    }


    public static String generateTestEndpointID() {
        String uuid = "";
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int rnd = 0;
        int r;

        for (int i = 0; i < 6; i += 1) {
            if (rnd <= 0x02) {
                rnd = (int) (0x2000000 + Math.round(java.lang.Math.random() * 0x1000000));
            }
            r = rnd & 0xf;
            rnd = rnd >> 4;
            uuid = uuid + chars.charAt(r);
        }
        return "test_user_" + uuid;
    }


    public boolean waitForCompletion(long timeoutSecs) {
        long timeoutDate = System.currentTimeMillis() + (timeoutSecs * 1000);

        try {
            do {
                if (System.currentTimeMillis() > timeoutDate) {
                    Log.e(TAG, "network timeout");
                    Log.e(TAG, "inside TapInspectTestCase : class = " + this.getClass().getSimpleName());
                    break;
                }
                synchronized (this) {
                    this.wait(100);
                }
            } while (!asyncTaskDone);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        return asyncTaskDone;
    }


    public RespokeClient createTestClient(String endpointID, RespokeClient.Listener listener) {
        final RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", client);
        client.baseURL = TEST_RESPOKE_BASE_URL;

        asyncTaskDone = false;
        client.setListener(listener);
        client.connect(endpointID, RespokeTestCase.TEST_APP_ID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect", false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Client connect timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Test client should connect", client.isConnected());

        return client;
    }


    public static boolean currentlyOnUIThread() {
        // Returns true if the current thread is the UI thread
        return Looper.myLooper() == Looper.getMainLooper();
    }

    
}
