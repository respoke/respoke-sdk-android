package com.digium.respokesdk.RestAPI;

import android.content.Context;

import org.json.JSONException;

/**
 * Created by jasonadams on 9/13/14.
 */
public class APIDoOpen extends APITransaction {

    public String tokenID;
    public String appToken;

    public APIDoOpen(Context context) {
        super(context);

        urlEndpoint = "/v1/appauthsessions";
    }


    public void go() {
        params = "tokenId=" + tokenID;

        super.go();
    }


    @Override
    public void transactionComplete() {
        if (success) {
            try {
                appToken = jsonResult.getString("token");
            }
            catch (JSONException e) {
                success = false;
                errorMessage = "Unexpected response from server";
            }
        }
    }
}
