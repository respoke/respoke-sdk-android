package com.digium.respokesdk;

import android.app.Application;
import android.test.ApplicationTestCase;

import java.util.ArrayList;

/**
 * Created by jasonadams on 1/15/15.
 */
public class RespokeEndpointTest extends ApplicationTestCase<Application> {

    private boolean callbackDidSucceed;


    public RespokeEndpointTest() {
        super(Application.class);
    }


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
}
