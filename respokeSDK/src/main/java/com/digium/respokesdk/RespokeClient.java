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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.digium.respokesdk.RestAPI.APIDoOpen;
import com.digium.respokesdk.RestAPI.APIGetToken;
import com.digium.respokesdk.RestAPI.APITransaction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    public static final String PROPERTY_LAST_VALID_PUSH_TOKEN = "pushToken";
    public static final String PROPERTY_LAST_VALID_PUSH_TOKEN_ID = "pushTokenServiceID";

    private WeakReference<Listener> listenerReference;
    private WeakReference<ResolvePresenceListener> resolveListenerReference;
    private String localEndpointID;  ///< The local endpoint ID
    private String localConnectionID; ///< The local connection ID
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
    private String pushServiceID; ///< The push service ID
    private ArrayList<String> presenceRegistrationQueue; ///< An array of endpoints that need to be registered for presence updates
    private HashMap<String, Boolean> presenceRegistered; ///< A Hash of all the endpoint IDs that have already been registered for presence updates
    private boolean registrationTaskWaiting; ///< A flag to indicate that a task is scheduled to begin presence registration

    public String baseURL = APITransaction.RESPOKE_BASE_URL;  ///< The base url of the Respoke service to use


    /**
     * A listener interface to notify the receiver of events occurring with the client
     */
    public interface Listener {


        /**
         *  Receive a notification Respoke has successfully connected to the cloud.
         *
         *  @param sender The RespokeClient that has connected
         */
        void onConnect(RespokeClient sender);


        /**
         *  Receive a notification Respoke has successfully disconnected from the cloud.
         *
         *  @param sender        The RespokeClient that has disconnected
         *  @param reconnecting  Indicates if the Respoke SDK is attempting to automatically reconnect
         */
        void onDisconnect(RespokeClient sender, boolean reconnecting);


        /**
         *  Handle an error that resulted from a method call.
         *
         *  @param sender The RespokeClient that is reporting the error
         *  @param errorMessage  The error that has occurred
         */
        void onError(RespokeClient sender, String errorMessage);


        /**
         *  Receive a notification that the client is receiving a call from a remote party.
         *
         *  @param sender The RespokeClient that is receiving the call
         *  @param call   A reference to the incoming RespokeCall object
         */
        void onCall(RespokeClient sender, RespokeCall call);


        /**
         *  This event is fired when the logged-in endpoint is receiving a request to open a direct connection
         *  to another endpoint.  If the user wishes to allow the direct connection, calling 'accept' on the
         *  direct connection will allow the connection to be set up.
         *
         *  @param directConnection  The incoming direct connection object
         *  @param endpoint          The remote endpoint initiating the direct connection
         */
        void onIncomingDirectConnection(RespokeDirectConnection directConnection, RespokeEndpoint endpoint);


        /**
         *  Receive a notification that a message addressed to this group has been received
         *
         *  @param message    The message
         *  @param endpoint   The remote endpoint the message is related to
         *  @param group      If this was a group message, the group to which this group message was posted.
         *  @param timestamp  The timestamp of the message
         *  @param didSend    True if the specified endpoint sent the message, False if it received the message. Null for group messages.
         */
        void onMessage(String message, RespokeEndpoint endpoint, RespokeGroup group, Date timestamp, Boolean didSend);
    }


    /**
     * A listener interface to receive a notification that the task to join the groups has completed
     */
    public interface JoinGroupCompletionListener {

        /**
         *  Received notification that the groups were successfully joined
         *
         *  @param groupList  An array of RespokeGroup instances representing the groups that were successfully joined
         */
        void onSuccess(ArrayList<RespokeGroup> groupList);

        /**
         *  Receive a notification that the asynchronous operation failed
         *
         *  @param errorMessage  A human-readable description of the error that was encountered
         */
        void onError(String errorMessage);

    }


    /**
     * A listener interface to receive a notification that the connect action has failed
     */
    public interface ConnectCompletionListener {

        /**
         *  Receive a notification that the asynchronous operation failed
         *
         *  @param errorMessage  A human-readable description of the error that was encountered
         */
        void onError(String errorMessage);

    }


    /**
     *  A listener interface to ask the receiver to resolve a list of presence values for an endpoint
     */
    public interface ResolvePresenceListener {

        /**
         *  Resolve the presence among multiple connections belonging to this endpoint. Note that this callback will NOT be called in the UI thread.
         *
         *  @param presenceArray An array of presence values
         *
         *  @return The resolved presence value to use
         */
        Object resolvePresence(ArrayList<Object> presenceArray);

    }


    /**
     * A listener interface to receive a notification when the request to retrieve the history
     * of messages for a list of groups has completed
     */
    public interface GroupHistoriesCompletionListener {

        void onSuccess(Map<String, List<RespokeGroupMessage>> groupsToMessages);

        void onError(String errorMessage);
    }

    /**
     * A listener interface to receive a notification when the request to retrieve the
     * history of messages for a specific group has completed
     */
    public interface GroupHistoryCompletionListener {

        void onSuccess(List<RespokeGroupMessage> messageList);

        void onError(String errorMessage);
    }


    /**
     *  The constructor for this class
     */
    public RespokeClient() {
        calls = new ArrayList<RespokeCall>();
        groups = new HashMap<String, RespokeGroup>();
        knownEndpoints = new ArrayList<RespokeEndpoint>();
        presenceRegistrationQueue = new ArrayList<String>();
        presenceRegistered = new HashMap<String, Boolean>();
    }


    /**
     *  Set a receiver for the Listener interface
     *
     *  @param listener  The new receiver for events from the Listener interface for this client instance
     */
    public void setListener(Listener listener) {
        listenerReference = new WeakReference<Listener>(listener);
    }


    /**
     *  Set a receiver for the ResolvePresenceListener interface
     *
     *  @param listener  The new receiver for events from the ResolvePresenceListener interface for this client instance
     */
    public void setResolvePresenceListener(ResolvePresenceListener listener) {
        resolveListenerReference = new WeakReference<ResolvePresenceListener>(listener);
    }


    /**
     *  Get the current receiver for the ResolvePresenceListener interface
     *
     *  @return The current receiver for the ResolvePresenceListener interface
     */
    public ResolvePresenceListener getResolvePresenceListener() {
        if (null != resolveListenerReference) {
            return resolveListenerReference.get();
        } else {
            return null;
        }
    }


    /**
     *  Connect to the Respoke infrastructure and authenticate in development mode using the specified endpoint ID and app ID.
     *  Attempt to obtain an authentication token automatically from the Respoke infrastructure.
     *
     *  @param endpointID          The endpoint ID to use when connecting
     *  @param appID               Your Application ID
     *  @param shouldReconnect     Whether or not to automatically reconnect to the Respoke service when a disconnect occurs.
     *  @param initialPresence     The optional initial presence value to set for this client
     *  @param context             An application context with which to access system resources
     *  @param completionListener  A listener to be called when an error occurs, passing a string describing the error
     */
    public void connect(String endpointID, String appID, boolean shouldReconnect, final Object initialPresence, Context context, final ConnectCompletionListener completionListener) {
        if ((endpointID != null) && (appID != null) && (endpointID.length() > 0) && (appID.length() > 0)) {
            connectionInProgress = true;
            reconnect = shouldReconnect;
            applicationID = appID;
            appContext = context;

            APIGetToken request = new APIGetToken(context, baseURL) {
                @Override
                public void transactionComplete() {
                    super.transactionComplete();

                    if (success) {
                        connect(this.token, initialPresence, appContext, new ConnectCompletionListener() {
                            @Override
                            public void onError(final String errorMessage) {
                                connectionInProgress = false;

                                postConnectError(completionListener, errorMessage);
                            }
                        });
                    } else {
                        connectionInProgress = false;

                        postConnectError(completionListener, this.errorMessage);
                    }
                }
            };

            request.appID = appID;
            request.endpointID = endpointID;
            request.go();
        } else {
            postConnectError(completionListener, "AppID and endpointID must be specified");
        }
    }


    /**
     *  Connect to the Respoke infrastructure and authenticate with the specified brokered auth token ID. 
     *
     *  @param tokenID             The token ID to use when connecting
     *  @param initialPresence     The optional initial presence value to set for this client
     *  @param context             An application context with which to access system resources
     *  @param completionListener  A listener to be called when an error occurs, passing a string describing the error
     */
    public void connect(String tokenID, final Object initialPresence, Context context, final ConnectCompletionListener completionListener) {
        if ((tokenID != null) && (tokenID.length() > 0)) {
            connectionInProgress = true;
            appContext = context;

            APIDoOpen request = new APIDoOpen(context, baseURL) {
                @Override
                public void transactionComplete() {
                    super.transactionComplete();

                    if (success) {
                        // Remember the presence value to set once connected
                        presence = initialPresence;

                        signalingChannel = new RespokeSignalingChannel(appToken, RespokeClient.this, baseURL, appContext);
                        signalingChannel.authenticate();
                    } else {
                        connectionInProgress = false;

                        postConnectError(completionListener, this.errorMessage);
                    }
                }
            };

            request.tokenID = tokenID;
            request.go();
        } else {
            postConnectError(completionListener, "TokenID must be specified");
        }
    }


    /**
     *  Disconnect from the Respoke infrastructure, leave all groups, invalidate the token, and disconnect the websocket.
     */
    public void disconnect() {
        reconnect = false;

        if (null != signalingChannel) {
            signalingChannel.disconnect();
        }
    }


    /**
     *  Check whether this client is connected to the backend infrastructure.
     *
     *  @return True if connected
     */
    public boolean isConnected() {
        return ((signalingChannel != null) && (signalingChannel.connected));
    }


    /**
     *  Join a list of Groups and begin keeping track of them.
     *
     *  @param groupIDList         An array of IDs of the groups to join
     *  @param completionListener  A listener to receive a notification of the success or failure of the asynchronous operation
     */
    public void joinGroups(final ArrayList<String> groupIDList, final JoinGroupCompletionListener completionListener) {
        if (isConnected()) {
            if ((groupIDList != null) && (groupIDList.size() > 0)) {
                String urlEndpoint = "/v1/groups";

                JSONArray groupList = new JSONArray(groupIDList);
                JSONObject data = new JSONObject();
                try {
                    data.put("groups", groupList);

                    signalingChannel.sendRESTMessage("post", urlEndpoint, data, new RespokeSignalingChannel.RESTListener() {
                        @Override
                        public void onSuccess(Object response) {
                            final ArrayList<RespokeGroup> newGroupList = new ArrayList<RespokeGroup>();
                            for (String eachGroupID : groupIDList) {
                                RespokeGroup newGroup = new RespokeGroup(eachGroupID, signalingChannel, RespokeClient.this);
                                groups.put(eachGroupID, newGroup);
                                newGroupList.add(newGroup);
                            }

                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    if (null != completionListener) {
                                        completionListener.onSuccess(newGroupList);
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(final String errorMessage) {
                            postJoinGroupMembersError(completionListener, errorMessage);
                        }
                    });
                } catch (JSONException e) {
                    postJoinGroupMembersError(completionListener, "Error encoding group list to json");
                }
            } else {
                postJoinGroupMembersError(completionListener, "At least one group must be specified");
            }
        } else {
            postJoinGroupMembersError(completionListener, "Can't complete request when not connected. Please reconnect!");
        }
    }


    /**
     *  Find a Connection by id and return it. In most cases, if we don't find it we will create it. This is useful
     *  in the case of dynamic endpoints where groups are not in use. Set skipCreate=true to return null
     *  if the Connection is not already known.
     *
     *  @param connectionID The ID of the connection to return
     *  @param endpointID   The ID of the endpoint to which this connection belongs
     *  @param skipCreate   If true, return null if the connection is not already known
     *
     *  @return The connection whose ID was specified
     */
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
                    connection = new RespokeConnection(connectionID, endpoint);
                    endpoint.connections.add(connection);
                }
            }
        }

        return connection;
    }


    /**
     *  Initiate a call to a conference.
     *
     *  @param callListener  A listener to receive notifications about the new call
     *  @param context       An application context with which to access system resources
     *  @param conferenceID  The ID of the conference to call
     *
     *  @return A reference to the new RespokeCall object representing this call
     */
    public RespokeCall joinConference(RespokeCall.Listener callListener, Context context, String conferenceID) {
        RespokeCall call = null;

        if ((null != signalingChannel) && (signalingChannel.connected)) {
            call = new RespokeCall(signalingChannel, conferenceID, "conference");
            call.setListener(callListener);

            call.startCall(context, null, true);
        }

        return call;
    }


    /**
     *  Find an endpoint by id and return it. In most cases, if we don't find it we will create it. This is useful
     *  in the case of dynamic endpoints where groups are not in use. Set skipCreate=true to return null
     *  if the Endpoint is not already known.
     *
     *  @param endpointIDToFind The ID of the endpoint to return
     *  @param skipCreate       If true, return null if the connection is not already known
     *
     *  @return The endpoint whose ID was specified
     */
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
                endpoint = new RespokeEndpoint(signalingChannel, endpointIDToFind, this);
                knownEndpoints.add(endpoint);
            }

            if (null != endpoint) {
                queuePresenceRegistration(endpoint.getEndpointID());
            }
        }

        return endpoint;
    }


    /**
     *  Returns the group with the specified ID
     *
     *  @param groupIDToFind  The ID of the group to find
     *
     *  @return The group with specified ID, or null if it was not found
     */
    public RespokeGroup getGroup(String groupIDToFind) {
        RespokeGroup group = null;

        if (null != groupIDToFind) {
            group = groups.get(groupIDToFind);
        }

        return group;
    }


    /**
     * Retrieve the history of messages that have been persisted for 1 or more groups. Only those
     * messages that have been marked to be persisted when sent will show up in the history. Only
     * the most recent message in each group will be retrieved - to pull more messages, use the
     * other method signature that allows `maxMessages` to be specified.
     *
     * @param groupIds The groups to pull history for
     * @param completionListener The callback called when this async operation has completed
     */
    public void getGroupHistories(final List<String> groupIds,
                                  final GroupHistoriesCompletionListener completionListener) {
        getGroupHistories(groupIds, 1, completionListener);
    }

    /**
     * Retrieve the history of messages that have been persisted for 1 or more groups. Only those
     * messages that have been marked to be persisted when sent will show up in the history.
     *
     * @param groupIds The groups to pull history for
     * @param maxMessages The maximum number of messages per group to pull. Must be &gt;= 1
     * @param completionListener The callback called when this async operation has completed
     */
    public void getGroupHistories(final List<String> groupIds, final Integer maxMessages,
                                  final GroupHistoriesCompletionListener completionListener) {
        if (!isConnected()) {
            getGroupHistoriesError(completionListener, "Can't complete request when not connected, " +
                    "Please reconnect!");
            return;
        }

        if ((maxMessages == null) || (maxMessages < 1)) {
            getGroupHistoriesError(completionListener, "maxMessages must be at least 1");
            return;
        }

        if ((groupIds == null) || (groupIds.size() == 0)) {
            getGroupHistoriesError(completionListener, "At least 1 group must be specified");
            return;
        }

        Uri.Builder builder = new Uri.Builder();
        builder.appendQueryParameter("limit", maxMessages.toString());
        for (String id: groupIds) {
            builder.appendQueryParameter("groupIds", id);
        }

        String urlEndpoint = "/v1/group-histories" + builder.build().toString();
        signalingChannel.sendRESTMessage("get", urlEndpoint, null,
                new RespokeSignalingChannel.RESTListener() {
            @Override
            public void onSuccess(Object response) {
                if (!(response instanceof JSONObject)) {
                    getGroupHistoriesError(completionListener, "Invalid response from server");
                    return;
                }

                final JSONObject json = (JSONObject) response;
                final HashMap<String, List<RespokeGroupMessage>> results = new HashMap<>();

                for (Iterator<String> keys = json.keys(); keys.hasNext();) {
                    final String key = keys.next();

                    try {
                        final JSONArray jsonMessages = json.getJSONArray(key);

                        final ArrayList<RespokeGroupMessage> messageList =
                                new ArrayList<>(jsonMessages.length());

                        for (int i = 0; i < jsonMessages.length(); i++) {
                            final JSONObject jsonMessage = jsonMessages.getJSONObject(i);
                            final RespokeGroupMessage message = buildGroupMessage(jsonMessage);
                            messageList.add(message);
                        }

                        results.put(key, messageList);
                    } catch (JSONException e) {
                        getGroupHistoriesError(completionListener, "Error parsing JSON response");
                        return;
                    }
                }

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (completionListener != null) {
                            completionListener.onSuccess(results);
                        }
                    }
                });
            }

            @Override
            public void onError(final String errorMessage) {
                getGroupHistoriesError(completionListener, errorMessage);
            }
        });
    }

    /**
     * Retrieve the history of messages that have been persisted for a specific group. Only those
     * messages that have been marked to be persisted when sent will show up in the history. Only
     * the 50 most recent messages in each group will be retrieved - to change the maximum number of
     * messages pulled, use the other method signature that allows `maxMessages` to be specified. To
     * retrieve messages further back in the history than right now, use the other method signature
     * that allows `before` to be specified.
     *
     * @param groupId The groups to pull history for
     * @param completionListener The callback called when this async operation has completed
     */
    public void getGroupHistory(final String groupId,
                                final GroupHistoryCompletionListener completionListener) {
        getGroupHistory(groupId, 50, null, completionListener);
    }

    /**
     * Retrieve the history of messages that have been persisted for a specific group. Only those
     * messages that have been marked to be persisted when sent will show up in the history. To
     * retrieve messages further back in the history than right now, use the other method signature
     * that allows `before` to be specified.
     *
     * @param groupId The groups to pull history for
     * @param maxMessages The maximum number of messages per group to pull. Must be &gt;= 1
     * @param completionListener The callback called when this async operation has completed
     */
    public void getGroupHistory(final String groupId, final Integer maxMessages,
                                final GroupHistoryCompletionListener completionListener) {
        getGroupHistory(groupId, maxMessages, null, completionListener);
    }

    /**
     * Retrieve the history of messages that have been persisted for a specific group. Only those
     * messages that have been marked to be persisted when sent will show up in the history.
     *
     * @param groupId The groups to pull history for
     * @param maxMessages The maximum number of messages per group to pull. Must be &gt;= 1
     * @param before Limit messages to those with a timestamp before this value
     * @param completionListener The callback called when this async operation has completed
     */
    public void getGroupHistory(final String groupId, final Integer maxMessages, final Date before,
                                final GroupHistoryCompletionListener completionListener) {
        if (!isConnected()) {
            getGroupHistoryError(completionListener, "Can't complete request when not connected, " +
                    "Please reconnect!");
            return;
        }

        if ((maxMessages == null) || (maxMessages < 1)) {
            getGroupHistoryError(completionListener, "maxMessages must be at least 1");
            return;
        }

        if ((groupId == null) || groupId.length() == 0) {
            getGroupHistoryError(completionListener, "groupId cannot be blank");
            return;
        }

        Uri.Builder builder = new Uri.Builder();
        builder.appendQueryParameter("limit", maxMessages.toString());

        if (before != null) {
            builder.appendQueryParameter("before", Long.toString(before.getTime()));
        }

        String urlEndpoint = String.format("/v1/groups/%s/history%s", groupId, builder.build().toString());
        signalingChannel.sendRESTMessage("get", urlEndpoint, null,
                new RespokeSignalingChannel.RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        if (!(response instanceof JSONArray)) {
                            getGroupHistoryError(completionListener, "Invalid response from server");
                            return;
                        }

                        final JSONArray json = (JSONArray) response;
                        final ArrayList<RespokeGroupMessage> results = new ArrayList<>(json.length());

                        try {
                            for (int i = 0; i < json.length(); i++) {
                                final JSONObject jsonMessage = json.getJSONObject(i);
                                final RespokeGroupMessage message = buildGroupMessage(jsonMessage);
                                results.add(message);
                            }
                        } catch (JSONException e) {
                            getGroupHistoryError(completionListener, "Error parsing JSON response");
                            return;
                        }

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (completionListener != null) {
                                    completionListener.onSuccess(results);
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(final String errorMessage) {
                        getGroupHistoryError(completionListener, errorMessage);
                    }
                });
    }

    /**
     *  Return the Endpoint ID of this client
     *
     *  @return The Endpoint ID of this client
     */
    public String getEndpointID() {
        return localEndpointID;
    }


    /**
     *  Set the presence on the client session
     *
     *  @param newPresence         The new presence to use
     *  @param completionListener  A listener to receive the notification on the success or failure of the asynchronous operation
     */
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

                       Respoke.postTaskSuccess(completionListener);
                    }

                    @Override
                    public void onError(final String errorMessage) {
                        Respoke.postTaskError(completionListener, errorMessage);
                    }
                });
            } catch (JSONException e) {
                Respoke.postTaskError(completionListener, "Error encoding presence to json");
            }
        } else {
            Respoke.postTaskError(completionListener, "Can't complete request when not connected. Please reconnect!");
        }
    }


    /**
     *  Get the current presence of this client
     *
     *  @return The current presence value
     */
    public Object getPresence() {
        return presence;
    }


    /**
     *  Register the client to receive push notifications when the socket is not active
     *
     *  @param token  The GCMS token to register
     */
    public void registerPushServicesWithToken(final String token) {
        String httpURI;
        String httpMethod;

        JSONObject data = new JSONObject();
        try {
            data.put("token", token);
            data.put("service", "google");

            SharedPreferences prefs = appContext.getSharedPreferences(appContext.getPackageName(), Context.MODE_PRIVATE);

            if (null != prefs) {
                String lastKnownPushToken = prefs.getString(PROPERTY_LAST_VALID_PUSH_TOKEN, "notAvailable");
                String lastKnownPushTokenID = prefs.getString(PROPERTY_LAST_VALID_PUSH_TOKEN_ID, "notAvailable");

                if ((null == lastKnownPushTokenID) || (lastKnownPushTokenID.equals("notAvailable"))) {
                    httpURI = String.format("/v1/connections/%s/push-token", localConnectionID);
                    httpMethod = "post";
                    createOrUpdatePushServiceToken(token, httpURI, httpMethod, data, prefs);
                } else if (!lastKnownPushToken.equals("notAvailable") && !lastKnownPushToken.equals(token)) {
                    httpURI = String.format("/v1/connections/%s/push-token/%s", localConnectionID, lastKnownPushTokenID);
                    httpMethod = "put";
                    createOrUpdatePushServiceToken(token, httpURI, httpMethod, data, prefs);
                }
            }
        } catch(JSONException e) {
            Log.d("", "Invalid JSON format for token");
        }
    }


    /**
     *  Unregister this client from the push service so that no more notifications will be received for this endpoint ID
     *
     *  @param completionListener  A listener to receive the notification on the success or failure of the asynchronous operation
     */
    public void unregisterFromPushServices(final Respoke.TaskCompletionListener completionListener) {
        if (isConnected()) {
            SharedPreferences prefs = appContext.getSharedPreferences(appContext.getPackageName(), Context.MODE_PRIVATE);

            if (null != prefs) {
                String lastKnownPushTokenID = prefs.getString(PROPERTY_LAST_VALID_PUSH_TOKEN_ID, "notAvailable");

                if ((null != lastKnownPushTokenID) && !lastKnownPushTokenID.equals("notAvailable")) {
                    // A push token has previously been registered successfully
                    String httpURI = String.format("/v1/connections/%s/push-token/%s", localConnectionID, lastKnownPushTokenID);
                    signalingChannel.sendRESTMessage("delete", httpURI, null, new RespokeSignalingChannel.RESTListener() {
                        @Override
                        public void onSuccess(Object response) {
                            // Remove the push token ID from shared memory so that push may be registered again in the future
                            SharedPreferences prefs = appContext.getSharedPreferences(appContext.getPackageName(), Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.remove(PROPERTY_LAST_VALID_PUSH_TOKEN_ID);
                            editor.apply();

                            Respoke.postTaskSuccess(completionListener);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Respoke.postTaskError(completionListener, "Error unregistering push service token: " + errorMessage);
                        }
                    });
                } else {
                    Respoke.postTaskSuccess(completionListener);
                }
            } else {
                Respoke.postTaskError(completionListener, "Unable to access shared preferences to look for push token");
            }
        } else {
            Respoke.postTaskError(completionListener, "Can't complete request when not connected. Please reconnect!");
        }
    }


    //** Private methods


    private void createOrUpdatePushServiceToken(final String token, String httpURI, String httpMethod, JSONObject data, final SharedPreferences prefs) {
        signalingChannel.sendRESTMessage(httpMethod, httpURI, data, new RespokeSignalingChannel.RESTListener() {
            @Override
            public void onSuccess(Object response) {
                if (response instanceof JSONObject) {
                    try {
                        JSONObject responseJSON = (JSONObject) response;
                        pushServiceID = responseJSON.getString("id");

                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(PROPERTY_LAST_VALID_PUSH_TOKEN, token);
                        editor.putString(PROPERTY_LAST_VALID_PUSH_TOKEN_ID, pushServiceID);
                        editor.apply();
                    } catch (JSONException e) {
                        Log.d(TAG, "Unexpected response from server while registering push service token");
                    }
                } else {
                    Log.d(TAG, "Unexpected response from server while registering push service token");
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.d(TAG, "Error registering push service token: " + errorMessage);
            }
        });
    }


    /**
     *  A convenience method for posting errors to a ConnectCompletionListener
     *
     *  @param completionListener  The listener to notify
     *  @param errorMessage        The human-readable error message that occurred
     */
    private void postConnectError(final ConnectCompletionListener completionListener, final String errorMessage) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != completionListener) {
                    completionListener.onError(errorMessage);
                }
            }
        });
    }


    /**
     *  A convenience method for posting errors to a JoinGroupCompletionListener
     *
     *  @param completionListener  The listener to notify
     *  @param errorMessage        The human-readable error message that occurred
     */
    private void postJoinGroupMembersError(final JoinGroupCompletionListener completionListener, final String errorMessage) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != completionListener) {
                    completionListener.onError(errorMessage);
                }
            }
        });
    }

    private void getGroupHistoriesError(final GroupHistoriesCompletionListener completionListener,
                                        final String errorMessage) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (completionListener != null) {
                    completionListener.onError(errorMessage);
                }
            }
        });
    }

    private void getGroupHistoryError(final GroupHistoryCompletionListener completionListener,
                                      final String errorMessage) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (completionListener != null) {
                    completionListener.onError(errorMessage);
                }
            }
        });
    }


    /**
     *  Attempt to reconnect the client after a small delay
     */
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


    /**
     *  Attempt to reconnect the client if it is not already trying in another thread
     */
    private void actuallyReconnect() {
        if (((null == signalingChannel) || !signalingChannel.connected) && reconnect) {
            if (connectionInProgress) {
                // The client app must have initiated a connection manually during the timeout period. Try again later
                performReconnect();
            } else {
                Log.d(TAG, "Trying to reconnect...");
                connect(localEndpointID, applicationID, reconnect, presence, appContext, new ConnectCompletionListener() {
                    @Override
                    public void onError(final String errorMessage) {
                        // A REST API call failed. Socket errors are handled in the onError callback
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Listener listener = listenerReference.get();
                                if (null != listener) {
                                    listener.onError(RespokeClient.this, errorMessage);
                                }
                            }
                        });

                        // Try again later
                        performReconnect();
                    }
                });
            }
        }
    }


    /**
     *  Register for presence updates for the specified endpoint ID. Registration will not occur immediately, 
     *  it will be queued and performed asynchronously. Queuing allows for large numbers of presence 
     *  registration requests to occur in batches, minimizing the number of network transactions (and overall 
     *  time required).
     *
     *  @param endpointID  The ID of the endpoint for which to register for presence updates
     */
    private void queuePresenceRegistration(String endpointID) {
        if (null != endpointID) {
            Boolean shouldSpawnRegistrationTask = false;

            synchronized (this) {
                Boolean alreadyRegistered = presenceRegistered.get(endpointID);
                if ((null == alreadyRegistered) || !alreadyRegistered) {
                    presenceRegistrationQueue.add(endpointID);

                    // If a Runnable to register presence has not already been scheduled, note that one will be shortly
                    if (!registrationTaskWaiting) {
                        shouldSpawnRegistrationTask = true;
                        registrationTaskWaiting = true;
                    }
                }
            }

            if (shouldSpawnRegistrationTask) {
                // Schedule a Runnable to register presence on the next context switch, which should allow multiple subsequent calls to queuePresenceRegistration to get batched into a single socket transaction for efficiency
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        final HashMap<String, Boolean> endpointIDMap = new HashMap<String, Boolean>();

                        synchronized (this) {
                            // Build a list of the endpointIDs that have been scheduled for registration, and have not already been taken care of by a previous loop of this task
                            while (presenceRegistrationQueue.size() > 0) {
                                String nextEndpointID = presenceRegistrationQueue.remove(0);
                                Boolean alreadyRegistered = presenceRegistered.get(nextEndpointID);
                                if ((null == alreadyRegistered) || !alreadyRegistered) {
                                    endpointIDMap.put(nextEndpointID, true);
                                }
                            }

                            // Now that the batch of endpoint IDs to register has been determined, indicate to the client that any new registration calls should schedule a new Runnable
                            registrationTaskWaiting = false;
                        }

                        // Build an array from the map keySet to ensure there are no duplicates in the list
                        final ArrayList<String> endpointIDsToRegister = new ArrayList<String>(endpointIDMap.keySet());

                        if ((endpointIDsToRegister.size() > 0) && isConnected()) {
                            signalingChannel.registerPresence(endpointIDsToRegister, new RespokeSignalingChannel.RegisterPresenceListener() {
                                @Override
                                public void onSuccess(JSONArray initialPresenceData) {
                                    // Indicate that registration was successful for each endpoint ID in the list
                                    synchronized (RespokeClient.this) {
                                        for (String eachID : endpointIDsToRegister) {
                                            presenceRegistered.put(eachID, true);
                                        }
                                    }

                                    if (null != initialPresenceData) {
                                        for (int ii = 0; ii < initialPresenceData.length(); ii++) {
                                            try {
                                                JSONObject eachEndpointData = (JSONObject) initialPresenceData.get(ii);
                                                String dataEndpointID = eachEndpointData.getString("endpointId");
                                                RespokeEndpoint endpoint = getEndpoint(dataEndpointID, true);

                                                if (null != endpoint) {
                                                    JSONObject connectionData = eachEndpointData.getJSONObject("connectionStates");
                                                    Iterator<?> keys = connectionData.keys();

                                                    while (keys.hasNext()) {
                                                        String eachConnectionID = (String) keys.next();
                                                        JSONObject presenceDict = connectionData.getJSONObject(eachConnectionID);
                                                        Object newPresence = presenceDict.get("type");
                                                        RespokeConnection connection = endpoint.getConnection(eachConnectionID, false);

                                                        if ((null != connection) && (null != newPresence)) {
                                                            connection.presence = newPresence;
                                                        }
                                                    }
                                                }
                                            } catch (JSONException e) {
                                                // Silently skip this problem
                                            }
                                        }
                                    }

                                    for (String eachID : endpointIDsToRegister) {
                                        RespokeEndpoint endpoint = getEndpoint(eachID, true);
                                        
                                        if (null != endpoint) {
                                            endpoint.resolvePresence();
                                        }
                                    }
                                }

                                @Override
                                public void onError(final String errorMessage) {
                                    Log.d(TAG, "Error registering presence: " + errorMessage);
                                }
                            });
                        }
                    }
                });
            }
        }
    }


    // RespokeSignalingChannelListener methods


    public void onConnect(RespokeSignalingChannel sender, String endpointID, String connectionID) {
        connectionInProgress = false;
        reconnectCount = 0;
        localEndpointID = endpointID;
        localConnectionID = connectionID;

        Respoke.sharedInstance().clientConnected(this);

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

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onConnect(RespokeClient.this);
                }
            }
        });
    }


    public void onDisconnect(RespokeSignalingChannel sender) {
        // Can only reconnect in development mode, not brokered mode
        final boolean willReconnect = reconnect && (applicationID != null);

        calls.clear();
        groups.clear();
        knownEndpoints.clear();
        presenceRegistrationQueue.clear();
        presenceRegistered.clear();
        registrationTaskWaiting = false;

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onDisconnect(RespokeClient.this, willReconnect);
                }
            }
        });

        signalingChannel = null;

        if (willReconnect) {
            performReconnect();
        }
    }


    public void onIncomingCall(JSONObject sdp, String sessionID, String connectionID, String endpointID, String fromType, Date timestamp, RespokeSignalingChannel sender) {
        RespokeEndpoint endpoint = null;

        if (fromType.equals("web")) {
            /* Only create endpoints for type web */
            endpoint = getEndpoint(endpointID, false);

            if (null == endpoint) {
                Log.d(TAG, "Error: Could not create Endpoint for incoming call");
                return;
            }
        }

        final RespokeCall call = new RespokeCall(signalingChannel, sdp, sessionID, connectionID, endpointID, fromType, endpoint, false, timestamp);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onCall(RespokeClient.this, call);
                }
            }
        });
    }


    public void onIncomingDirectConnection(JSONObject sdp, String sessionID, String connectionID, String endpointID, Date timestamp, RespokeSignalingChannel sender) {
        RespokeEndpoint endpoint = getEndpoint(endpointID, false);

        if (null != endpoint) {
            final RespokeCall call = new RespokeCall(signalingChannel, sdp, sessionID, connectionID, endpointID, "web", endpoint, true, timestamp);
        } else {
            Log.d(TAG, "Error: Could not create Endpoint for incoming direct connection");
        }
    }


    public void onError(final String errorMessage, RespokeSignalingChannel sender) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onError(RespokeClient.this, errorMessage);
                }
            }
        });

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

            if (null != group) {
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

            if (null != group) {
                // Get the existing instance for this connection. If we are not already aware of it, ignore it
                RespokeConnection connection = getConnection(connectionID, endpointID, true);

                if (null != connection) {
                    group.connectionDidLeave(connection);
                }
            }
        }
    }


    private void didReceiveMessage(final RespokeEndpoint endpoint, final String message, final Date timestamp) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != listenerReference) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onMessage(message, endpoint, null, timestamp, false);
                    }
                }
            }
        });
    }

    private void didSendMessage(final RespokeEndpoint endpoint, final String message, final Date timestamp) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != listenerReference) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onMessage(message, endpoint, null, timestamp, true);
                    }
                }
            }
        });
    }

    public void onMessage(final String message, final Date timestamp, String fromEndpointID, String toEndpointID, RespokeSignalingChannel sender) {

        if (localEndpointID.equals(fromEndpointID) && (null != toEndpointID)) {
            // The local endpoint sent this message to the remote endpoint from another device (ccSelf)
            final RespokeEndpoint endpoint = getEndpoint(toEndpointID, false);
            if (null != endpoint) {
                endpoint.didReceiveMessage(message, timestamp);

                // Notify the client listener of the message
                didReceiveMessage(endpoint, message, timestamp);
            }
            return;
        }

        // The local endpoint received this message from a remote endpoint
        final RespokeEndpoint endpoint = getEndpoint(fromEndpointID, false);
        if (null != endpoint) {
            endpoint.didSendMessage(message, timestamp);

            // Notify the client listener of the message
            didSendMessage(endpoint, message, timestamp);
        }
    }


    public void onGroupMessage(final String message, String groupID, String endpointID, RespokeSignalingChannel sender, final Date timestamp) {
        final RespokeGroup group = groups.get(groupID);

        if (null != group) {
            final RespokeEndpoint endpoint = getEndpoint(endpointID, false);

            // Notify the group of the new message
            group.didReceiveMessage(message, endpoint, timestamp);

            // Notify the client listener of the group message
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (null != listenerReference) {
                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            listener.onMessage(message, endpoint, group, timestamp, null);
                        }
                    }
                }
            });
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


    public void directConnectionAvailable(final RespokeDirectConnection directConnection, final RespokeEndpoint endpoint) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != listenerReference) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onIncomingDirectConnection(directConnection, endpoint);
                    }
                }
            }
        });
    }


    /**
     * Build a group message from a JSON object. The format of the JSON object would be the
     * format that comes over the wire from Respoke when receiving a pubsub message. This same
     * format is used when retrieving message history.
     *
     * @param source The source JSON object to build the RespokeGroupMessage from
     * @return The built RespokeGroupMessage
     * @throws JSONException
     */
    private RespokeGroupMessage buildGroupMessage(JSONObject source) throws JSONException {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }

        final JSONObject header = source.getJSONObject("header");
        final String endpointID = header.getString("from");
        final RespokeEndpoint endpoint = getEndpoint(endpointID, false);
        final String groupID = header.getString("channel");
        RespokeGroup group = getGroup(groupID);

        if (group == null) {
            group = new RespokeGroup(groupID, signalingChannel, this, false);
            groups.put(groupID, group);
        }

        final String message = source.getString("message");

        final Date timestamp;

        if (!header.isNull("timestamp")) {
            timestamp = new Date(header.getLong("timestamp"));
        } else {
            // Just use the current time if no date is specified in the header data
            timestamp = new Date();
        }

        return new RespokeGroupMessage(message, group, endpoint, timestamp);
    }
}
