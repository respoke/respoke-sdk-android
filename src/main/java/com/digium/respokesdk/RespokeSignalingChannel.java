package com.digium.respokesdk;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.socketio.Acknowledge;
import com.koushikdutta.async.http.socketio.ConnectCallback;
import com.koushikdutta.async.http.socketio.DisconnectCallback;
import com.koushikdutta.async.http.socketio.ErrorCallback;
import com.koushikdutta.async.http.socketio.EventCallback;
import com.koushikdutta.async.http.socketio.SocketIOClient;

import com.digium.respokesdk.RestAPI.APITransaction;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;


/**
 *  The purpose of this class is to make a method call for each API call
 *  to the backend REST interface.  This class takes care of App authentication, websocket connection,
 *  Endpoint authentication, and all App interactions thereafter.
 */
public class RespokeSignalingChannel {

    private static final String TAG = "RespokeSignalingChannel";
    private static final String RESPOKE_SOCKETIO_PORT = "443";

    public boolean connected;
    private WeakReference<Listener> listenerReference;
    private String appToken;
    private SocketIOClient client;
    private String connectionID;


    /**
     *  A delegate protocol to notify the receiver of events occurring with the connection status of the signaling channel
     */
    public interface Listener {

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
         *  @param timestamp     The timestamp when the call was initiated
         *  @param sender        The signaling channel that triggered the event
         */
        public void onIncomingCall(JSONObject sdp, String sessionID, String connectionID, String endpointID, Date timestamp, RespokeSignalingChannel sender);


        /**
         *  Receive a notification from the signaling channel that a remote endpoint is attempting to start a direct connection
         *
         *  @param sdp           The SDP data for the directConnection
         *  @param sessionID     The session ID of the directConnection
         *  @param connectionID  The connectionID that is calling
         *  @param endpointID    The endpointID that is calling
         *  @param timestamp     The timestamp when the call was initiated
         *  @param sender        The signaling channel that triggered the event
         */
        public void onIncomingDirectConnection(JSONObject sdp, String sessionID, String connectionID, String endpointID, Date timestamp, RespokeSignalingChannel sender);


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
         *  @param timestamp  The timestamp of the message
         *  @param endpointID The ID of the endpoint sending the message
         *  @param sender     The signaling channel that triggered the event
         */
        public void onMessage(String message, Date timestamp, String endpointID, RespokeSignalingChannel sender);


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


        /**
         *  This event is fired when the logged-in endpoint is receiving a request to open a direct connection
         *  to another endpoint.  If the user wishes to allow the direct connection, calling 'accept' on the
         *  direct connection will allow the connection to be set up.
         *
         *  @param directConnection The direct connection object
         *  @param endpoint         The remote endpoint
         */
        public void directConnectionAvailable(RespokeDirectConnection directConnection, RespokeEndpoint endpoint);
    }


    /**
     * A listener interface to receive a notification that the REST message transmission has completed
     */
    public interface RESTListener {

        public void onSuccess(Object response);

        public void onError(String errorMessage);

    }


    /**
     * A listener interface to receive a notification that this client has been registered to receive presence updates for a specific endpoint
     */
    public interface RegisterPresenceListener {

        public void onSuccess(JSONArray initialPresenceData);

        public void onError(String errorMessage);

    }


    public RespokeSignalingChannel(String token, Listener newListener) {
        appToken = token;
        listenerReference = new WeakReference<Listener>(newListener);
    }


    public Listener GetListener() {
        return listenerReference.get();
    }


    public void authenticate() {
        String connectURL = "https://" + APITransaction.RESPOKE_BASE_URL + ":" + RESPOKE_SOCKETIO_PORT + "?app-token=" + appToken;

        SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), connectURL, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient newClient) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }

                client = newClient;
                connected = true;

                client.setDisconnectCallback(new DisconnectCallback() {
                    @Override
                    public void onDisconnect(Exception e) {
                        Log.d(TAG, "Socket disconnected");
                        if (null != e) {
                            e.printStackTrace();
                        }

                        if (connected) {
                            connected = false;
                            client = null;

                            Listener listener = listenerReference.get();
                            if (null != listener) {
                                listener.onDisconnect(RespokeSignalingChannel.this);
                            }
                        }
                    }
                });

                client.setErrorCallback(new ErrorCallback() {
                    @Override
                    public void onError(String error) {
                        Log.d(TAG, "Socket error: " + error);

                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            listener.onError(error, RespokeSignalingChannel.this);
                        }
                    }
                });

                client.on("join", new EventCallback() {
                    @Override
                    public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                        for (int ii = 0; ii < arguments.length(); ii++) {
                            try {
                                JSONObject eachEvent = arguments.getJSONObject(ii);
                                String endpoint = eachEvent.getString("endpointId");
                                String connection = eachEvent.getString("connectionId");
                                JSONObject header = eachEvent.getJSONObject("header");
                                String groupID = header.getString("channel");

                                Listener listener = listenerReference.get();
                                if (null != listener) {
                                    listener.onJoinGroup(groupID, endpoint, connection, RespokeSignalingChannel.this);
                                }
                            } catch (JSONException e) {
                                Log.d(TAG, "Error parsing received event");
                            }
                        }
                    }
                });

                client.on("leave", new EventCallback() {
                    @Override
                    public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                        for (int ii = 0; ii < arguments.length(); ii++) {
                            try {
                                JSONObject eachEvent = arguments.getJSONObject(ii);
                                String endpoint = eachEvent.getString("endpointId");
                                String connection = eachEvent.getString("connectionId");
                                JSONObject header = eachEvent.getJSONObject("header");
                                String groupID = header.getString("channel");

                                Listener listener = listenerReference.get();
                                if (null != listener) {
                                    listener.onLeaveGroup(groupID, endpoint, connection, RespokeSignalingChannel.this);
                                }
                            } catch (JSONException e) {
                                Log.d(TAG, "Error parsing received event");
                            }
                        }
                    }
                });

                client.on("message", new EventCallback() {
                    @Override
                    public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                        for (int ii = 0; ii < arguments.length(); ii++) {
                            try {
                                JSONObject eachEvent = arguments.getJSONObject(ii);
                                String message = eachEvent.getString("body");
                                JSONObject header = eachEvent.getJSONObject("header");
                                String endpoint = header.getString("from");
                                Date messageDate;

                                if (!header.isNull("timestamp")) {
                                    messageDate = new Date(header.getLong("timestamp"));
                                } else {
                                    // Just use the current time if no date is specified in the header data
                                    messageDate = new Date();
                                }

                                Listener listener = listenerReference.get();
                                if (null != listener) {
                                    listener.onMessage(message, messageDate, endpoint, RespokeSignalingChannel.this);
                                }
                            } catch (JSONException e) {
                                Log.d(TAG, "Error parsing received event");
                            }
                        }
                    }
                });

                client.on("signal", new EventCallback() {
                    @Override
                    public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                        for (int ii = 0; ii < arguments.length(); ii++) {
                            try {
                                JSONObject eachEvent = arguments.getJSONObject(ii);
                                routeSignal(eachEvent);
                            } catch (JSONException e) {
                                Log.d(TAG, "Error parsing received event");
                            }
                        }
                    }
                });

                client.on("pubsub", new EventCallback() {
                    @Override
                    public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                        for (int ii = 0; ii < arguments.length(); ii++) {
                            try {
                                JSONObject eachEvent = arguments.getJSONObject(ii);
                                String message = eachEvent.getString("message");
                                JSONObject header = eachEvent.getJSONObject("header");
                                String endpointID = header.getString("from");
                                String groupID = header.getString("channel");

                                Listener listener = listenerReference.get();
                                if (null != listener) {
                                    listener.onGroupMessage(message, groupID, endpointID, RespokeSignalingChannel.this);
                                }
                            } catch (JSONException e) {
                                Log.d(TAG, "Error parsing received event");
                            }
                        }
                    }
                });

                client.on("presence", new EventCallback() {
                    @Override
                    public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                        for (int ii = 0; ii < arguments.length(); ii++) {
                            try {
                                JSONObject eachEvent = arguments.getJSONObject(ii);
                                Object type = eachEvent.getString("type");
                                JSONObject header = eachEvent.getJSONObject("header");
                                String endpointID = header.getString("from");
                                String connectionID = header.getString("fromConnection");

                                Listener listener = listenerReference.get();
                                if (null != listener) {
                                    listener.onPresence(type, connectionID, endpointID, RespokeSignalingChannel.this);
                                }
                            } catch (JSONException e) {
                                Log.d(TAG, "Error parsing received event");
                            }
                        }
                    }
                });

                // Once the socket is connected, perform a post to get the connection and endpoint IDs for this client
                sendRESTMessage("post", "/v1/connections", null, new RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            if (response instanceof JSONObject) {
                                try {
                                    JSONObject responseJSON = (JSONObject) response;
                                    String endpointID = responseJSON.getString("endpointId");
                                    connectionID = responseJSON.getString("id");

                                    listener.onConnect(RespokeSignalingChannel.this, endpointID);
                                } catch (JSONException e) {
                                    listener.onError("Unexpected response from server", RespokeSignalingChannel.this);
                                }
                            } else {
                                listener.onError("Unexpected response from server", RespokeSignalingChannel.this);
                            }
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            listener.onError(errorMessage, RespokeSignalingChannel.this);
                        }
                    }
                });
            }
        });
    }


    public void disconnect() {
        if (null != client) {
            client.disconnect();
        }
    }


    public void registerPresence(ArrayList<String> endpointList, final RegisterPresenceListener completionListener) {
        if (connected) {
            JSONObject data = new JSONObject();

            try {
                data.put("endpointList", new JSONArray(endpointList));

                sendRESTMessage("post", "/v1/presenceobservers", data, new RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        try {
                            JSONArray responseArray = new JSONArray((String) response);
                            completionListener.onSuccess(responseArray);
                        } catch (JSONException e) {
                            completionListener.onError("Unexpected response from server");
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        completionListener.onError(errorMessage);
                    }
                });
            } catch (JSONException e) {
                completionListener.onError("Unable to JSON encode message");
            }
        } else {
            completionListener.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public void sendRESTMessage(String httpMethod, String url, JSONObject data, final RESTListener completionListener) {
        if (connected) {
            JSONArray array = new JSONArray();

            try
            {
                JSONObject message = new JSONObject("{'headers':{'App-Token':'" + appToken + "'},'url':'" + url + "'}");

                if (null != data) {
                    message.put("data", data);
                }

                array.put(message);

                if (array.toString().getBytes("UTF-8").length <= APITransaction.bodySizeLimit) {
                    client.emit(httpMethod, array, new Acknowledge() {
                        @Override
                        public void acknowledge(JSONArray arguments) {
                            // There should only ever be one element in this array. Anything else is ignored for the time being.
                            if ((arguments != null) && (arguments.length() > 0)) {
                                try {
                                    Object responseObject = arguments.get(0);

                                    if (responseObject instanceof String) {
                                        String responseString = (String) responseObject;

                                        if (responseString.equals("null")) {
                                            // Success! There was just no response body
                                            completionListener.onSuccess(null);
                                        } else {
                                            try {
                                                JSONObject jsonResponse = new JSONObject(responseString);
                                                boolean errorMessageFound = false;
                                                // If there was a server error, there will be a key named 'error' or 'status'
                                                try {
                                                    String errorMessage = jsonResponse.getString("error");
                                                    errorMessageFound = true;
                                                    completionListener.onError(errorMessage);
                                                } catch (JSONException e) {
                                                    // If there was no 'error' key, then assume the operation was successful
                                                }

                                                try {
                                                    int statusCode = jsonResponse.getInt("status");
                                                    int[] validCodes = {200, 204, 205, 302, 401, 403, 404, 418};
                                                    if (Arrays.binarySearch(validCodes, statusCode) < 0) {
                                                        errorMessageFound = true;
                                                        completionListener.onError("An unknown error occurred");
                                                    }
                                                } catch (JSONException e) {
                                                    // If there was no 'status' key, then assume the operation was successful
                                                }

                                                if (!errorMessageFound) {
                                                    completionListener.onSuccess(jsonResponse);
                                                }
                                            } catch (JSONException e) {
                                                // It's not a jsonobject. Let the calling function figure it out
                                                completionListener.onSuccess(responseString);
                                            }
                                        }
                                    } else {
                                        completionListener.onSuccess(responseObject);
                                    }
                                } catch (JSONException e) {
                                    completionListener.onError("Unexpected response from server");
                                }
                            } else {
                                completionListener.onError("Unexpected response from server");
                            }
                        }
                    });
                } else {
                    completionListener.onError("Request body is too big");
                }
            } catch (JSONException e) {
                completionListener.onError("Unable to JSON encode message");
            } catch (UnsupportedEncodingException e) {
                completionListener.onError("Unable to encode message");
            }
        } else {
            completionListener.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public void sendSignal(JSONObject message, String toEndpointID, final Respoke.TaskCompletionListener completionListener) {
        JSONObject data = new JSONObject();

        try {
            data.put("to", toEndpointID);
            data.put("signal", message.toString());
            data.put("toType", "web");

            sendRESTMessage("post", "/v1/signaling", data, new RESTListener() {
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
            completionListener.onError("Error encoding signal to json");
        }
    }


    private void routeSignal(JSONObject message) {
        try {
            JSONObject signal = (JSONObject) message.get("body");
            JSONObject header = (JSONObject) message.get("header");
            String from = header.getString("from");
            String fromConnection = header.getString("fromConnection");

            if ((null != signal) && (null != from)) {
                String signalType = null;
                String sessionID = null;
                String target = null;
                String toConnection = null;

                try {
                    signalType = signal.getString("signalType");
                    sessionID = signal.getString("sessionId");
                    target = signal.getString("target");
                    toConnection = signal.getString("connectionId");
                } catch (JSONException e) {
                    // do nothing
                }

                if ((null != sessionID) && (null != signalType) && (null != target)) {
                    Log.d(TAG, "Received signal " + signalType);
                    boolean isDirectConnection = target.equals("directConnection");

                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        RespokeCall call = listener.callWithID(sessionID);

                        if (target.equals("call") || isDirectConnection) {
                            if (null != call) {
                                if (signalType.equals("bye")) {
                                    call.hangupReceived();
                                } else if (signalType.equals("answer")) {
                                    JSONObject sdp = (JSONObject) signal.get("sessionDescription");
                                    call.answerReceived(sdp, fromConnection);
                                } else if (signalType.equals("connected")) {
                                    if (null != toConnection) {
                                        if (toConnection.equals(connectionID)) {
                                            call.connectedReceived();
                                        } else {
                                            Log.d(TAG, "Another device answered, hanging up.");
                                            call.hangupReceived();
                                        }
                                    } else {
                                        Log.d(TAG, "Unable to find out which endpoint won the call, hanging up");
                                        call.hangupReceived();
                                    }
                                } else if (signalType.equals("iceCandidates")) {
                                    JSONArray candidates = (JSONArray) signal.get("iceCandidates");
                                    call.iceCandidatesReceived(candidates);
                                }
                            } else if (signalType.equals("offer")) {
                                JSONObject sdp = (JSONObject) signal.get("sessionDescription");

                                if (null != sdp) {
                                    Date timestamp;

                                    if (!header.isNull("timestamp")) {
                                        timestamp = new Date(header.getLong("timestamp"));
                                    } else {
                                        // Just use the current time if no date is specified in the header data
                                        timestamp = new Date();
                                    }

                                    if (isDirectConnection) {
                                        listener.onIncomingDirectConnection(sdp, sessionID, fromConnection, from, timestamp, RespokeSignalingChannel.this);
                                    } else {
                                        listener.onIncomingCall(sdp, sessionID, fromConnection, from, timestamp, RespokeSignalingChannel.this);
                                    }
                                } else {
                                    Log.d(TAG, "Error: Offer missing sdp");
                                }
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Error: Could not parse signal data");
                }
            } else {
                Log.d(TAG, "Error: signal missing header data");
            }
        } catch (JSONException e) {
            Log.d(TAG, "Unable to parse received signal");
        }
    }


}
