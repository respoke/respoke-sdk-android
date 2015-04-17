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
import android.test.ActivityInstrumentationTestCase2;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public abstract class RespokeActivityTestCase<T extends android.app.Activity> extends ActivityInstrumentationTestCase2<T> {

    public CountDownLatch asyncTaskSignal = new CountDownLatch(1);


    public RespokeActivityTestCase(Class activityClass) {
        super(activityClass);
    }


    public RespokeClient createTestClient(final String endpointID, RespokeClient.Listener listener, final Context context) {
        final RespokeClient client = Respoke.sharedInstance().createClient(context);
        assertNotNull("Should create test client", client);
        client.baseURL = RespokeTestCase.TEST_RESPOKE_BASE_URL;

        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        client.setListener(listener);
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    client.connect(endpointID, RespokeTestCase.testAppID, true, null, context, new RespokeClient.ConnectCompletionListener() {
                        @Override
                        public void onError(String errorMessage) {
                            assertTrue("Should successfully connect. error: " + errorMessage, false);
                            asyncTaskSignal.countDown();
                        }
                    });
                }
            });

            assertTrue("Client connect timed out", asyncTaskSignal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
            assertTrue("Test client should connect", client.isConnected());

        } catch (Throwable throwable) {
            assertTrue("Exception encountered while creating test client", false);
        }

        return client;
    }


}
