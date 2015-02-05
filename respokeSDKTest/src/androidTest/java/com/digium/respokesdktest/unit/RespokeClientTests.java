package com.digium.respokesdktest.unit;

import android.app.Application;
import android.test.ApplicationTestCase;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeGroup;

import java.util.ArrayList;

/**
 * Created by jasonadams on 1/15/15.
 */
public class RespokeClientTests extends ApplicationTestCase<Application> {

    private boolean callbackDidSucceed;


    public RespokeClientTests() {
        super(Application.class);
    }


    public void testUnconnectedClientBehavior() {
        RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull(client);

        assertFalse("Should indicate not connected", client.isConnected());
        assertNull("Local endpoint should be nil if never connected", client.getEndpointID());

        callbackDidSucceed = false;

        ArrayList<String> groupList = new ArrayList<String>();
        groupList.add("newGroupID");
        client.joinGroups(groupList, new RespokeClient.JoinGroupCompletionListener() {
            @Override
            public void onSuccess(final ArrayList<RespokeGroup> groupList) {
                assertTrue("Should not call success handler", false);
            }

            @Override
            public void onError(String errorMessage) {
                callbackDidSucceed = true;
                assertEquals("Can't complete request when not connected. Please reconnect!", errorMessage);
            }
        });

        assertTrue("Did not call error handler when not connected", callbackDidSucceed);

        // Should behave when calling disconnect on a client that is not connected (i.e. don't crash)

        client.disconnect();

        assertNull("Should return a nil presence when it has not been set", client.getPresence());
    }


    public void testConnectParameterErrorHandling() {
        RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull(client);

        // Test bad parameters

        callbackDidSucceed = false;

        client.connect("myEndpointID", null, false, null, getContext(), new RespokeClient.ConnectCompletionListener(){
            @Override
            public void onError(String errorMessage) {
                callbackDidSucceed = true;
                assertEquals("AppID and endpointID must be specified", errorMessage);
            }
        });

        assertTrue("Did not call error handler", callbackDidSucceed);

        callbackDidSucceed = false;

        client.connect(null, "anAwesomeAppID", false, null, getContext(), new RespokeClient.ConnectCompletionListener(){
            @Override
            public void onError(String errorMessage) {
                callbackDidSucceed = true;
                assertEquals("AppID and endpointID must be specified", errorMessage);
            }
        });

        assertTrue("Did not call error handler", callbackDidSucceed);

        callbackDidSucceed = false;

        client.connect("", "", false, null, getContext(), new RespokeClient.ConnectCompletionListener(){
            @Override
            public void onError(String errorMessage) {
                callbackDidSucceed = true;
                assertEquals("AppID and endpointID must be specified", errorMessage);
            }
        });

        assertTrue("Did not call error handler", callbackDidSucceed);


        // Test more bad parameters


        callbackDidSucceed = false;

        client.connect(null, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                callbackDidSucceed = true;
                assertEquals("TokenID must be specified", errorMessage);
            }
        });

        assertTrue("Did not call error handler", callbackDidSucceed);

        callbackDidSucceed = false;

        client.connect("", null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                callbackDidSucceed = true;
                assertEquals("TokenID must be specified", errorMessage);
            }
        });

        assertTrue("Did not call error handler", callbackDidSucceed);


        // Should fail silently if no error handler is specified (i.e. don't crash)

        client.connect("myEndpointID", null, false, null, getContext(), null);
        client.connect("", null, getContext(), null);
    }


}
