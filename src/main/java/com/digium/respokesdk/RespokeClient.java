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
import java.util.Map;

/**
 * Created by jasonadams on 9/13/14.
 */
public class RespokeClient implements RespokeSignalingChannelDelegate {

    private static final String TAG = "RespokeClient";

    public RespokeClientDelegate delegate;

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


    public RespokeClient() {
        calls = new ArrayList<RespokeCall>();
        groups = new HashMap<String, RespokeGroup>();
        knownEndpoints = new ArrayList<RespokeEndpoint>();
    }


    public void connect(String endpointID, String appID, boolean shouldReconnect, final Object initialPresence, Context context, final RespokeTaskCompletionDelegate completionDelegate) {
        if ((endpointID != null) && (appID != null) && (endpointID.length() > 0) && (appID.length() > 0)) {
            connectionInProgress = true;
            reconnect = shouldReconnect;
            applicationID = appID;
            appContext = context;

            APIGetToken request = new APIGetToken(context) {
                @Override
                public void transactionComplete(boolean transactionSuccess) {
                    super.transactionComplete(transactionSuccess);

                    if (transactionSuccess) {
                        connect(this.token, initialPresence, appContext, new RespokeTaskCompletionDelegate() {
                            @Override
                            public void onSuccess() {
                                // Do nothing, never called
                            }

                            @Override
                            public void onError(String errorMessage) {
                                connectionInProgress = false;
                                completionDelegate.onError(errorMessage);
                            }
                        });
                    } else {
                        connectionInProgress = false;
                        completionDelegate.onError(this.errorMessage);
                    }
                }
            };

            request.appID = appID;
            request.endpointID = endpointID;
            request.go();
        } else {
            completionDelegate.onError("AppID and endpointID must be specified");
        }
    }


    public void connect(String tokenID, final Object initialPresence, Context context, final RespokeTaskCompletionDelegate completionDelegate) {
        if ((tokenID != null) && (tokenID.length() > 0)) {
            connectionInProgress = true;
            appContext = context;

            APIDoOpen request = new APIDoOpen(context) {
                @Override
                public void transactionComplete(boolean transactionSuccess) {
                    super.transactionComplete(transactionSuccess);

                    if (transactionSuccess) {
                        // Remember the presence value to set once connected
                        presence = initialPresence;

                        signalingChannel = new RespokeSignalingChannel(appToken, RespokeClient.this);
                        signalingChannel.authenticate();
                    } else {
                        connectionInProgress = false;
                        completionDelegate.onError(this.errorMessage);
                    }
                }
            };

            request.tokenID = tokenID;
            request.go();
        } else {
            completionDelegate.onError("TokenID must be specified");
        }
    }


    public void disconnect() {
        reconnect = false;
        signalingChannel.disconnect();
    }


    public boolean isConnected() {
        return ((signalingChannel != null) && (signalingChannel.connected));
    }


    public void joinGroup(final String groupName, final RespokeJoinGroupCompletionDelegate completionDelegate) {
        if (isConnected()) {
            if ((groupName != null) && (groupName.length() > 0)) {
                String urlEndpoint = "/v1/channels/" + groupName + "/subscribers/";

                signalingChannel.sendRESTMessage("post", urlEndpoint, null, new RespokeSignalingChannelRESTDelegate() {
                    @Override
                    public void onSuccess(Object response) {
                        RespokeGroup newGroup = new RespokeGroup(groupName, applicationToken, signalingChannel, RespokeClient.this);
                        groups.put(groupName, newGroup);

                        completionDelegate.onSuccess(newGroup);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        completionDelegate.onError(errorMessage);
                    }
                });
            } else {
                completionDelegate.onError("Group name must be specified");
            }
        } else {
            completionDelegate.onError("Can't complete request when not connected. Please reconnect!");
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
                if (eachEndpoint.endpointID.equals(endpointIDToFind)) {
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


    private void performReconnect() {
        if (null != applicationID) {
            reconnectCount++;
            //TODO perform actuallyconnect
        }
    }


    private void actuallyReconnect() {
        // TODO
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
        // Can only reconnect in development mode, not brokered mode
        boolean willReconnect = reconnect && (applicationID != null);

        calls.clear();
        groups.clear();
        knownEndpoints.clear();

        delegate.onDisconnect(this, willReconnect);

        signalingChannel = null;

        if (willReconnect) {
            performReconnect();
        }
    }


    public void onIncomingCall(Map sdp, String sessionID, String connectionID, String endpointID, RespokeSignalingChannel sender) {
        //TODO
    }


    public void onError(String errorMessage, RespokeSignalingChannel sender) {
        delegate.onError(this, errorMessage);

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
        //TODO
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
