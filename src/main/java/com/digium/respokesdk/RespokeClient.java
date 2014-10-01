package com.digium.respokesdk;

import android.content.Context;
import android.util.Log;

import com.digium.respokesdk.RestAPI.APIDoOpen;
import com.digium.respokesdk.RestAPI.APIGetToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *  This is a top-level interface to the API. It handles authenticating the app to the
 *  API server, receiving server-side app-specific information, keeping track of connection status and presence,
 *  accepting callbacks and listeners, and interacting with information the library keeps
 *  track of, like groups and endpoints. The client also keeps track of default settings for calls and direct
 *  connections as well as automatically reconnecting to the service when network activity is lost.
 */
public class RespokeClient implements RespokeSignalingChannel.Listener {

    private static final String TAG = "RespokeClient";
    private static final int RECONNECT_INTERVAL = 500;  ///< The exponential step interval between automatic reconnect attempts, in milliseconds

    private WeakReference<Listener> listenerReference;
    private String localEndpointID;  ///< The local endpoint ID
    private String applicationToken;  ///< The application token to use
    private RespokeSignalingChannel signalingChannel;  ///< The signaling channel to use
    private ArrayList<RespokeCall> calls;  ///< An array of the active calls
    private HashMap<String, RespokeGroup> groups;  ///< An array of the groups this client is a member of
    private ArrayList<RespokeEndpoint> knownEndpoints;  ///< An array of the known endpoints
    private Object presence;  ///< The current presence of this client
    private String applicationID;  ///< The application ID to use when connecting in development mode
    private boolean reconnect;  ///< Indicates if the client should automatically reconnect if the web socket disconnects
    private int reconnectCount;  ///< A count of how many times reconnection has been attempted
    private boolean connectionInProgress;  ///< Indicates if the client is in the middle of attempting to connect
    private Context appContext;  ///< The application context


    /**
     * A delegate protocol to notify the receiver of events occurring with the client
     */
    public interface Listener {


        /**
         *  Receive notification Respoke has successfully connected to the cloud.
         *
         *  @param sender The RespokeClient that has connected
         */
        public void onConnect(RespokeClient sender);


        /**
         *  Receive notification Respoke has successfully disconnected from the cloud.
         *
         *  @param sender        The RespokeClient that has disconnected
         *  @param reconnecting  Indicates if the Respoke SDK is attempting to automatically reconnect
         */
        public void onDisconnect(RespokeClient sender, boolean reconnecting);


        /**
         *  Handle an error that resulted from a method call.
         *
         *  @param sender The RespokeClient that is reporting the error
         *  @param errorMessage  The error that has occurred
         */
        public void onError(RespokeClient sender, String errorMessage);


        /**
         *  Receive notification that the client is receiving a call from a remote party.
         *
         *  @param sender The RespokeClient that is receiving the call
         *  @param call   A reference to the incoming RespokeCall object
         */
        public void onCall(RespokeClient sender, RespokeCall call);
    }


    /**
     * A listener interface to receive a notification that the task to join the group has completed
     */
    public interface JoinGroupCompletionDelegate {

        void onSuccess(RespokeGroup group);

        void onError(String errorMessage);

    }


    public RespokeClient() {
        calls = new ArrayList<RespokeCall>();
        groups = new HashMap<String, RespokeGroup>();
        knownEndpoints = new ArrayList<RespokeEndpoint>();
    }


    public void setListener(Listener listener) {
        listenerReference = new WeakReference<Listener>(listener);
    }


    public void connect(String endpointID, String appID, boolean shouldReconnect, final Object initialPresence, Context context, final Respoke.TaskCompletionListener completionListener) {
        if ((endpointID != null) && (appID != null) && (endpointID.length() > 0) && (appID.length() > 0)) {
            connectionInProgress = true;
            reconnect = shouldReconnect;
            applicationID = appID;
            appContext = context;

            APIGetToken request = new APIGetToken(context) {
                @Override
                public void transactionComplete() {
                    super.transactionComplete();

                    if (success) {
                        connect(this.token, initialPresence, appContext, new Respoke.TaskCompletionListener() {
                            @Override
                            public void onSuccess() {
                                // Do nothing, never called
                            }

                            @Override
                            public void onError(String errorMessage) {
                                connectionInProgress = false;
                                completionListener.onError(errorMessage);
                            }
                        });
                    } else {
                        connectionInProgress = false;
                        completionListener.onError(this.errorMessage);
                    }
                }
            };

            request.appID = appID;
            request.endpointID = endpointID;
            request.go();
        } else {
            completionListener.onError("AppID and endpointID must be specified");
        }
    }


    public void connect(String tokenID, final Object initialPresence, Context context, final Respoke.TaskCompletionListener completionListener) {
        if ((tokenID != null) && (tokenID.length() > 0)) {
            connectionInProgress = true;
            appContext = context;

            APIDoOpen request = new APIDoOpen(context) {
                @Override
                public void transactionComplete() {
                    super.transactionComplete();

                    if (success) {
                        // Remember the presence value to set once connected
                        presence = initialPresence;

                        signalingChannel = new RespokeSignalingChannel(appToken, RespokeClient.this);
                        signalingChannel.authenticate();
                    } else {
                        connectionInProgress = false;
                        completionListener.onError(this.errorMessage);
                    }
                }
            };

            request.tokenID = tokenID;
            request.go();
        } else {
            completionListener.onError("TokenID must be specified");
        }
    }


    public void disconnect() {
        reconnect = false;

        if (null != signalingChannel) {
            signalingChannel.disconnect();
        }
    }


    public boolean isConnected() {
        return ((signalingChannel != null) && (signalingChannel.connected));
    }


    public void joinGroup(final String groupName, final JoinGroupCompletionDelegate completionListener) {
        if (isConnected()) {
            if ((groupName != null) && (groupName.length() > 0)) {
                String urlEndpoint = "/v1/channels/" + groupName + "/subscribers/";

                signalingChannel.sendRESTMessage("post", urlEndpoint, null, new RespokeSignalingChannel.RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        RespokeGroup newGroup = new RespokeGroup(groupName, applicationToken, signalingChannel, RespokeClient.this);
                        groups.put(groupName, newGroup);

                        completionListener.onSuccess(newGroup);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        completionListener.onError(errorMessage);
                    }
                });
            } else {
                completionListener.onError("Group name must be specified");
            }
        } else {
            completionListener.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    public RespokeConnection getConnection(String connectionID, String endpointID, boolean skipCreate) {
        RespokeConnection connection = null;

        if (null != connectionID) {
            RespokeEndpoint endpoint = getEndpoint(endpointID, skipCreate);

            if (null != endpoint) {
                for (RespokeConnection eachConnection : endpoint.connections) {
                    if (eachConnection.connectionID.equals(connectionID)) {
                        connection = eachConnection;
                        break;
                    }
                }

                if ((null == connection) && (!skipCreate)) {
                    connection = new RespokeConnection(signalingChannel, connectionID, endpoint);
                    endpoint.connections.add(connection);
                }
            }
        }

        return connection;
    }


    public RespokeEndpoint getEndpoint(String endpointIDToFind, boolean skipCreate) {
        RespokeEndpoint endpoint = null;

        if (null != endpointIDToFind) {
            for (RespokeEndpoint eachEndpoint : knownEndpoints) {
                if (eachEndpoint.getEndpointID().equals(endpointIDToFind)) {
                    endpoint = eachEndpoint;
                    break;
                }
            }

            if ((null == endpoint) && (!skipCreate)) {
                endpoint = new RespokeEndpoint(signalingChannel, endpointIDToFind);
                knownEndpoints.add(endpoint);
            }
        }

        return endpoint;
    }


    public String getEndpointID() {
        return localEndpointID;
    }


    public void setPresence(Object newPresence, final Respoke.TaskCompletionListener completionListener) {
        if (isConnected()) {
            Object presenceToSet = newPresence;

            if (null == presenceToSet) {
                presenceToSet = "available";
            }

            JSONObject typeData = new JSONObject();
            JSONObject data = new JSONObject();

            try {
                typeData.put("type", presenceToSet);
                data.put("presence", typeData);

                final Object finalPresence = presenceToSet;

                signalingChannel.sendRESTMessage("post", "/v1/presence", data, new RespokeSignalingChannel.RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        presence = finalPresence;
                    }

                    @Override
                    public void onError(String errorMessage) {
                        completionListener.onError(errorMessage);
                    }
                });
            } catch (JSONException e) {
                completionListener.onError("Error encoding presence to json");
            }
        } else {
            completionListener.onError("Can't complete request when not connected. Please reconnect!");
        }
    }


    private void performReconnect() {
        if (null != applicationID) {
            reconnectCount++;

            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            actuallyReconnect();
                        }
                    },
                    RECONNECT_INTERVAL * (reconnectCount - 1)
            );
        }
    }


    private void actuallyReconnect() {
        if (((null == signalingChannel) || !signalingChannel.connected) && reconnect) {
            if (connectionInProgress) {
                // The client app must have initiated a connection manually during the timeout period. Try again later
                performReconnect();
            } else {
                Log.d(TAG, "Trying to reconnect...");
                connect(localEndpointID, applicationID, reconnect, presence, appContext, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        // Do nothing. The onConnect delegate method will be called if successful
                    }

                    @Override
                    public void onError(String errorMessage) {
                        // A REST API call failed. Socket errors are handled in the onError callback
                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            listener.onError(RespokeClient.this, errorMessage);
                        }

                        // Try again later
                        performReconnect();
                    }
                });
            }
        }
    }


    // RespokeSignalingChannelListener methods


    public void onConnect(RespokeSignalingChannel sender, String endpointID) {
        connectionInProgress = false;
        reconnectCount = 0;
        localEndpointID = endpointID;

        // Try to set the presence to the initial or last set state
        setPresence(presence, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                // do nothing
            }

            @Override
            public void onError(String errorMessage) {
                // do nothing
            }
        });

        Listener listener = listenerReference.get();
        if (null != listener) {
            listener.onConnect(this);
        }
    }


    public void onDisconnect(RespokeSignalingChannel sender) {
        // Can only reconnect in development mode, not brokered mode
        boolean willReconnect = reconnect && (applicationID != null);

        calls.clear();
        groups.clear();
        knownEndpoints.clear();

        Listener listener = listenerReference.get();
        if (null != listener) {
            listener.onDisconnect(this, willReconnect);
        }

        signalingChannel = null;

        if (willReconnect) {
            performReconnect();
        }
    }


    public void onIncomingCall(JSONObject sdp, String sessionID, String connectionID, String endpointID, RespokeSignalingChannel sender) {
        RespokeEndpoint endpoint = getEndpoint(endpointID, false);

        if (null != endpoint) {
            RespokeCall call = new RespokeCall(signalingChannel, sdp, sessionID, connectionID, endpoint);
            Listener listener = listenerReference.get();
            if (null != listener) {
                listener.onCall(this, call);
            }
        } else {
            Log.d(TAG, "Error: Could not create Endpoint for incoming call");
        }
    }


    public void onError(String errorMessage, RespokeSignalingChannel sender) {
        Listener listener = listenerReference.get();
        if (null != listener) {
            listener.onError(this, errorMessage);
        }

        if ((null != signalingChannel) && (!signalingChannel.connected)) {
            connectionInProgress = false;

            if (reconnect) {
                performReconnect();
            }
        }
    }


    public void onJoinGroup(String groupID, String endpointID, String connectionID, RespokeSignalingChannel sender) {
        // only pass on notifications about people other than ourselves
        if ((null != endpointID) && (!endpointID.equals(localEndpointID))) {
            RespokeGroup group = groups.get(groupID);

            if (null!= group) {
                // Get the existing instance for this connection, or create a new one if necessary
                RespokeConnection connection = getConnection(connectionID, endpointID, false);

                if (null != connection) {
                    group.connectionDidJoin(connection);
                }
            }
        }
    }


    public void onLeaveGroup(String groupID, String endpointID, String connectionID, RespokeSignalingChannel sender) {
        // only pass on notifications about people other than ourselves
        if ((null != endpointID) && (!endpointID.equals(localEndpointID))) {
            RespokeGroup group = groups.get(groupID);

            if (null!= group) {
                // Get the existing instance for this connection. If we are not already aware of it, ignore it
                RespokeConnection connection = getConnection(connectionID, endpointID, true);

                if (null != connection) {
                    group.connectionDidLeave(connection);
                }
            }
        }
    }


    public void onMessage(String message, String endpointID, RespokeSignalingChannel sender) {
        RespokeEndpoint endpoint = getEndpoint(endpointID, true);

        if (null != endpoint) {
            endpoint.didReceiveMessage(message);
        }
    }


    public void onGroupMessage(String message, String groupID, String endpointID, RespokeSignalingChannel sender) {
        RespokeGroup group = groups.get(groupID);

        if (null != group) {
            RespokeEndpoint endpoint = getEndpoint(endpointID, false);
            group.didReceiveMessage(message, endpoint);
        }
    }


    public void onPresence(Object presence, String connectionID, String endpointID, RespokeSignalingChannel sender) {
        RespokeConnection connection = getConnection(connectionID, endpointID, false);

        if (null != connection) {
            connection.presence = presence;

            RespokeEndpoint endpoint = connection.getEndpoint();
            endpoint.resolvePresence();
        }
    }


    public void callCreated(RespokeCall call) {
        calls.add(call);
    }


    public void callTerminated(RespokeCall call) {
        calls.remove(call);
    }


    public RespokeCall callWithID(String sessionID) {
        RespokeCall call = null;

        for (RespokeCall eachCall : calls) {
            if (eachCall.getSessionID().equals(sessionID)) {
                call = eachCall;
                break;
            }
        }

        return call;
    }
}
