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
    public WeakReference<ResolvePresenceListener> resolveListenerReference;
    private String endpointID;
    public ArrayList<RespokeConnection> connections;
    private RespokeSignalingChannel signalingChannel;
    public Object presence;
    private WeakReference<RespokeDirectConnection> directConnectionReference;


    /**
     *  A listener interface to notify the receiver of events occurring with the endpoint
     */
    public interface Listener {

        /**
         *  Handle messages sent to the logged-in user from this one Endpoint.
         *
         *  @param message    The message
         *  @param timestamp  The timestamp of the message
         *  @param sender     The remote endpoint that sent the message
         */
        public void onMessage(String message, Date timestamp, RespokeEndpoint sender);


        /**
         *  A notification that the presence for an endpoint has changed
         *
         *  @param presence The new presence
         *  @param sender   The endpoint
         */
        public void onPresence(Object presence, RespokeEndpoint sender);

    }


    /**
     *  A listener interface to ask the receiver to resolve a list of presence values for an endpoint
     */
    public interface ResolvePresenceListener {


        /**
         *  Resolve the presence among multiple connections belonging to this endpoint
         *
         *  @param presenceArray An array of presence values
         *
         *  @return The resolved presence value to use
         */
        public Object resolvePresence(ArrayList<Object> presenceArray);

    }


    public RespokeEndpoint(RespokeSignalingChannel channel, String newEndpointID) {
        endpointID = newEndpointID;
        signalingChannel = channel;
        connections = new ArrayList<RespokeConnection>();
    }


    public void setListener(Listener listener) {
        listenerReference = new WeakReference<Listener>(listener);
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
                            if (null != completionListener) {
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        completionListener.onSuccess();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(final String errorMessage) {
                            if (null != completionListener) {
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        completionListener.onError(errorMessage);
                                    }
                                });
                            }
                        }
                    });
                } catch (JSONException e) {
                    if (null != completionListener) {
                        completionListener.onError("Error encoding message");
                    }
                }
            } else if (null != completionListener) {
                completionListener.onError("Specified endpoint does not have any connections");
            }
        } else if (null != completionListener) {
            completionListener.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public RespokeCall startCall(RespokeCall.Listener callListener, Context context, GLSurfaceView glView, boolean audioOnly) {
        RespokeCall call = new RespokeCall(signalingChannel, this, false);
        call.setListener(callListener);

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


    public void didReceiveMessage(final String message, final Date timestamp) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onMessage(message, timestamp, RespokeEndpoint.this);
                }
            }
        });
    }


    public void registerPresence(final Respoke.TaskCompletionListener completionListener) {
        if ((null != signalingChannel) && (signalingChannel.connected)) {
            ArrayList<String> endpointList = new ArrayList<String>();
            endpointList.add(endpointID);

            signalingChannel.registerPresence(endpointList, new RespokeSignalingChannel.RegisterPresenceListener() {
                @Override
                public void onSuccess(JSONArray initialPresenceData) {
                    if (null != initialPresenceData) {
                        for (int ii = 0; ii < initialPresenceData.length(); ii++) {
                            try {
                                JSONObject eachEndpointData = (JSONObject) initialPresenceData.get(ii);
                                String dataEndpointID = eachEndpointData.getString("endpointId");

                                // Ignore presence data related to other endpoints
                                if (endpointID.equals(dataEndpointID)) {
                                    JSONObject connectionData = eachEndpointData.getJSONObject("connectionStates");
                                    Iterator<?> keys = connectionData.keys();

                                    while (keys.hasNext()) {
                                        String eachConnectionID = (String)keys.next();
                                        JSONObject presenceDict = connectionData.getJSONObject(eachConnectionID);
                                        Object newPresence = presenceDict.get("type");
                                        RespokeConnection connection = null;

                                        for (RespokeConnection eachConnection : connections) {
                                            if (eachConnection.connectionID.equals(eachConnectionID)) {
                                                connection = eachConnection;
                                                break;
                                            }
                                        }

                                        if ((null != connection) && (null != newPresence)) {
                                            connection.presence = newPresence;
                                        }
                                    }
                                }
                            } catch (JSONException e) {
                                // Silently skip this problem
                            }
                        }

                        resolvePresence();
                    }

                    if (null != completionListener) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                completionListener.onSuccess();
                            }
                        });
                    }
                }

                @Override
                public void onError(final String errorMessage) {
                    if (null != completionListener) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                completionListener.onError(errorMessage);
                            }
                        });
                    }
                }
            });
        } else if (null != completionListener) {
            completionListener.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public void resolvePresence() {
        ArrayList<Object> list = new ArrayList<Object>();

        for (RespokeConnection eachConnection : connections) {
            Object connectionPresence = eachConnection.presence;

            if (null != connectionPresence) {
                list.add(connectionPresence);
            }
        }

        if (null != resolveListenerReference) {
            ResolvePresenceListener resolveListener = resolveListenerReference.get();
            if (null != resolveListener) {
                presence = resolveListener.resolvePresence(list);
            }
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
                newPresence = "available";
            }

            presence = newPresence;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onPresence(presence, RespokeEndpoint.this);
                }
            }
        });
    }


    public RespokeDirectConnection directConnection() {
        if (null != directConnectionReference) {
            return directConnectionReference.get();
        } else {
            return null;
        }
    }


    public void setDirectConnection(RespokeDirectConnection newDirectConnection) {
        if (null != newDirectConnection) {
            directConnectionReference = new WeakReference<RespokeDirectConnection>(newDirectConnection);
        } else {
            directConnectionReference = null;
        }
    }


    public RespokeDirectConnection startDirectConnection() {
        // The constructor will call the setDirectConnection method on this endpoint instance with a reference to the new RespokeDirectConnection object
        RespokeCall call = new RespokeCall(signalingChannel, this, true);
        call.startCall(null, null, false);

        return directConnection();
    }
}
