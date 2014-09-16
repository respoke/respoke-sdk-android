package com.digium.respokesdk;


import java.util.Map;

/**
 *  A delegate protocol to notify the receiver of events occurring with the connection status of the signaling channel
 */

public interface RespokeSignalingChannelDelegate {

    /**
     *  Receive a notification from the signaling channel that it has connected to the cloud infrastructure
     *
     *  @param sender      The signaling channel that triggered the event
     *  @param endpointID  The endpointID for this connection, as reported by the server
     */
    public void onConnect(RespokeSignalingChannel sender, String endpointID);


    /**
     *  Receive a notification from the signaling channel that it has disconnected to the cloud infrastructure
     *
     *  @param sender The signaling channel that triggered the event
     */
    public void onDisconnect(RespokeSignalingChannel sender);


    /**
     *  Receive a notification from the signaling channel that a remote endpoint is attempting to start a call
     *
     *  @param sdp           The SDP data for the call
     *  @param sessionID     The session ID of the call
     *  @param connectionID  The connectionID that is calling
     *  @param endpointID    The endpointID that is calling
     *  @param sender        The signaling channel that triggered the event
     */
    public void onIncomingCall(Map sdp, String sessionID, String connectionID, String endpointID, RespokeSignalingChannel sender);


    /**
     *  Receive a notification from the signaling channel that an error has occurred
     *
     *  @param errorMessage  Error message
     *  @param sender        The signaling channel that triggered the event
     */
    public void onError(String errorMessage, RespokeSignalingChannel sender);


    /**
     *  Receive a notification from the signaling channel that an endpoint has joined this group.
     *
     *  @param groupID      The ID of the group triggering the join message
     *  @param endpointID   The ID of the endpoint that to which the connection belongs
     *  @param connectionID The ID of the connection that has joined the group
     *  @param sender       The signaling channel that triggered the event
     */
    public void onJoinGroup(String groupID, String endpointID, String connectionID, RespokeSignalingChannel sender);


    /**
     *  Receive a notification from the signaling channel that an endpoint has left this group.
     *
     *  @param groupID      The ID of the group triggering the leave message
     *  @param endpointID   The ID of the endpoint that to which the connection belongs
     *  @param connectionID The ID of the connection that has left the group
     *  @param sender       The signaling channel that triggered the event
     */
    public void onLeaveGroup(String groupID, String endpointID, String connectionID, RespokeSignalingChannel sender);


    /**
     *  Receive a notification from the signaling channel that a message has been sent to this group
     *
     *  @param message    The body of the message
     *  @param endpointID The ID of the endpoint sending the message
     *  @param sender     The signaling channel that triggered the event
     */
    public void onMessage(String message, String endpointID, RespokeSignalingChannel sender);


    /**
     *  Receive a notification that a group message was received
     *
     *  @param message    The body of the message
     *  @param groupID    The ID of the group to which the message was sent
     *  @param endpointID The ID of the endpoint that sent the message
     *  @param sender     The signaling channel that triggered the event
     */
    public void onGroupMessage(String message, String groupID, String endpointID, RespokeSignalingChannel sender);


    /**
     *  Receive a notification that a presence change message was received
     *
     *  @param presence     The new presence value
     *  @param connectionID The connection ID whose presence changed
     *  @param endpointID     The endpoint ID to which the connection belongs
     *  @param sender       The signaling channel that triggered the event
     */
    public void onPresence(Object presence, String connectionID, String endpointID, RespokeSignalingChannel sender);


    /**
     *  Receive a notification from the signaling channel that a call has been created
     *
     *  @param call The RespokeCall instance that was created
     */
    public void callCreated(RespokeCall call);


    /**
     *  Receive a notification from the signaling channel that a call has terminated
     *
     *  @param call The RespokeCall instance that was terminated
     */
    public void callTerminated(RespokeCall call);


    /**
     *  Find a call with the specified session ID
     *
     *  @param sessionID SessionID to find
     *
     *  @return The RespokeCall instance with that sessionID. If not found, will return nil.
     */
    public RespokeCall callWithID(String sessionID);

}
