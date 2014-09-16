package com.digium.respokesdk;

/**
 * Created by jasonadams on 9/14/14.
 */
public class RespokeConnection {

    public String connectionID;
    private RespokeSignalingChannel signalingChannel;
    private RespokeEndpoint endpoint;
    private Object presence;

    public RespokeConnection(RespokeSignalingChannel channel, String newConnectionID, RespokeEndpoint newEndpoint) {
        signalingChannel = channel;
        connectionID = newConnectionID;
        endpoint = newEndpoint;
    }


    public RespokeEndpoint getEndpoint() {
        return endpoint;
    }
}
