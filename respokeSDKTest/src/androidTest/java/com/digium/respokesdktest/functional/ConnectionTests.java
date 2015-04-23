/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdktest.functional;

import android.util.Log;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdk.RespokeGroup;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.Date;


public class ConnectionTests extends RespokeTestCase implements RespokeClient.Listener {

    private boolean rateLimitHit;
    RespokeEndpoint endpoint;


    public void testRateLimiting() {
        // Create a client to test with
        final String testEndpointID = generateTestEndpointID();
        final RespokeClient client = createTestClient(testEndpointID, this);

        endpoint = client.getEndpoint(testEndpointID, false);
        assertNotNull("Should create endpoint instance", endpoint);

        asyncTaskDone = false;

        // Launch 4 simultaneous cascades of presence registration calls to the server, in an effort to hit the rate limit
        sendLoop(1);
        sendLoop(1);
        sendLoop(1);
        sendLoop(1);

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Should eventually hit a rate limit error", rateLimitHit);
    }


    public void sendLoop(final Integer attempt) {
        // Generally hits the rate limit around 20 attempts
        if (!rateLimitHit && (attempt < 30)) {
            endpoint.registerPresence(new Respoke.TaskCompletionListener() {
                @Override
                public void onSuccess() {
                    assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                    sendLoop(attempt + 1);
                }

                @Override
                public void onError(String errorMessage) {
                    if (!rateLimitHit) {
                        assertTrue("Should indicate a rate limit error", errorMessage.equals("Too Many Requests"));
                        rateLimitHit = true;
                        asyncTaskDone = true;
                    }
                }
            });
        } else {
            asyncTaskDone = true;
        }
    }


    // RespokeClient.Listener methods


    public void onConnect(RespokeClient sender) {
        asyncTaskDone = true;
    }


    public void onDisconnect(RespokeClient sender, boolean reconnecting) {

    }


    public void onError(RespokeClient sender, String errorMessage) {
        assertTrue("Should not produce any client errors during endpoint testing", false);
        asyncTaskDone = true;
    }


    public void onCall(RespokeClient sender, RespokeCall call) {
        // Not under test
    }


    public void onIncomingDirectConnection(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }


    public void onMessage(String message, RespokeEndpoint sender, RespokeGroup group, Date timestamp) {
        // Not under test
    }


}
