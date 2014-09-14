package com.digium.respokesdk;


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
}
