package com.digium.respokesdk;

/**
 * Created by jasonadams on 9/14/14.
 */
public interface RespokeGroupDelegate {


    /**
     *  Receive a notification that an connection has joined this group.
     *
     *  @param connection The RespokeConnection that joined the group
     *  @param sender     The RespokeGroup that the connection has joined
     */
    public void onJoin(RespokeConnection connection, RespokeGroup sender);


    /**
     *  Receive a notification that an connection has left this group.
     *
     *  @param connection The RespokeConnection that left the group
     *  @param sender     The RespokeGroup that the connection has left
     */
    public void onLeave(RespokeConnection connection, RespokeGroup sender);


    /**
     *  Receive a notification that a group message has been received
     *
     *  @param message  The body of the message
     *  @param endpoint The endpoint that sent the message
     *  @param sender   The group that received the message
     */
    public void onGroupMessage(String message, RespokeEndpoint endpoint, RespokeGroup sender);


}
