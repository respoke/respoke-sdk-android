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
    private boolean endpointMessageDelivered;
    private boolean endpointMessageCopied;
    private boolean doCopySelf;
    private boolean clientMessageDelivered;
    private boolean clientMessageCopied;
    private RespokeEndpoint recipientEndpoint;
    private RespokeEndpoint senderEndpoint;


    public void testEndpointMessaging() {
        doCopySelf = false;
        runMessagingTest();
    }

    public void testEndpointMessagingCCSelf() {
        doCopySelf = true;
        runMessagingTest();
    }

    /**
     *  This test will create two client instances with unique endpoint IDs. It will then send messages between the two to test functionality.
     */

    public void runMessagingTest() {
        RespokeClient senderCCClient = null;
        RespokeEndpoint recipientCCEndpoint = null;

        // Create a client to test with
        final String recipientEndpointID = generateTestEndpointID();
        final RespokeClient recipientClient = createTestClient(recipientEndpointID, this);

        // Create a second client to test with
        final String senderEndpointID = generateTestEndpointID();
        final RespokeClient senderClient = createTestClient(senderEndpointID, this);

        // Build references to each of the endpoints
        recipientEndpoint = senderClient.getEndpoint(recipientEndpointID, false);
        assertNotNull("Should create endpoint instance", recipientEndpoint);
        recipientEndpoint.setListener(this);

        senderEndpoint = recipientClient.getEndpoint(senderEndpointID, false);
        assertNotNull("Should create endpoint instance", senderEndpoint);
        senderEndpoint.setListener(this);

        // Create new client and endpoint for copying self
        if (doCopySelf)
        {
            senderCCClient = createTestClient(senderEndpointID, this);
            assertNotNull("Should create sender CC client", senderCCClient);

            recipientCCEndpoint = senderCCClient.getEndpoint(recipientEndpointID, false);
            assertNotNull("Should create endpoint instance", recipientCCEndpoint);
            recipientCCEndpoint.setListener(this);
        }

        endpointMessageCopied = false;
        endpointMessageDelivered = false;
        clientMessageCopied = false;
        clientMessageDelivered = false;
        asyncTaskDone = false;
        callbackDidSucceed = false;
        recipientEndpoint.sendMessage(TEST_MESSAGE, false, doCopySelf, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                callbackDidSucceed = true;
                tryCompleteTask();
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully send a message", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("sendMessage should call successHandler", callbackDidSucceed);
        assertTrue("Should call RespokeEndpoint.onMessage listener when a message is received", endpointMessageDelivered);
        assertTrue("Should call RespokeClient.onMessage listener when a message is received", clientMessageDelivered);

        if (doCopySelf) {
            assertTrue("Should call RespokeEndpoint.onMessage listener with didSend=false when a message is copied", endpointMessageCopied);
            assertTrue("Should call RespokeClient.onMessage listener with didSend=false when a message is copied", clientMessageCopied);
        }
    }

    public void tryCompleteTask() {
        asyncTaskDone = callbackDidSucceed && endpointMessageDelivered && clientMessageDelivered;
        if (doCopySelf) {
            asyncTaskDone = asyncTaskDone && endpointMessageCopied && clientMessageCopied;
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


    public void onMessage(String message, RespokeEndpoint endpoint, RespokeGroup group, Date timestamp, Boolean didSend) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Message sent should be the message received", message.equals(TEST_MESSAGE));
        assertNotNull("Should include a timestamp", timestamp);

        if (!doCopySelf) {
            assertTrue("Endpoint should always be the sender", didSend);
        }

        assertNotNull("Endpoint passed to RespokeClient.onMessage listener should not be null", endpoint);
        final String onMessageEndpointID = endpoint.getEndpointID();

        if (didSend) {
            assertTrue("Should indicate correct sender endpointID", onMessageEndpointID.equals(senderEndpoint.getEndpointID()));
            clientMessageDelivered = true;
        } else {
            assertTrue("Should indicate correct sender endpointID", onMessageEndpointID.equals(recipientEndpoint.getEndpointID()));
            clientMessageCopied = true;
        }

        tryCompleteTask();
    }


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint endpoint, boolean didSend) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Message sent should be the message received", message.equals(TEST_MESSAGE));
        assertNotNull("Should include a timestamp", timestamp);

        if (!doCopySelf) {
            assertTrue("Endpoint should always be the sender", didSend);
        }

        assertNotNull("Endpoint passed to RespokeEndpoint.onMessage listener should not be null", endpoint);
        final String onMessageEndpointID = endpoint.getEndpointID();

        if (didSend) {
            assertTrue("Should indicate correct sender endpointID", onMessageEndpointID.equals(senderEndpoint.getEndpointID()));
            endpointMessageDelivered = true;
        } else {
            assertTrue("Should indicate correct sender endpointID", onMessageEndpointID.equals(recipientEndpoint.getEndpointID()));
            endpointMessageCopied = true;
        }

        tryCompleteTask();
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        // Not under test
    }


}
