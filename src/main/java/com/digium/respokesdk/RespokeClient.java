package com.digium.respokesdk;

import android.content.Context;
import android.util.Log;

import com.digium.respokesdk.RestAPI.APIDoOpen;
import com.digium.respokesdk.RestAPI.APIGetToken;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jasonadams on 9/13/14.
 */
public class RespokeClient {

    private static final String TAG = "RespokeClient";

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

                    Log.d(TAG, "APIGetToken finished.");
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
                }
            };

            request.tokenID = tokenID;
            request.go();
        } else {

        }
    }


}
