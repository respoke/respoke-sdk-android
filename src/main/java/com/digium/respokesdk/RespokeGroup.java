package com.digium.respokesdk;

import com.koushikdutta.async.http.socketio.Acknowledge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 *  A group, representing a collection of connections and the method by which to communicate with them.
 */
public class RespokeGroup {

    public RespokeGroupDelegate delegate;
    private String groupID;  ///< The ID of this group
    private String appToken;  ///< The application token to use
    private RespokeClient client;  ///< The client managing this group
    private RespokeSignalingChannel signalingChannel;  ///< The signaling channel to use
    private ArrayList<RespokeConnection> members;  ///< An array of the members of this group
    private boolean joined;  ///< Indicates if the client is a member of this group


    public RespokeGroup(String newGroupID, String token, RespokeSignalingChannel channel, RespokeClient newClient) {
        groupID = newGroupID;
        appToken = token;
        signalingChannel = channel;
        client = newClient;
        members = new ArrayList<RespokeConnection>();
        joined = true;
    }


    public void getMembers(final RespokeGetGroupMembersCompletionDelegate completionDelegate) {
        if (joined) {
            if ((null != groupID) && (groupID.length() > 0)) {
                String urlEndpoint = "/v1/channels/" + groupID + "/subscribers/";

                signalingChannel.sendRESTMessage("get", urlEndpoint, null, new RespokeSignalingChannelRESTDelegate() {
                    @Override
                    public void onSuccess(Object response) {
                        try {
                            JSONArray responseArray = new JSONArray((String) response);
                            ArrayList<RespokeConnection> nameList = new ArrayList<RespokeConnection>();

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

                            // If certain connections present in the members array prior to this method are somehow no longer in the list received from the server, it's assumed a pending onLeave message will handle flushing it out of the client cache after this method completes
                            members.clear();
                            members.addAll(nameList);

                            completionDelegate.onSuccess(nameList);
                        } catch (JSONException e) {
                            completionDelegate.onError("Invalid response from server");
                        }
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
            completionDelegate.onError("Not a member of this group anymore.");
        }
    }


    public void leave(final RespokeTaskCompletionDelegate completionDelegate) {
        if (joined) {
            if ((null != groupID) && (groupID.length() > 0)) {
                String urlEndpoint = "/v1/channels/" + groupID + "/subscribers/";

                signalingChannel.sendRESTMessage("get", urlEndpoint, null, new RespokeSignalingChannelRESTDelegate() {
                    @Override
                    public void onSuccess(Object response) {
                        joined = false;
                        completionDelegate.onSuccess();
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
            completionDelegate.onError("Not a member of this group anymore.");
        }
    }


    public boolean isJoined() {
        return joined;
    }


    public String getGroupID() {
        return groupID;
    }


    public void sendMessage(String message, final RespokeTaskCompletionDelegate completionDelegate) {
        if (joined) {
            if ((null != groupID) && (groupID.length() > 0)) {
                JSONObject data = null;

                try {
                    data = new JSONObject("{'endpointId':'" + client.getEndpointID() + "','message':'" + message + "'}");
                } catch (JSONException e) {
                    completionDelegate.onError("Unable to encode message");
                    return;
                }

                String urlEndpoint = "/v1/channels/" + groupID + "/publish/";

                signalingChannel.sendRESTMessage("post", urlEndpoint, data, new RespokeSignalingChannelRESTDelegate() {
                    @Override
                    public void onSuccess(Object response) {
                        completionDelegate.onSuccess();
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
            completionDelegate.onError("Not a member of this group anymore.");
        }
    }


    public void connectionDidJoin(RespokeConnection connection) {
        members.add(connection);
        delegate.onJoin(connection, this);
    }


    public void connectionDidLeave(RespokeConnection connection) {
        members.remove(connection);
        delegate.onLeave(connection, this);
    }


    public void didReceiveMessage(String message, RespokeEndpoint endpoint) {
        delegate.onGroupMessage(message, endpoint, this);
    }


}
