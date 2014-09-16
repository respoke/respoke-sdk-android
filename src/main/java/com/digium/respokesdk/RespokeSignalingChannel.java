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


/**
 * Created by jasonadams on 9/13/14.
 */
public class RespokeSignalingChannel {

    private static final String TAG = "RespokeSignalingChannel";
    private static final String RESPOKE_SOCKETIO_PORT = "443";

    public boolean connected;
    public RespokeSignalingChannelDelegate delegate;
    private String appToken;
    private SocketIOClient client;
    private String connectionID;


    public RespokeSignalingChannel(String token, RespokeSignalingChannelDelegate newDelegate) {
        appToken = token;
        delegate = newDelegate;
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
                        if (connected) {
                            connected = false;
                            client = null;

                            delegate.onDisconnect(RespokeSignalingChannel.this);
                        }
                    }
                });

                client.setErrorCallback(new ErrorCallback() {
                    @Override
                    public void onError(String error) {
                        Log.d(TAG, "Socket error: " + error);
                        delegate.onError(error, RespokeSignalingChannel.this);
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

                                delegate.onJoinGroup(groupID, endpoint, connection, RespokeSignalingChannel.this);
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

                                delegate.onLeaveGroup(groupID, endpoint, connection, RespokeSignalingChannel.this);
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

                                delegate.onMessage(message, endpoint, RespokeSignalingChannel.this);
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

                                delegate.onGroupMessage(message, groupID, endpointID, RespokeSignalingChannel.this);
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

                                delegate.onPresence(type, connectionID, endpointID, RespokeSignalingChannel.this);
                            } catch (JSONException e) {
                                Log.d(TAG, "Error parsing received event");
                            }
                        }
                    }
                });

                // Once the socket is connected, perform a post to get the connection and endpoint IDs for this client
                sendRESTMessage("post", "/v1/endpointconnections", null, new RespokeSignalingChannelRESTDelegate() {
                    @Override
                    public void onSuccess(Object response) {
                        if (response instanceof JSONObject) {
                            String endpointID = null;
                            try {
                                JSONObject responseJSON = (JSONObject) response;
                                endpointID = responseJSON.getString("endpointId");
                                connectionID = responseJSON.getString("id");

                                delegate.onConnect(RespokeSignalingChannel.this, endpointID);
                            } catch (JSONException e) {
                                delegate.onError("Unexpected response from server", RespokeSignalingChannel.this);
                            }
                        } else {
                            delegate.onError("Unexpected response from server", RespokeSignalingChannel.this);
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        delegate.onError(errorMessage, RespokeSignalingChannel.this);
                    }
                });
            }
        });
    }


    public void disconnect() {
        client.disconnect();
    }


    public void sendRESTMessage(String httpMethod, String url, Object data, final RespokeSignalingChannelRESTDelegate completionDelegate) {
        if (connected) {
            JSONArray array = new JSONArray();

            try
            {
                JSONObject message = new JSONObject("{'headers':{'App-Token':'" + appToken + "'},'url':'" + url + "'}");
                array.put(message);

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
                                        completionDelegate.onSuccess(null);
                                    } else {
                                        try {
                                            JSONObject jsonResponse = new JSONObject(responseString);

                                            // If there was a server error, there will be a key named 'error'
                                            try {
                                                String errorMessage = jsonResponse.getString("error");
                                                completionDelegate.onError(errorMessage);
                                            } catch (JSONException e) {
                                                // If there was no 'error' key, then the operation was successful
                                                completionDelegate.onSuccess(jsonResponse);
                                            }
                                        } catch (JSONException e) {
                                            // It's not a jsonobject. Let the calling function figure it out
                                            completionDelegate.onSuccess(responseString);
                                        }
                                    }
                                } else {
                                    completionDelegate.onSuccess(responseObject);
                                }
                            } catch (JSONException e) {
                                completionDelegate.onError("Unexpected response from server");
                            }
                        } else {
                            completionDelegate.onError("Unexpected response from server");
                        }
                    }
                });
            } catch (JSONException e) {
                completionDelegate.onError("Unable to JSON encode message");
            }
        }
    }


    private void routeSignal(JSONObject message) {

    }


}
