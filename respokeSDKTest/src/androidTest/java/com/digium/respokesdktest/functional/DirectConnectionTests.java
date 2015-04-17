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


public class DirectConnectionTests extends RespokeTestCase implements RespokeClient.Listener, RespokeEndpoint.Listener, RespokeCall.Listener, RespokeDirectConnection.Listener {

    private boolean callbackDidSucceed;
    private boolean messageReceived;
    private boolean didConnect;
    private boolean didHangup;
    private boolean didGetIncomingDirectConnection;
    private boolean didGetCallerOnOpen;
    private boolean didGetCalleeOnOpen;
    private boolean didGetCallerOnClose;
    private boolean didGetCalleeOnClose;
    private RespokeEndpoint firstEndpoint;
    private RespokeEndpoint secondEndpoint;
    private RespokeDirectConnection callerDirectConnection;
    private RespokeDirectConnection calleeDirectConnection;
    private Object receivedMessageObject;


    public void testDirectConnection() {
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

        // Start a direct connection between the two endpoints, calling from firstEndpoint
        asyncTaskDone = false;
        didGetIncomingDirectConnection = false;
        didGetCallerOnOpen = false;
        didGetCalleeOnOpen = false;
        callerDirectConnection = secondEndpoint.startDirectConnection();
        callerDirectConnection.setListener(this);

        RespokeCall call = callerDirectConnection.getCall();
        call.setListener(this);

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.CALL_TEST_TIMEOUT));
        assertTrue("Call should be established", didConnect);
        assertTrue("Callee client should have received an incoming direct connection notification", didGetIncomingDirectConnection);
        assertTrue("Caller should have received an onOpen notification", didGetCallerOnOpen);
        assertTrue("Callee should have received an onOpen notification", didGetCalleeOnOpen);
        assertTrue("Call should indicate that it is the caller", call.isCaller());
        assertTrue("Should indicate call is with the endpoint that the call was started from", secondEndpoint == call.endpoint);
        assertNotNull("Callee should have been notified about the incoming direct connection", calleeDirectConnection);

        // Test sending a text message over the direct connection

        asyncTaskDone = false;
        callbackDidSucceed = false;
        messageReceived = false;
        callerDirectConnection.sendMessage(TEST_MESSAGE, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                callbackDidSucceed = true;
                asyncTaskDone = messageReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should not encounter an error when sending a message over a direct connection. Error: %@" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("sendMessage should have called the successHandler", callbackDidSucceed);
        assertTrue("Received message should be a string", receivedMessageObject instanceof String);
        assertTrue("Should have received correct message", receivedMessageObject.equals(TEST_MESSAGE));

        asyncTaskDone = false;
        didGetCallerOnClose = false;
        didGetCalleeOnClose = false;
        didHangup = false;
        call.hangup(true);

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Callee should have received onClose notification", didGetCalleeOnClose);
        assertTrue("Caller should have received onClose notification", didGetCallerOnClose);
    }


    // RespokeClient.Listener methods


    public void onConnect(RespokeClient sender) {
        asyncTaskDone = true;
    }


    public void onDisconnect(RespokeClient sender, boolean reconnecting) {
        // Not under test
    }


    public void onError(RespokeClient sender, String errorMessage) {
        assertTrue("Should not produce any client errors during testing", false);
        asyncTaskDone = true;
    }


    public void onCall(RespokeClient sender, RespokeCall call) {
        // Not under test
    }


    public void onIncomingDirectConnection(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Should originate from the first Endpoint", endpoint == firstEndpoint);
        assertNotNull("DirectConnection object should not be nil", directConnection);
        calleeDirectConnection = directConnection;
        calleeDirectConnection.setListener(this);
        didGetIncomingDirectConnection = true;

        // Accept the call to continue the connection process
        directConnection.accept();

        asyncTaskDone = didConnect && didGetCallerOnOpen && didGetCalleeOnOpen;
    }


    public void onMessage(String message, RespokeEndpoint sender, RespokeGroup group, Date timestamp) {
        // Not under test
    }


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint sender) {
        assertTrue("No messages should have been received through the Respoke service", false);
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        // Not under test
    }


    // RespokeCall.Listener methods


    public void onError(String errorMessage, RespokeCall sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Should perform a call without any errors. Error: " + errorMessage, false);
        asyncTaskDone = true;
    }


    public void onHangup(RespokeCall sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        didHangup = true;
        asyncTaskDone = didGetCallerOnClose && didGetCalleeOnClose;
    }


    public void onConnected(RespokeCall sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        didConnect = true;
        asyncTaskDone = didGetIncomingDirectConnection && didGetCallerOnOpen && didGetCalleeOnOpen;
    }


    public void directConnectionAvailable(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Should reference correct direct connection object", directConnection == callerDirectConnection);
        assertTrue("Should reference correct remote endpoint", endpoint == secondEndpoint);
    }


    // RespokeDirectConnection.Listener methods


    public void onStart(RespokeDirectConnection sender) {
        // This callback will not be called in this test. It is only triggered when adding a directConnection to an existing call, which is currently not supported.
    }


    public void onOpen(RespokeDirectConnection sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        if (sender == callerDirectConnection) {
            didGetCallerOnOpen = true;
        } else if (sender == calleeDirectConnection) {
            didGetCalleeOnOpen = true;
        } else {
            assertTrue("Should reference the correct direct connection object", false);
        }

        asyncTaskDone = didGetIncomingDirectConnection && didConnect && didGetCallerOnOpen && didGetCalleeOnOpen;
    }


    public void onClose(RespokeDirectConnection sender){
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        if (sender == callerDirectConnection) {
            didGetCallerOnClose = true;
        } else if (sender == calleeDirectConnection) {
            didGetCalleeOnClose = true;
        } else {
            assertTrue("Should reference the correct direct connection object", false);
        }

        asyncTaskDone = didHangup && didGetCallerOnClose && didGetCalleeOnClose;
    }


    public void onMessage(String message, RespokeDirectConnection sender)
    {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Should reference the correct direct connection object", sender == calleeDirectConnection);
        assertNotNull("message should not be null", message);
        receivedMessageObject = message;
        messageReceived = true;
        asyncTaskDone = callbackDidSucceed;
    }

}
