package com.digium.respokesdk;

/**
 * A delegate protocol to notify the receiver of events occurring with the client
 */
public interface RespokeClientDelegate {


    /**
     *  Receive notification Respoke has successfully connected to the cloud.
     *
     *  @param sender The RespokeClient that has connected
     */
    public void onConnect(RespokeClient sender);


    /**
     *  Receive notification Respoke has successfully disconnected from the cloud.
     *
     *  @param sender        The RespokeClient that has disconnected
     *  @param reconnecting  Indicates if the Respoke SDK is attempting to automatically reconnect
     */
    public void onDisconnect(RespokeClient sender, boolean reconnecting);


    /**
     *  Handle an error that resulted from a method call.
     *
     *  @param sender The RespokeClient that is reporting the error
     *  @param errorMessage  The error that has occurred
     */
    public void onError(RespokeClient sender, String errorMessage);


    /**
     *  Receive notification that the client is receiving a call from a remote party.
     *
     *  @param sender The RespokeClient that is receiving the call
     *  @param call   A reference to the incoming RespokeCall object
     */
    public void onCall(RespokeClient sender, RespokeCall call);
}
