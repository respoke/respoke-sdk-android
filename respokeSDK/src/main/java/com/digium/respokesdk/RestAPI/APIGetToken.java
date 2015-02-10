package com.digium.respokesdk.RestAPI;

import android.content.Context;

import org.json.JSONException;

/**
 * Created by jasonadams on 9/11/14.
 */
public class APIGetToken extends APITransaction {

    private static final String DEFAULT_TTL = "21600";

    public String appID;
    public String endpointID;
    public String token;

    public APIGetToken(Context context, String baseURL) {
        super(context, baseURL + "/v1/tokens");
    }


    public void go() {
        params = "appId=" + appID + "&endpointId=" + endpointID + "&ttl=" + DEFAULT_TTL;

        super.go();
    }


    @Override
    public void transactionComplete() {
        if (success) {
            try {
                token = jsonResult.getString("tokenId");
            }
            catch (JSONException e) {
                success = false;
                errorMessage = "Unexpected response from server";
            }
        }
    }
}
