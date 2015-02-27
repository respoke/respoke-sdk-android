package com.digium.respokesdktest.unit;

import android.app.Application;
import android.test.ApplicationTestCase;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeConnection;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by jasonadams on 1/15/15.
 */
public class RespokeEndpointTests extends RespokeTestCase implements RespokeEndpoint.Listener, RespokeClient.ResolvePresenceListener {

    private boolean callbackDidSucceed;
    private RespokeEndpoint presenceTestEndpoint;
    private Object callbackPresence;
    private Object customPresenceResolution;
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

        callbackDidSucceed = false;
        asyncTaskDone = false;
        endpoint.sendMessage("Hi there!", new Respoke.TaskCompletionListener(){
            @Override
            public void onSuccess() {
                assertTrue("Should not call success handler", false);
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                callbackDidSucceed = true;
                assertEquals("Can't complete request when not connected. Please reconnect!", errorMessage);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Should call error handler", callbackDidSucceed);
        assertNull("Should not create a call object when not connected", endpoint.startCall(null, getContext(), null,false));
    }


    public void testPresence() {
        RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull(client);

        assertNull("Should return nil if no endpoint exists", client.getEndpoint("someEndpointID", true));

        presenceTestEndpoint = client.getEndpoint("someEndpointID", false);
        assertNotNull("Should create an endpoint instance if it does not exist and it so commanded to", presenceTestEndpoint);
        presenceTestEndpoint.setListener(this);

        assertNull("Presence should initially be null", presenceTestEndpoint.presence);


        // Test presence with no connections


        callbackDidSucceed = false;
        callbackPresence = null;
        asyncTaskDone = false;
        presenceTestEndpoint.resolvePresence();
        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Presence delegate should be called", callbackDidSucceed);
        assertTrue("Should resolve to the correct presence value", "available".equals(callbackPresence));


        // Test presence with one connection


        RespokeConnection connection = new RespokeConnection(null, Respoke.makeGUID(), presenceTestEndpoint);
        assertNotNull("Should create connection", connection);
        assertNull("Presence should initially be null", connection.presence);
        presenceTestEndpoint.connections.add(connection);

        callbackDidSucceed = false;
        callbackPresence = null;
        asyncTaskDone = false;
        presenceTestEndpoint.resolvePresence();
        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Presence delegate should be called", callbackDidSucceed);
        assertTrue("Should resolve to the correct presence value", "available".equals(callbackPresence));

        ArrayList<String> options = new ArrayList<String>();
        options.add("chat");
        options.add("available");
        options.add("away");
        options.add("dnd");
        options.add("xa");
        options.add("unavailable");

        for (String eachPresence : options) {
            connection.presence = eachPresence;

            callbackDidSucceed = false;
            callbackPresence = null;
            asyncTaskDone = false;
            presenceTestEndpoint.resolvePresence();
            assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
            assertTrue("Presence listener should be called", callbackDidSucceed);
            assertTrue("Expected presence to be [" + eachPresence + "] but found [" + callbackPresence + "]", eachPresence.equals(connection.presence));
            assertTrue("Resolved endpoint presence should match the connections", eachPresence.equals(presenceTestEndpoint.presence));
        }


        // Test presence with 2 connections


        RespokeConnection secondConnection = new RespokeConnection(null, Respoke.makeGUID(), presenceTestEndpoint);
        assertNotNull("Should create connection", secondConnection);
        assertNull("Presence should initially be null", secondConnection.presence);
        presenceTestEndpoint.connections.add(secondConnection);

        for (Integer ii = 0; ii < options.size(); ii++) {
            String firstPresence = options.get(ii);

            for (Integer jj = 0; jj < options.size(); jj++) {
                String secondPresence = options.get(jj);

                connection.presence = firstPresence;
                secondConnection.presence = secondPresence;

                String expectedPresence = null;

                if (ii <= jj) {
                    expectedPresence = firstPresence;
                } else {
                    expectedPresence = secondPresence;
                }

                callbackDidSucceed = false;
                callbackPresence = null;
                asyncTaskDone = false;
                presenceTestEndpoint.resolvePresence();
                assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
                assertTrue("Presence delegate should be called", callbackDidSucceed);
                assertTrue("Expected presence to be [" + expectedPresence + "] but found [" + callbackPresence + "]", expectedPresence.equals(callbackPresence));
                assertTrue("Resolved endpoint presence should match the connections", expectedPresence.equals(presenceTestEndpoint.presence));
            }
        }
    }


    public void testCustomPresence() {
        RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull(client);
        client.setResolvePresenceListener(this);

        presenceTestEndpoint = client.getEndpoint("someEndpointID", false);
        assertNotNull("Should create an endpoint instance if it does not exist and it so commanded to", presenceTestEndpoint);
        presenceTestEndpoint.setListener(this);

        RespokeConnection connection1 = new RespokeConnection(null, Respoke.makeGUID(), presenceTestEndpoint);
        assertNotNull("Should create connection", connection1);
        presenceTestEndpoint.connections.add(connection1);

        RespokeConnection connection2 = new RespokeConnection(null, Respoke.makeGUID(), presenceTestEndpoint);
        assertNotNull("Should create connection", connection2);
        presenceTestEndpoint.connections.add(connection2);

        RespokeConnection connection3 = new RespokeConnection(null, Respoke.makeGUID(), presenceTestEndpoint);
        assertNotNull("Should create connection", connection3);
        presenceTestEndpoint.connections.add(connection3);


        // Test presence values that are not strings


        customPresenceResolution = new HashMap<String, String>();
        ((HashMap<String, String>)customPresenceResolution).put("myRealPresence", "ready");

        HashMap<String, String> connection1Presence = new HashMap<String, String>();
        connection1Presence.put("myRealPresence", "not ready");
        connection1.presence = connection1Presence;

        connection2.presence = customPresenceResolution;

        HashMap<String, String> connection3Presence = new HashMap<String, String>();
        connection3Presence.put("myRealPresence", "not ready");
        connection3.presence = connection3Presence;

        callbackDidSucceed = false;
        callbackPresence = null;
        presenceTestEndpoint.resolvePresence();
        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Presence delegate should be called", callbackDidSucceed);
        assertTrue("Custom presence should be a hash map", presenceTestEndpoint.presence instanceof HashMap);
        HashMap<String, String> resolvedPresence = (HashMap<String, String>)presenceTestEndpoint.presence;
        assertTrue("Should resolve to correct custom presence", resolvedPresence.get("myRealPresence").equals("ready"));
        assertTrue("Should resolve correct custom presence in callback", ((HashMap<String, String>)callbackPresence).get("myRealPresence").equals("ready"));
    }


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint sender) {
        // Not under test
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        assertTrue("Sender should be set correctly", sender == presenceTestEndpoint);
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        callbackPresence = presence;
        callbackDidSucceed = true;
        asyncTaskDone = true;
    }


    // RespokeClient.ResolvePresenceListener methods


    public Object resolvePresence(ArrayList<Object> presenceArray) {
        assertTrue("presence array should contain the correct number of values", 3 == presenceArray.size());
        return customPresenceResolution;
    }


}
