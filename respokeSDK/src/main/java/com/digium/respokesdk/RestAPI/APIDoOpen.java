/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdk.RestAPI;

import android.content.Context;

import org.json.JSONException;


public class APIDoOpen extends APITransaction {

    public String tokenID;
    public String appToken;

    public APIDoOpen(Context context, String baseURL) {
        super(context, baseURL + "/v1/session-tokens");
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
