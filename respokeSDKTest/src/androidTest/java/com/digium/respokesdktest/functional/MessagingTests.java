package com.digium.respokesdktest.functional;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.Date;

/**
 * Created by jasonadams on 1/23/15.
 */
public class MessagingTests extends RespokeTestCase implements RespokeClient.Listener, RespokeEndpoint.Listener {

    private boolean callbackDidSucceed;
    private boolean messageReceived;
    private RespokeEndpoint firstEndpoint;
    private RespokeEndpoint secondEndpoint;
    private static final String TEST_MESSAGE = "This is a test message!";


    /**
     *  This test will create two client instances with unique endpoint IDs. It will then send messages between the two to test functionality.
     */
    public void testEndpointMessaging() {
        // Create a client to test with
        final RespokeClient firstClient = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", firstClient);
        firstClient.baseURL = TEST_RESPOKE_BASE_URL;

        final String testEndpointID = generateTestEndpointID();
        assertNotNull("Should create test endpoint id", testEndpointID);

        asyncTaskDone = false;
        firstClient.setListener(this);
        firstClient.connect(testEndpointID, RespokeTestCase.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect. error: " + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("First client should connect", firstClient.isConnected());


        // Create a second client to test with
        final RespokeClient secondClient = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", secondClient);
        secondClient.baseURL = TEST_RESPOKE_BASE_URL;

        final String secondTestEndpointID = generateTestEndpointID();
        assertNotNull("Should create test endpoint id", secondTestEndpointID);

        asyncTaskDone = false;
        secondClient.setListener(this);
        secondClient.connect(secondTestEndpointID, RespokeTestCase.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Second client should connect", secondClient.isConnected());


        // Build references to each of the endpoints
        firstEndpoint = secondClient.getEndpoint(testEndpointID, false);
        assertNotNull("Should create endpoint instance", firstEndpoint);
        firstEndpoint.setListener(this);

        secondEndpoint = firstClient.getEndpoint(secondTestEndpointID, false);
        assertNotNull("Should create endpoint instance", secondEndpoint);
        secondEndpoint.setListener(this);

        asyncTaskDone = false;
        messageReceived = false;
        callbackDidSucceed = false;
        firstEndpoint.sendMessage(TEST_MESSAGE, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
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
        assertTrue("Should call onMessage delegate when a message is received", messageReceived);
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


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint sender) {
        assertTrue("Message sent should be the message received", message.equals(TEST_MESSAGE));
        assertTrue("Should indicate correct sender endpoint ID", sender.getEndpointID().equals(secondEndpoint.getEndpointID()));
        assertNotNull("Should include a timestamp", timestamp);
        messageReceived = true;
        asyncTaskDone = callbackDidSucceed;
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        // Not under test
    }


}
