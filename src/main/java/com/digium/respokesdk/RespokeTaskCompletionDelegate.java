package com.digium.respokesdk;

/**
 * Created by jasonadams on 9/15/14.
 */
public interface RespokeTaskCompletionDelegate {

    void onSuccess();

    void onError(String errorMessage);

}
