package com.digium.respokesdk.RestAPI;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by jasonadams on 11/21/14.
 */
public class APIRegisterPushToken extends APITransaction {

    private static final String RESPOKE_PUSH_SETTINGS = "RESPOKE_PUSH_SETTINGS";
    private static final String LAST_VALID_PUSH_TOKEN_KEY = "LAST_VALID_PUSH_TOKEN_KEY";
    private static final String RESPOKE_PUSH_SERVER_URL = "http://192.168.1.65:3000";

    public String token;
    public ArrayList<String> endpointIDArray;

    public APIRegisterPushToken(Context context) {
        super(context);

        baseURL = RESPOKE_PUSH_SERVER_URL;
        urlEndpoint = "/v1/register";
        contentType = "application/json";
    }


    public void go() {
        if (null != token) {
            SharedPreferences settings = context.getSharedPreferences(RESPOKE_PUSH_SETTINGS, 0);
            String lastKnownPushToken = settings.getString(LAST_VALID_PUSH_TOKEN_KEY, null);

            JSONObject jsonParams = new JSONObject();
            JSONArray jsonArray = new JSONArray();

            try {
                for (String eachEndpointID : endpointIDArray) {
                    jsonArray.put(eachEndpointID);
                }

                jsonParams.put("app_id", 1);
                jsonParams.put("names", jsonArray);
                jsonParams.put("service", 0);
                jsonParams.put("token", token);

                if ((null != lastKnownPushToken) && !(lastKnownPushToken.equals(token))) {
                    jsonParams.put("old_token", lastKnownPushToken);
                }

                params = jsonParams.toString();

                super.go();
            } catch (JSONException e) {
                success = false;
                errorMessage = "Unable to encode message to json";
                transactionComplete();
            }
        } else {
            success = false;
            errorMessage = "Push token may not be blank";
            transactionComplete();
        }
    }


    @Override
    public void transactionComplete() {
        if (success) {
            SharedPreferences settings = context.getSharedPreferences(RESPOKE_PUSH_SETTINGS, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(LAST_VALID_PUSH_TOKEN_KEY, token).apply();
        }
    }


}
