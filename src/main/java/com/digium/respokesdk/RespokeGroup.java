package com.digium.respokesdk;

import java.util.ArrayList;

/**
 * Created by jasonadams on 9/13/14.
 */
public class RespokeGroup {

    public RespokeGroupDelegate delegate;
    private String groupID;  ///< The ID of this group
    private String appToken;  ///< The application token to use
    private RespokeClient client;  ///< The client managing this group
    private RespokeSignalingChannel signalingChannel;  ///< The signaling channel to use
    private ArrayList members;  ///< An array of the members of this group
    private boolean joined;  ///< Indicates if the client is a member of this group


    public RespokeGroup(String newGroupID, String token, RespokeSignalingChannel channel, RespokeClient newClient) {
        groupID = newGroupID;
        appToken = token;
        signalingChannel = channel;
        client = newClient;
        members = new ArrayList();
        joined = true;
    }


    public boolean isJoined() {
        return joined;
    }


    public String getGroupID() {
        return groupID;
    }


}
