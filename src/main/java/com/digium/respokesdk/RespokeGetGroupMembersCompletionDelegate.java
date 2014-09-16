package com.digium.respokesdk;

import java.util.ArrayList;

/**
 * Created by jasonadams on 9/15/14.
 */
public interface RespokeGetGroupMembersCompletionDelegate {

    void onSuccess(ArrayList<RespokeConnection> memberArray);

    void onError(String errorMessage);
}
