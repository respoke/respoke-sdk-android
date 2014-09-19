package com.digium.respokesdk;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by jasonadams on 9/14/14.
 */
public class RespokeEndpoint {

    public RespokeEndpointDelegate delegate;
    private String endpointID;
    public ArrayList<RespokeConnection> connections;
    private RespokeSignalingChannel signalingChannel;
    private Object presence;


    public RespokeEndpoint(RespokeSignalingChannel channel, String newEndpointID) {
        endpointID = newEndpointID;
        signalingChannel = channel;
        connections = new ArrayList<RespokeConnection>();
    }


    public void sendMessage(String message, final RespokeTaskCompletionDelegate completionDelegate) {
        if ((null != signalingChannel) && (signalingChannel.connected)) {
            if (connections.size() > 0) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("to", endpointID);
                    data.put("message", message);

                    signalingChannel.sendRESTMessage("post", "/v1/messages", data, new RespokeSignalingChannelRESTDelegate() {
                        @Override
                        public void onSuccess(Object response) {
                            completionDelegate.onSuccess();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            completionDelegate.onError(errorMessage);
                        }
                    });
                } catch (JSONException e) {
                    completionDelegate.onError("Error encoding message");
                }
            } else {
                completionDelegate.onError("Specified endpoint does not have any connections");
            }
        } else {
            completionDelegate.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public RespokeCall startVideoCall(RespokeCallDelegate callDelegate, Context context) {
        RespokeCall call = new RespokeCall(signalingChannel, this, false);
        call.delegate = callDelegate;

        //todo hook up views

        call.startCall(context);

        return call;
    }


    public RespokeCall startAudioCall(RespokeCallDelegate callDelegate, Context context) {
        RespokeCall call = new RespokeCall(signalingChannel, this, true);
        call.delegate = callDelegate;

        call.startCall(context);

        return call;
    }


    public String getEndpointID() {
        return endpointID;
    }


    /*public ArrayList<RespokeConnection> getConnections() {
        ArrayList<RespokeConnection> newArray = new ArrayList<RespokeConnection>();
        newArray.addAll(connections);
        return newArray;
    }*/

    public ArrayList<RespokeConnection> getConnections() {
        return connections;
    }


    public void didReceiveMessage(String message) {
        delegate.onMessage(message, this);
    }


    public void registerPresence(RespokeTaskCompletionDelegate completionDelegate) {
        if ((null != signalingChannel) && (signalingChannel.connected)) {
            //TODO
        } else {
            completionDelegate.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public void resolvePresence() {
        //TODO
    }
}
