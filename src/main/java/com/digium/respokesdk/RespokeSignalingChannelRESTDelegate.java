package com.digium.respokesdk;

import org.json.JSONObject;

/**
 * Created by jasonadams on 9/15/14.
 */
public interface RespokeSignalingChannelRESTDelegate {

    void onSuccess(Object response);

    void onError(String errorMessage);

}
