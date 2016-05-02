/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdk;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

/**
 *  Represents remote Endpoints. Endpoints are users of this application that are not the one logged into this
 *  instance of the application. An Endpoint could be logged in from multiple other instances of this app, each of
 *  which is represented by a Connection. The client can interact with endpoints by calling them or
 *  sending them messages. An endpoint can be a person using an app from a browser or a script using the APIs on
 *  a server.
 */
public class RespokeEndpoint {

    private WeakReference<Listener> listenerReference;
    private String endpointID;
    public ArrayList<RespokeConnection> connections;
    private RespokeSignalingChannel signalingChannel;
    public Object presence;
    private WeakReference<RespokeDirectConnection> directConnectionReference;
    private WeakReference<RespokeClient> clientReference;


    /**
     *  A listener interface to notify the receiver of events occurring with the endpoint
     */
    public interface Listener {

        /**
         *  Handle messages sent to the logged-in user from this one Endpoint.
         *
         *  @param message   The message
         *  @param timestamp The timestamp of the message
         *  @param endpoint  The remote endpoint that sent the message
         *  @param didSend   True if the specified endpoint sent the message, False if it received the message
         */
        public void onMessage(String message, Date timestamp, RespokeEndpoint endpoint, boolean didSend);


        /**
         *  A notification that the presence for an endpoint has changed
         *
         *  @param presence The new presence
         *  @param sender   The endpoint
         */
        public void onPresence(Object presence, RespokeEndpoint sender);

    }


    /**
     *  The constructor for this class
     *
     *  @param channel        The signaling channel managing communications with this endpoint
     *  @param newEndpointID  The ID for this endpoint
     *  @param client      The client to which this endpoint instance belongs
     */
    public RespokeEndpoint(RespokeSignalingChannel channel, String newEndpointID, RespokeClient client) {
        endpointID = newEndpointID;
        signalingChannel = channel;
        connections = new ArrayList<RespokeConnection>();
        clientReference = new WeakReference<RespokeClient>(client);
    }


    /**
     *  Set a receiver for the Listener interface
     *
     *  @param listener  The new receiver for events from the Listener interface for this endpoint instance
     */
    public void setListener(Listener listener) {
        listenerReference = new WeakReference<Listener>(listener);
    }


    /**
     *  Send a message to the endpoint through the infrastructure.
     *
     *  @param message             The message to send
     *  @param push                A flag indicating if a push notification should be sent for this message
     *  @param ccSelf              A flag indicating if the message should be copied to other devices the client might be logged into
     *  @param completionListener  A listener to receive a notification on the success of the asynchronous operation
     */
    public void sendMessage(String message, boolean push, boolean ccSelf, final Respoke.TaskCompletionListener completionListener) {
        if ((null != signalingChannel) && (signalingChannel.connected)) {
            try {
                JSONObject data = new JSONObject();
                data.put("to", endpointID);
                data.put("message", message);
                data.put("push", push);
                data.put("ccSelf", ccSelf);

                signalingChannel.sendRESTMessage("post", "/v1/messages", data, new RespokeSignalingChannel.RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        Respoke.postTaskSuccess(completionListener);
                    }

                    @Override
                    public void onError(final String errorMessage) {
                        Respoke.postTaskError(completionListener, errorMessage);
                    }
                });
            } catch (JSONException e) {
                Respoke.postTaskError(completionListener, "Error encoding message");
            }
        } else {
            Respoke.postTaskError(completionListener, "Can't complete request when not connected. Please reconnect!");
        }
    }


    /**
     *  Create a new call with audio and optionally video.
     *
     *  @param callListener  A listener to receive notifications of call related events
     *  @param context       An application context with which to access system resources
     *  @param glView        A GLSurfaceView into which video from the call should be rendered, or null if the call is audio only
     *  @param audioOnly     Specify true for an audio-only call
     *
     *  @return A new RespokeCall instance
     */
    public RespokeCall startCall(RespokeCall.Listener callListener, Context context, GLSurfaceView glView, boolean audioOnly) {
        RespokeCall call = null;

        if ((null != signalingChannel) && (signalingChannel.connected)) {
            call = new RespokeCall(signalingChannel, this, false);
            call.setListener(callListener);

            call.startCall(context, glView, audioOnly);
        }

        return call;
    }


    /**
     *  Get the endpoint's ID
     *
     *  @return The ID
     */
    public String getEndpointID() {
        return endpointID;
    }


    /**
     *  Returns a connection with the specified ID, and optionally creates one if it does not exist
     *
     *  @param connectionID  The ID of the connection
     *  @param skipCreate    Whether or not to create a new connection if it is not found
     *
     *  @return The connection that matches the specified ID, or null if not found and skipCreate is true
     */
    public RespokeConnection getConnection(String connectionID, boolean skipCreate) {
        RespokeConnection connection = null;

        for (RespokeConnection eachConnection : connections) {
            if (eachConnection.connectionID.equals(connectionID)) {
                connection = eachConnection;
                break;
            }
        }

        if ((null == connection) && !skipCreate) {
            connection = new RespokeConnection(connectionID, this);
            connections.add(connection);
        }

        return connection;
    }


    /**
     *  Get an array of connections associated with this endpoint
     *
     *  @return The array of connections
     */
    public ArrayList<RespokeConnection> getConnections() {
        return connections;
    }


    /**
     *  Process a received message. This is used internally to the SDK and should not be called directly by your client application.
     *
     *  @param message The body of the message
     *  @param timestamp The message timestamp
     */
    public void didReceiveMessage(final String message, final Date timestamp) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != listenerReference) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onMessage(message, timestamp, RespokeEndpoint.this, false);
                    }
                }
            }
        });
    }


    /**
     *  Process a sent message. This is used internally to the SDK and should not be called directly by your client application.
     *
     *  @param message The body of the message
     *  @param timestamp The message timestamp
     */
    public void didSendMessage(final String message, final Date timestamp) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != listenerReference) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onMessage(message, timestamp, RespokeEndpoint.this, true);
                    }
                }
            }
        });
    }


    /**
     *  Find the presence out of all known connections with the highest priority (most availability)
     *  and set it as the endpoint's resolved presence.
     */
    public void resolvePresence() {
        ArrayList<Object> list = new ArrayList<Object>();

        for (RespokeConnection eachConnection : connections) {
            Object connectionPresence = eachConnection.presence;

            if (null != connectionPresence) {
                list.add(connectionPresence);
            }
        }

        RespokeClient client = null;
        RespokeClient.ResolvePresenceListener resolveListener = null;
        if (null != clientReference) {
            client = clientReference.get();

            if (client != null) {
                resolveListener = client.getResolvePresenceListener();
            }
        }

        if (null != resolveListener) {
            presence = resolveListener.resolvePresence(list);
        } else {
            ArrayList<String> options = new ArrayList<String>();
            options.add("chat");
            options.add("available");
            options.add("away");
            options.add("dnd");
            options.add("xa");
            options.add("unavailable");

            String newPresence = null;
            for (String eachOption : options) {
                for (Object eachObject : list) {
                    if (eachObject instanceof String) {
                        String eachObjectString = (String) eachObject;

                        if (eachObjectString.toLowerCase().equals(eachOption)) {
                            newPresence = eachOption;
                        }
                    }
                }

                if (null != newPresence) {
                    break;
                }
            }

            if (null == newPresence) {
                newPresence = "unavailable";
            }

            presence = newPresence;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != listenerReference) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onPresence(presence, RespokeEndpoint.this);
                    }
                }
            }
        });
    }


    /**
     *  Get the active direct connection with this endpoint (if any)
     *
     *  @return  The active direct connection instance, or null otherwise
     */
    public RespokeDirectConnection directConnection() {
        if (null != directConnectionReference) {
            return directConnectionReference.get();
        } else {
            return null;
        }
    }


    /**
     *  Associate a direct connection object with this endpoint. This method is used internally by the SDK should not be called by your client application.
     *
     *  @param newDirectConnection  The direct connection to associate
     */
    public void setDirectConnection(RespokeDirectConnection newDirectConnection) {
        if (null != newDirectConnection) {
            directConnectionReference = new WeakReference<RespokeDirectConnection>(newDirectConnection);
        } else {
            directConnectionReference = null;
        }
    }


    /**
     *  Create a new DirectConnection.  This method creates a new Call as well, attaching this DirectConnection to
     *  it for the purposes of creating a peer-to-peer link for sending data such as messages to the other endpoint.
     *  Information sent through a DirectConnection is not handled by the cloud infrastructure.  
     *
     *  @return The DirectConnection which can be used to send data and messages directly to the other endpoint.
     */
    public RespokeDirectConnection startDirectConnection() {
        // The constructor will call the setDirectConnection method on this endpoint instance with a reference to the new RespokeDirectConnection object
        RespokeCall call = new RespokeCall(signalingChannel, this, true);
        call.startCall(null, null, false);

        return directConnection();
    }
}
