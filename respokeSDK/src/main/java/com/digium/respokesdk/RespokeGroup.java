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

import com.koushikdutta.async.http.socketio.Acknowledge;

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
    private String appToken;  ///< The application token to use
    private WeakReference<RespokeClient> clientReference;  ///< The client managing this group
    private RespokeSignalingChannel signalingChannel;  ///< The signaling channel to use
    private ArrayList<RespokeConnection> members;  ///< An array of the members of this group
    private boolean joined;  ///< Indicates if the client is a member of this group


    /**
     *  A delegate protocol to notify the receiver of events occurring with the group
     */
    public interface Listener {


        /**
         *  Receive a notification that an connection has joined this group.
         *
         *  @param connection The RespokeConnection that joined the group
         *  @param sender     The RespokeGroup that the connection has joined
         */
        public void onJoin(RespokeConnection connection, RespokeGroup sender);


        /**
         *  Receive a notification that an connection has left this group.
         *
         *  @param connection The RespokeConnection that left the group
         *  @param sender     The RespokeGroup that the connection has left
         */
        public void onLeave(RespokeConnection connection, RespokeGroup sender);


        /**
         *  Receive a notification that a group message has been received
         *
         *  @param message  The body of the message
         *  @param endpoint The endpoint that sent the message
         *  @param sender   The group that received the message
         */
        public void onGroupMessage(String message, RespokeEndpoint endpoint, RespokeGroup sender, Date timestamp);


    }


    /**
     * A listener interface to receive a notification that the task to get the list of group members has completed
     */
    public interface GetGroupMembersCompletionListener {

        void onSuccess(ArrayList<RespokeConnection> memberArray);

        void onError(String errorMessage);
    }


    public RespokeGroup(String newGroupID, String token, RespokeSignalingChannel channel, RespokeClient newClient) {
        groupID = newGroupID;
        appToken = token;
        signalingChannel = channel;
        clientReference = new WeakReference<RespokeClient>(newClient);
        members = new ArrayList<RespokeConnection>();
        joined = true;
    }


    public void setListener(Listener listener) {
        listenerReference = new WeakReference<Listener>(listener);
    }


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


    public boolean isJoined() {
        return joined && (null != signalingChannel) && (signalingChannel.connected);
    }


    public String getGroupID() {
        return groupID;
    }


    public void sendMessage(String message, boolean push, final Respoke.TaskCompletionListener completionListener) {
        if (isJoined()) {
            if ((null != groupID) && (groupID.length() > 0)) {
                RespokeClient client = clientReference.get();
                if (null != client) {

                    JSONObject data = new JSONObject();

                    try {
                        data.put("endpointId", client.getEndpointID());
                        data.put("message", message);
                        data.put("push", push);
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


}
