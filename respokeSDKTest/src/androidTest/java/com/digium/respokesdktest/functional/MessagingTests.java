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

import java.util.Date;


public class MessagingTests extends RespokeTestCase implements RespokeClient.Listener, RespokeEndpoint.Listener {

    private boolean callbackDidSucceed;
    private boolean messageReceived;
    private boolean clientMessageReceived;
    private RespokeEndpoint firstEndpoint;
    private RespokeEndpoint secondEndpoint;


    /**
     *  This test will create two client instances with unique endpoint IDs. It will then send messages between the two to test functionality.
     */
    public void testEndpointMessaging() {
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

        asyncTaskDone = false;
        messageReceived = false;
        clientMessageReceived = false;
        callbackDidSucceed = false;
        firstEndpoint.sendMessage(TEST_MESSAGE, false, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                callbackDidSucceed = true;
                asyncTaskDone = messageReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully send a message", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("sendMessage should call successHandler", callbackDidSucceed);
        assertTrue("Should call RespokeEndpoint.onMessage listener when a message is received", messageReceived);
        assertTrue("Should call RespokeClient.onMessage listener when a message is received", clientMessageReceived);
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
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Message sent should be the message received", message.equals(TEST_MESSAGE));
        assertTrue("Should indicate correct sender endpoint ID", sender.getEndpointID().equals(secondEndpoint.getEndpointID()));
        assertNotNull("Should include a timestamp", timestamp);
        clientMessageReceived = true;
        asyncTaskDone = callbackDidSucceed && messageReceived;
    }


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Message sent should be the message received", message.equals(TEST_MESSAGE));
        assertTrue("Should indicate correct sender endpoint ID", sender.getEndpointID().equals(secondEndpoint.getEndpointID()));
        assertNotNull("Should include a timestamp", timestamp);
        messageReceived = true;
        asyncTaskDone = callbackDidSucceed && clientMessageReceived;
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        // Not under test
    }


}
