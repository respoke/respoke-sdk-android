package com.digium.respokesdk;

/**
 *  A delegate protocol to notify the receiver of events occurring with the call
 */
public interface RespokeCallDelegate {


    /**
     *  Receive a notification that an error has occurred while on a call
     *
     *  @param errorMessage A human-readable description of the error.
     *  @param sender       The RespokeCall that experienced the error
     */
    public void onError(String errorMessage, RespokeCall sender);


    /**
     *  When on a call, receive notification the call has been hung up
     *
     *  @param sender The RespokeCall that has hung up
     */
    public void onHangup(RespokeCall sender);


    /**
     *  When on a call, receive remote media when it becomes available. This is what you will need to provide if you want
     *  to show the user the other party's video during a call.
     *
     *  @param sender The RespokeCall that has connected
     */
    public void onConnected(RespokeCall sender);


}
