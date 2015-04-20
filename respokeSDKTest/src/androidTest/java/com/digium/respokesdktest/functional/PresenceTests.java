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

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdk.RespokeGroup;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;


public class PresenceTests extends RespokeTestCase implements RespokeClient.Listener, RespokeClient.ResolvePresenceListener, RespokeEndpoint.Listener {

    private boolean callbackDidSucceed;
    private boolean remotePresenceReceived;
    private RespokeEndpoint firstEndpoint;
    private RespokeEndpoint secondEndpoint;
    private Object expectedRemotePresence;
    private Object customPresenceResolution;

    public void testCustomResolveMethod() {
        // Create a client to test with
        final String testEndpointID = generateTestEndpointID();
        final RespokeClient firstClient = createTestClient(testEndpointID, this);

        // Create a second client to test with
        final String secondTestEndpointID = generateTestEndpointID();
        final RespokeClient secondClient = createTestClient(secondTestEndpointID, this);

        // Build references to each of the endpoints
        firstEndpoint = secondClient.getEndpoint(testEndpointID, false);
        assertNotNull("Should create endpoint instance", firstEndpoint);
        firstEndpoint.setListener(this);

        secondEndpoint = firstClient.getEndpoint(secondTestEndpointID, false);
        assertNotNull("Should create endpoint instance", secondEndpoint);
        secondEndpoint.setListener(this);

        // The custom resolve function will always return this random
        customPresenceResolution = Respoke.makeGUID();

        secondClient.setResolvePresenceListener(this);

        asyncTaskDone = false;
        remotePresenceReceived = false;
        callbackDidSucceed = false;
        firstEndpoint.registerPresence(new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                callbackDidSucceed = true;
                asyncTaskDone = remotePresenceReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully register to receive presence updates. Error: " + errorMessage, false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));

        asyncTaskDone = false;
        remotePresenceReceived = false;
        callbackDidSucceed = false;
        expectedRemotePresence = new HashMap<String, String>();
        ((HashMap<String, String>)expectedRemotePresence).put("presence", "nacho presence2");
        firstClient.setPresence(expectedRemotePresence, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                callbackDidSucceed = true;
                asyncTaskDone = remotePresenceReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully register to receive presence updates. Error: " + errorMessage, false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));

        assertTrue("Resolved presence should be a string", firstEndpoint.presence instanceof String);
        assertTrue("Resolved presence should be correct", customPresenceResolution.equals(firstEndpoint.presence));
    }

    public void testOfflineEndpointPresence() {
        // Create a client to test with
        final String testEndpointID = generateTestEndpointID();
        final RespokeClient client = createTestClient(testEndpointID, this);

        // Create a second random endpoint id to test with
        final String secondTestEndpointID = generateTestEndpointID();

        // Get an endpoint object to represent the second endpoint which is not online
        RespokeEndpoint endpoint = client.getEndpoint(secondTestEndpointID, false);

        assertNull("Presence should be null if the client has not registered for presence updates yet", endpoint.presence);

        asyncTaskDone = false;
        callbackDidSucceed = false;
        endpoint.registerPresence(new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                callbackDidSucceed = true;
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully register to receive presence updates. Error: " + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Callback should succeed", callbackDidSucceed);

        assertTrue("Presence should be unavailable", endpoint.presence.equals("unavailable"));
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


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint sender) {
        // Not under test
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertNotNull("Remote presence should not be null", presence);
        assertTrue("Remote presence should be a string", presence instanceof String);
        assertTrue("Resolved presence should be correct", customPresenceResolution.equals((String)presence));
        remotePresenceReceived = true;
        asyncTaskDone = callbackDidSucceed;
    }


    // RespokeClient.ResolvePresenceListener methods


    public Object resolvePresence(ArrayList<Object> presenceArray) {
        assertTrue("presence array should contain the correct number of values", 1 == presenceArray.size());
        return customPresenceResolution;
    }


}
