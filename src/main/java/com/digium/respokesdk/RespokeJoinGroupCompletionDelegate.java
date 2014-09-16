package com.digium.respokesdk;

/**
 * Created by jasonadams on 9/15/14.
 */
public interface RespokeJoinGroupCompletionDelegate {

    void onSuccess(RespokeGroup group);

    void onError(String errorMessage);

}
