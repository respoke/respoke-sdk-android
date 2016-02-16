package com.digium.respokesdk;

import java.util.Date;

public class RespokeGroupMessage {
    public String message;
    public RespokeGroup group;
    public RespokeEndpoint endpoint;
    public Date timestamp;

    public RespokeGroupMessage(String message, RespokeGroup group, RespokeEndpoint endpoint,
                               Date timestamp) {
        this.message = message;
        this.endpoint = endpoint;
        this.group = group;
        this.timestamp = timestamp;
    }
}
