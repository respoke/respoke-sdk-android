package com.digium.respokesdk;

import android.content.Context;
import android.opengl.GLSurfaceView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by jasonadams on 9/14/14.
 */
public class RespokeEndpoint {

    public Listener listener;
    private String endpointID;
    public ArrayList<RespokeConnection> connections;
    private RespokeSignalingChannel signalingChannel;
    private Object presence;


    /**
     *  A delegate protocol to notify the receiver of events occurring with the endpoint
     */
    public interface Listener {

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


    public RespokeEndpoint(RespokeSignalingChannel channel, String newEndpointID) {
        endpointID = newEndpointID;
        signalingChannel = channel;
        connections = new ArrayList<RespokeConnection>();
    }


    public void sendMessage(String message, final Respoke.TaskCompletionListener completionListener) {
        if ((null != signalingChannel) && (signalingChannel.connected)) {
            if (connections.size() > 0) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("to", endpointID);
                    data.put("message", message);

                    signalingChannel.sendRESTMessage("post", "/v1/messages", data, new RespokeSignalingChannel.RESTListener() {
                        @Override
                        public void onSuccess(Object response) {
                            completionListener.onSuccess();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            completionListener.onError(errorMessage);
                        }
                    });
                } catch (JSONException e) {
                    completionListener.onError("Error encoding message");
                }
            } else {
                completionListener.onError("Specified endpoint does not have any connections");
            }
        } else {
            completionListener.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public RespokeCall startCall(RespokeCall.Listener callListener, Context context, GLSurfaceView glView, boolean audioOnly) {
        RespokeCall call = new RespokeCall(signalingChannel, this);
        call.listener = callListener;

        call.startCall(context, glView, audioOnly);

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
        listener.onMessage(message, this);
    }


    public void registerPresence(Respoke.TaskCompletionListener completionListener) {
        if ((null != signalingChannel) && (signalingChannel.connected)) {
            //TODO
        } else {
            completionListener.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public void resolvePresence() {
        //TODO
    }
}
