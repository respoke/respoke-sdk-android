package com.digium.respokesdk;

import android.app.Application;
import android.test.ApplicationTestCase;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by jasonadams on 1/15/15.
 */
public class RespokeEndpointTest extends RespokeTestCase implements RespokeClient.Listener, RespokeEndpoint.Listener {

    private boolean callbackDidSucceed;
    private boolean messageReceived;
    private RespokeEndpoint firstEndpoint;
    private RespokeEndpoint secondEndpoint;
    private static final String TEST_MESSAGE = "This is a test message!";


    public void testUnconnectedEndpointBehavior() {
        RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull(client);

        assertNull("Should return nil if no endpoint exists", client.getEndpoint("someEndpointID", true));

        RespokeEndpoint endpoint = client.getEndpoint("someEndpointID", false);
        assertNotNull("Should create an endpoint instance if it does not exist and it so commanded to", endpoint);
        assertEquals("Should have the correct endpoint ID", "someEndpointID", endpoint.getEndpointID());

        ArrayList<RespokeConnection> connections = endpoint.getConnections();
        assertNotNull("Should return an empty list of connections when not connected", connections);
        assertTrue("Should return an empty list of connections when not connected", 0 == connections.size());

        endpoint.sendMessage("Hi there!", new Respoke.TaskCompletionListener(){
            @Override
            public void onSuccess() {
                assertTrue("Should not call success handler", false);
            }

            @Override
            public void onError(String errorMessage) {
                callbackDidSucceed = true;
                assertEquals("Can't complete request when not connected. Please reconnect!", errorMessage);
            }
        });

        assertTrue("Did not call error handler", callbackDidSucceed);
        assertNull("Should not create a call object when not connected", endpoint.startCall(null, getContext(), null,false));
    }


    /**
     *  This test will create two client instances with unique endpoint IDs. It will then send messages between the two to test functionality.
     */
    public void testEndpointMessaging() {
        // Create a client to test with
        final RespokeClient firstClient = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", firstClient);

        final String testEndpointID = generateTestEndpointID();
        assertNotNull("Should create test endpoint id", testEndpointID);

        asyncTaskDone = false;
        firstClient.setListener(this);
        firstClient.connect(testEndpointID, RespokeTest.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("First client should connect", firstClient.isConnected());


        // Create a second client to test with
        final RespokeClient secondClient = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", secondClient);

        final String secondTestEndpointID = generateTestEndpointID();
        assertNotNull("Should create test endpoint id", secondTestEndpointID);

        asyncTaskDone = false;
        secondClient.setListener(this);
        secondClient.connect(secondTestEndpointID, RespokeTest.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("First client should connect", secondClient.isConnected());


        // Build references to each of the endpoints
        firstEndpoint = secondClient.getEndpoint(testEndpointID, false);
        assertNotNull("Should create endpoint instance", firstEndpoint);
        firstEndpoint.setListener(this);

        secondEndpoint = firstClient.getEndpoint(secondTestEndpointID, false);
        assertNotNull("Should create endpoint instance", secondEndpoint);
        secondEndpoint.setListener(this);

        asyncTaskDone = false;
        callbackDidSucceed = false;
        firstEndpoint.sendMessage(TEST_MESSAGE, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                callbackDidSucceed = true;
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
        asyncTaskDone = true;
        assertTrue("Should not produce any client errors during endpoint testing", false);
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
        asyncTaskDone = true;
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        // Not under test
    }


}
