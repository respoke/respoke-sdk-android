package com.digium.respokesdk;

import java.util.Date;

import static android.R.id.message;

/**
 *
 */

public class RespokeConversationReadStatus {
    public String groupId;
    public Date timestamp;

    public RespokeConversationReadStatus(String groupId, Date timestamp) {
        this.groupId = groupId;
        this.timestamp = timestamp;
    }
}
