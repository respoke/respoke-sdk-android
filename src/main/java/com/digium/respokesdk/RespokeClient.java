package com.digium.respokesdk;

import android.content.Context;
import android.util.Log;

import com.digium.respokesdk.RestAPI.APIDoOpen;
import com.digium.respokesdk.RestAPI.APIGetToken;
import com.koushikdutta.async.http.socketio.Acknowledge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jasonadams on 9/13/14.
 */
public class RespokeClient implements RespokeSignalingChannelDelegate {

    private static final String TAG = "RespokeClient";

    public RespokeClientDelegate delegate;

    private String localEndpointID;  ///< The local endpoint ID
    private String applicationToken;  ///< The application token to use
    private RespokeSignalingChannel signalingChannel;  ///< The signaling channel to use
    private ArrayList calls;  ///< An array of the active calls
    private HashMap<String, RespokeGroup> groups;  ///< An array of the groups this client is a member of
    private ArrayList knownEndpoints;  ///< An array of the known endpoints
    private Object presence;  ///< The current presence of this client
    private String applicationID;  ///< The application ID to use when connecting in development mode
    private boolean reconnect;  ///< Indicates if the client should automatically reconnect if the web socket disconnects
    private int reconnectCount;  ///< A count of how many times reconnection has been attempted
    private boolean connectionInProgress;  ///< Indicates if the client is in the middle of attempting to connect
    private Context appContext;  ///< The application context


    public RespokeClient() {
        calls = new ArrayList();
        groups = new HashMap<String, RespokeGroup>();
        knownEndpoints = new ArrayList();
    }


    public void connect(String endpointID, String appID, boolean shouldReconnect, final Object initialPresence, Context context) {
        if ((endpointID != null) && (appID != null) && (endpointID.length() > 0) && (appID.length() > 0)) {
            connectionInProgress = true;
            reconnect = shouldReconnect;
            applicationID = appID;
            appContext = context;

            APIGetToken request = new APIGetToken(context) {
                @Override
                public void transactionComplete(boolean transactionSuccess) {
                    super.transactionComplete(transactionSuccess);

                    connect(this.token, initialPresence, appContext);
                }
            };

            request.appID = appID;
            request.endpointID = endpointID;
            request.go();
        } else {

        }
    }


    public void connect(String tokenID, final Object initialPresence, Context context) {
        if ((tokenID != null) && (tokenID.length() > 0)) {
            connectionInProgress = true;
            appContext = context;

            APIDoOpen request = new APIDoOpen(context) {
                @Override
                public void transactionComplete(boolean transactionSuccess) {
                    super.transactionComplete(transactionSuccess);

                    Log.d(TAG, "APIDoOpen finished.");
                    presence = initialPresence;

                    signalingChannel = new RespokeSignalingChannel(appToken, RespokeClient.this);
                    signalingChannel.authenticate();
                }
            };

            request.tokenID = tokenID;
            request.go();
        } else {

        }
    }


    public void disconnect() {
        reconnect = false;
        signalingChannel.disconnect();
    }


    public boolean isConnected() {
        return ((signalingChannel != null) && (signalingChannel.connected));
    }


    public void joinGroup(final String groupName) {
        if (isConnected()) {
            if ((groupName != null) && (groupName.length() > 0)) {
                String urlEndpoint = "/v1/channels/" + groupName + "/subscribers/";

                signalingChannel.sendRESTMessage("post", urlEndpoint, null, new Acknowledge() {
                    @Override
                    public void acknowledge(JSONArray arguments) {
                        if ((arguments != null) && (arguments.length() == 1)) {
                            try {
                                String responseString = (String) arguments.get(0);

                                if (responseString.equals("null"))
                                {
                                    // Success!
                                    RespokeGroup newGroup = new RespokeGroup(groupName, applicationToken, signalingChannel, RespokeClient.this);
                                    groups.put(groupName, newGroup);
                                } else {
                                    // There was probably an error, get the message
                                    JSONObject response = new JSONObject(responseString);
                                    String errorMessage = response.getString("error");
                                    Log.d(TAG, "Error joining group: " + errorMessage);
                                }
                            } catch (JSONException e) {
                                Log.d(TAG, "Unexpected response from server");
                            }
                        } else {
                            Log.d(TAG, "Unexpected response from server");
                        }
                    }
                });
            } else {

            }
        }
    }


    // RespokeSignalingChannelDelegate methods


    public void onConnect(RespokeSignalingChannel sender, String endpointID) {
        connectionInProgress = true;
        reconnectCount = 0;
        localEndpointID = endpointID;

        //TODO set presence

        delegate.onConnect(this);
    }


    public void onDisconnect(RespokeSignalingChannel sender) {

    }


    public void onJoinGroup(String groupID, String endpointID, String connectionID, RespokeSignalingChannel sender) {

    }


    public void onLeaveGroup(String groupID, String endpointID, String connectionID, RespokeSignalingChannel sender) {

    }
}
