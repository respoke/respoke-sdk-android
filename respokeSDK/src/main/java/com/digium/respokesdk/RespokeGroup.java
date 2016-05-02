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

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;


/**
 *  A group, representing a collection of connections and the method by which to communicate with them.
 */
public class RespokeGroup {

    private WeakReference<Listener> listenerReference;
    private String groupID;  ///< The ID of this group
    private WeakReference<RespokeClient> clientReference;  ///< The client managing this group
    private RespokeSignalingChannel signalingChannel;  ///< The signaling channel to use
    private ArrayList<RespokeConnection> members;  ///< An array of the members of this group
    private boolean joined;  ///< Indicates if the client is a member of this group


    /**
     *  A listener interface to notify the receiver of events occurring with the group
     */
    public interface Listener {


        /**
         *  Receive a notification that an connection has joined this group.
         *
         *  @param connection The RespokeConnection that joined the group
         *  @param sender     The RespokeGroup that the connection has joined
         */
        void onJoin(RespokeConnection connection, RespokeGroup sender);


        /**
         *  Receive a notification that an connection has left this group.
         *
         *  @param connection The RespokeConnection that left the group
         *  @param sender     The RespokeGroup that the connection has left
         */
        void onLeave(RespokeConnection connection, RespokeGroup sender);


        /**
         *  Receive a notification that a group message has been received
         *
         *  @param message  The body of the message
         *  @param endpoint The endpoint that sent the message
         *  @param sender   The group that received the message
         *  @param timestamp The timestamp of when the message was sent.
         */
        void onGroupMessage(String message, RespokeEndpoint endpoint, RespokeGroup sender, Date timestamp);


    }


    /**
     *  A listener interface to receive a notification that the task to get the list of group members has completed
     */
    public interface GetGroupMembersCompletionListener {

        /**
        *  Receive an array of the group members asynchronously
        *
        *  @param memberArray An array of the connections that are a member of this group
        */
        void onSuccess(ArrayList<RespokeConnection> memberArray);


        /**
         *  Receive a notification that the asynchronous operation failed
         *
         *  @param errorMessage  A human-readable description of the error that was encountered
         */
        void onError(String errorMessage);
    }

    /**
     *  The constructor for this class
     *
     *  @param newGroupID  The ID for this group
     *  @param channel     The signaling channel managing communications with this group
     *  @param newClient   The client to which this group instance belongs
     *  @param isJoined    Whether the group has already been joined
     */
    public RespokeGroup(String newGroupID, RespokeSignalingChannel channel, RespokeClient newClient,
                        Boolean isJoined) {
        groupID = newGroupID;
        signalingChannel = channel;
        clientReference = new WeakReference<RespokeClient>(newClient);
        members = new ArrayList<RespokeConnection>();
        joined = isJoined;
    }

    public RespokeGroup(String newGroupID, RespokeSignalingChannel channel, RespokeClient newClient) {
        this(newGroupID, channel, newClient, true);
    }

    /**
     *  Set a receiver for the Listener interface
     *
     *  @param listener  The new receiver for events from the Listener interface for this group instance
     */
    public void setListener(Listener listener) {
        listenerReference = new WeakReference<Listener>(listener);
    }


    /**
     *  Get an array containing the members of the group.
     *
     *  @param completionListener  A listener to receive a notification on the success of the asynchronous operation
     **/
    public void getMembers(final GetGroupMembersCompletionListener completionListener) {
        if (isJoined()) {
            if ((null != groupID) && (groupID.length() > 0)) {
                String urlEndpoint = "/v1/channels/" + groupID + "/subscribers/";

                signalingChannel.sendRESTMessage("get", urlEndpoint, null, new RespokeSignalingChannel.RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        JSONArray responseArray = null;

                        if (response != null) {
                            if (response instanceof JSONArray) {
                                responseArray = (JSONArray) response;
                            } else if (response instanceof String) {
                                try {
                                    responseArray = new JSONArray((String) response);
                                } catch (JSONException e) {
                                    // An exception will trigger the error handler
                                }
                            }
                        }

                        if (null != responseArray) {
                            final ArrayList<RespokeConnection> nameList = new ArrayList<RespokeConnection>();
                            RespokeClient client = clientReference.get();
                            if (null != client) {
                                for (int ii = 0; ii < responseArray.length(); ii++) {
                                    try {
                                        JSONObject eachEntry = (JSONObject) responseArray.get(ii);
                                        String newEndpointID = eachEntry.getString("endpointId");
                                        String newConnectionID = eachEntry.getString("connectionId");

                                        // Do not include ourselves in this list
                                        if (!newEndpointID.equals(client.getEndpointID())) {
                                            // Get the existing instance for this connection, or create a new one if necessary
                                            RespokeConnection connection = client.getConnection(newConnectionID, newEndpointID, false);

                                            if (null != connection) {
                                                nameList.add(connection);
                                            }
                                        }
                                    } catch (JSONException e) {
                                        // Skip unintelligible records
                                    }
                                }
                            }

                            // If certain connections present in the members array prior to this method are somehow no longer in the list received from the server, it's assumed a pending onLeave message will handle flushing it out of the client cache after this method completes
                            members.clear();
                            members.addAll(nameList);

                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    if (null != completionListener) {
                                        completionListener.onSuccess(nameList);
                                    }
                                }
                            });
                        } else {
                            postGetGroupMembersError(completionListener, "Invalid response from server");
                        }
                    }

                    @Override
                    public void onError(final String errorMessage) {
                        postGetGroupMembersError(completionListener, errorMessage);
                    }
                });
            } else {
                postGetGroupMembersError(completionListener, "Group name must be specified");
            }
        } else {
            postGetGroupMembersError(completionListener, "Not a member of this group anymore.");
        }
    }


    /**
     * Join this group
     *
     * @param completionListener A listener to receive a notification upon completion of this
     *                           async operation
     */
    public void join(final Respoke.TaskCompletionListener completionListener) {
        if (!isConnected()) {
            Respoke.postTaskError(completionListener, "Can't complete request when not connected. " +
                    "Please reconnect!");
            return;
        }

        if ((groupID == null) || (groupID.length() == 0)) {
            Respoke.postTaskError(completionListener, "Group name must be specified");
            return;
        }

        String urlEndpoint = String.format("/v1/groups/%s", groupID);
        signalingChannel.sendRESTMessage("post", urlEndpoint, null, new RespokeSignalingChannel.RESTListener() {
            @Override
            public void onSuccess(Object response) {
                joined = true;
                Respoke.postTaskSuccess(completionListener);
            }

            @Override
            public void onError(final String errorMessage) {
                Respoke.postTaskError(completionListener, errorMessage);
            }
        });
    }


    /**
     *  Leave this group
     *
     *  @param completionListener  A listener to receive a notification on the success of the asynchronous operation
     **/
    public void leave(final Respoke.TaskCompletionListener completionListener) {
        if (isJoined()) {
            if ((null != groupID) && (groupID.length() > 0)) {
                String urlEndpoint = "/v1/groups";

                JSONArray groupList = new JSONArray();
                groupList.put(groupID);

                JSONObject data = new JSONObject();
                try {
                    data.put("groups", groupList);

                    signalingChannel.sendRESTMessage("delete", urlEndpoint, data, new RespokeSignalingChannel.RESTListener() {
                        @Override
                        public void onSuccess(Object response) {
                            joined = false;

                            Respoke.postTaskSuccess(completionListener);
                        }

                        @Override
                        public void onError(final String errorMessage) {
                            Respoke.postTaskError(completionListener, errorMessage);
                        }
                    });
                } catch (JSONException e) {
                    Respoke.postTaskError(completionListener, "Error encoding group list to json");
                }
            } else {
                Respoke.postTaskError(completionListener, "Group name must be specified");
            }
        } else {
            Respoke.postTaskError(completionListener, "Not a member of this group anymore.");
        }
    }


    /**
     *  Return true if the local client is a member of this group and false if not.
     *
     *  @return The membership status
     */
    public boolean isJoined() {
        return joined && isConnected();
    }

    public boolean isConnected() {
        return (null != signalingChannel) && signalingChannel.connected;
    }

    /**
     *  Get the ID for this group
     *
     *  @return The group's ID
     */
    public String getGroupID() {
        return groupID;
    }


    /**
     *  Send a message to the entire group.
     *
     *  @param message             The message to send
     *  @param push                A flag indicating if a push notification should be sent for this message
     *  @param persist             A flag indicating if history should be maintained for this message.
     *  @param completionListener  A listener to receive a notification on the success of the asynchronous operation
     **/
    public void sendMessage(String message, boolean push, boolean persist,
                            final Respoke.TaskCompletionListener completionListener) {
        if (isJoined()) {
            if ((null != groupID) && (groupID.length() > 0)) {
                RespokeClient client = clientReference.get();
                if (null != client) {

                    JSONObject data = new JSONObject();

                    try {
                        data.put("endpointId", client.getEndpointID());
                        data.put("message", message);
                        data.put("push", push);
                        data.put("persist", persist);
                    } catch (JSONException e) {
                        Respoke.postTaskError(completionListener, "Unable to encode message");
                        return;
                    }

                    String urlEndpoint = "/v1/channels/" + groupID + "/publish/";
                    signalingChannel.sendRESTMessage("post", urlEndpoint, data, new RespokeSignalingChannel.RESTListener() {
                        @Override
                        public void onSuccess(Object response) {
                            Respoke.postTaskSuccess(completionListener);
                        }

                        @Override
                        public void onError(final String errorMessage) {
                            Respoke.postTaskError(completionListener, errorMessage);
                        }
                    });
                } else {
                    Respoke.postTaskError(completionListener, "There was an internal error processing this request.");
                }
            } else {
                Respoke.postTaskError(completionListener, "Group name must be specified");
            }
        } else {
            Respoke.postTaskError(completionListener, "Not a member of this group anymore.");
        }
    }

    public void sendMessage(String message, boolean push, final Respoke.TaskCompletionListener completionListener) {
        sendMessage(message, push, false, completionListener);
    }


    /**
     *  Notify the group that a connection has joined. This is used internally to the SDK and should not be called directly by your client application.
     *
     *  @param connection The connection that has joined the group
     */
    public void connectionDidJoin(final RespokeConnection connection) {
        members.add(connection);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onJoin(connection, RespokeGroup.this);
                }
            }
        });
    }


    /**
     *  Notify the group that a connection has left. This is used internally to the SDK and should not be called directly by your client application.
     *
     *  @param connection The connection that has left the group
     */
    public void connectionDidLeave(final RespokeConnection connection) {
        members.remove(connection);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onLeave(connection, RespokeGroup.this);
                }
            }
        });
    }


    /**
     *  Notify the group that a group message was received. This is used internally to the SDK and should not be called directly by your client application.
     *
     *  @param message      The body of the message
     *  @param endpoint     The endpoint that sent the message
     *  @param timestamp    The message timestamp
     */
    public void didReceiveMessage(final String message, final RespokeEndpoint endpoint, final Date timestamp) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onGroupMessage(message, endpoint, RespokeGroup.this, timestamp);
                }
            }
        });
    }


    //** Private methods


    /**
     *  A convenience method for posting errors to a GetGroupMembersCompletionListener
     *
     *  @param completionListener  The listener to notify
     *  @param errorMessage        The human-readable error message that occurred
     */
    private void postGetGroupMembersError(final GetGroupMembersCompletionListener completionListener, final String errorMessage) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != completionListener) {
                    completionListener.onError(errorMessage);
                }
            }
        });
    }


}
