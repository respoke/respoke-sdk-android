package com.digium.respokesdk;

/**
 *  A delegate protocol to notify the receiver of events occurring with the endpoint
 */
public interface RespokeEndpointDelegate {

    /**
     *  Handle messages sent to the logged-in user from this one Endpoint.
     *
     *  @param message The message
     *  @param sender  The remote endpoint that sent the message
     */
    public void onMessage(String message, RespokeEndpoint sender);


    /**
     *  A notification that the presence for an endpoint has changed
     *
     *  @param presence The new presence
     *  @param sender   The endpoint
     */
    public void onPresence(Object presence, RespokeEndpoint sender);

}
