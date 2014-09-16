package com.digium.respokesdk;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by jasonadams on 9/14/14.
 */
public class RespokeCall {

    RespokeSignalingChannel signalingChannel;
    private ArrayList iceServers;
    private ArrayList queuedRemoteCandidates;
    private ArrayList queuedLocalCandidates;
    private boolean caller;
    private boolean waitingForAnswer;
    private Map incomingSDP;
    private String sessionID;
    private String toConnection;
    private RespokeEndpoint endpoint;
    private boolean audioOnly;


    public RespokeCall(RespokeSignalingChannel channel) {
        commonConstructor(channel);
    }


    public RespokeCall(RespokeSignalingChannel channel, RespokeEndpoint newEndpoint, boolean newAudioOnly) {
        commonConstructor(channel);

        endpoint = newEndpoint;
        audioOnly = newAudioOnly;
    }


    public RespokeCall(RespokeSignalingChannel channel, Map sdp, String newSessionID, String newConnectionID, RespokeEndpoint newEndpoint) {
        commonConstructor(channel);

        incomingSDP = sdp;
        sessionID = newSessionID;
        endpoint = newEndpoint;
        toConnection = newConnectionID;
    }


    public void commonConstructor(RespokeSignalingChannel channel) {
        signalingChannel = channel;
        iceServers = new ArrayList();
        queuedLocalCandidates = new ArrayList();
        //todo session ID
        signalingChannel.delegate.callCreated(this);

        //TODO resign active handler?
    }


    public String getSessionID() {
        return sessionID;
    }


    public void disconnect() {
        //TODO
    }


    public void startCall() {
        caller = true;
        waitingForAnswer = true;

        //TODO
    }


    public void answer() {
        if (!caller) {
            //TODO
        }
    }


    public void hangup(boolean shouldSendHangupSignal) {
        if (shouldSendHangupSignal) {
            //TODO
        }

        disconnect();
    }
}
