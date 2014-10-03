package com.digium.respokesdk;

import com.koushikdutta.async.http.socketio.Acknowledge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;


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
        public void onGroupMessage(String message, RespokeEndpoint endpoint, RespokeGroup sender);


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
        if (joined) {
            if ((null != groupID) && (groupID.length() > 0)) {
                String urlEndpoint = "/v1/channels/" + groupID + "/subscribers/";

                signalingChannel.sendRESTMessage("get", urlEndpoint, null, new RespokeSignalingChannel.RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        try {
                            JSONArray responseArray = new JSONArray((String) response);
                            ArrayList<RespokeConnection> nameList = new ArrayList<RespokeConnection>();
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

                            completionListener.onSuccess(nameList);
                        } catch (JSONException e) {
                            completionListener.onError("Invalid response from server");
                        }
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
            completionListener.onError("Not a member of this group anymore.");
        }
    }


    public void leave(final Respoke.TaskCompletionListener completionListener) {
        if (joined) {
            if ((null != groupID) && (groupID.length() > 0)) {
                String urlEndpoint = "/v1/channels/" + groupID + "/subscribers/";

                signalingChannel.sendRESTMessage("delete", urlEndpoint, null, new RespokeSignalingChannel.RESTListener() {
                    @Override
                    public void onSuccess(Object response) {
                        joined = false;
                        completionListener.onSuccess();
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
            completionListener.onError("Not a member of this group anymore.");
        }
    }


    public boolean isJoined() {
        return joined;
    }


    public String getGroupID() {
        return groupID;
    }


    public void sendMessage(String message, final Respoke.TaskCompletionListener completionListener) {
        if (joined) {
            if ((null != groupID) && (groupID.length() > 0)) {
                RespokeClient client = clientReference.get();
                if (null != client) {

                    JSONObject data = null;

                    try {
                        data = new JSONObject("{'endpointId':'" + client.getEndpointID() + "','message':'" + message + "'}");
                    } catch (JSONException e) {
                        completionListener.onError("Unable to encode message");
                        return;
                    }

                    String urlEndpoint = "/v1/channels/" + groupID + "/publish/";

                    signalingChannel.sendRESTMessage("post", urlEndpoint, data, new RespokeSignalingChannel.RESTListener() {
                        @Override
                        public void onSuccess(Object response) {
                            completionListener.onSuccess();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            completionListener.onError(errorMessage);
                        }
                    });
                } else {
                    completionListener.onError("There was an internal error processing this request.");
                }
            } else {
                completionListener.onError("Group name must be specified");
            }
        } else {
            completionListener.onError("Not a member of this group anymore.");
        }
    }


    public void connectionDidJoin(RespokeConnection connection) {
        members.add(connection);
        Listener listener = listenerReference.get();
        if (null != listener) {
            listener.onJoin(connection, this);
        }
    }


    public void connectionDidLeave(RespokeConnection connection) {
        members.remove(connection);
        Listener listener = listenerReference.get();
        if (null != listener) {
            listener.onLeave(connection, this);
        }
    }


    public void didReceiveMessage(String message, RespokeEndpoint endpoint) {
        Listener listener = listenerReference.get();
        if (null != listener) {
            listener.onGroupMessage(message, endpoint, this);
        }
    }


}
