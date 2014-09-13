package com.digium.respokesdk;

import android.util.Log;
import android.view.inputmethod.InputMethodSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.socketio.Acknowledge;
import com.koushikdutta.async.http.socketio.ConnectCallback;
import com.koushikdutta.async.http.socketio.DisconnectCallback;
import com.koushikdutta.async.http.socketio.EventCallback;
import com.koushikdutta.async.http.socketio.JSONCallback;
import com.koushikdutta.async.http.socketio.SocketIOClient;
import com.koushikdutta.async.http.socketio.StringCallback;

import com.digium.respokesdk.RestAPI.APITransaction;

/**
 * Created by jasonadams on 9/13/14.
 */
public class RespokeSignalingChannel {

    private static final String TAG = "RespokeSignalingChannel";
    private static final String RESPOKE_SOCKETIO_PORT = "443";

    public boolean connected;
    private String appToken;
    private SocketIOClient client;
    private String connectionID;


    public RespokeSignalingChannel(String token) {
        appToken = token;
    }


    public void authenticate() {
        String connectURL = "https://" + APITransaction.RESPOKE_BASE_URL + ":" + RESPOKE_SOCKETIO_PORT + "?app-token=" + appToken;

        SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), connectURL, new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient newClient) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }

                client = newClient;
                connected = true;

                client.setStringCallback(new StringCallback() {
                    @Override
                    public void onString(String string, Acknowledge acknowledge) {
                        System.out.println(string);
                    }
                });
                client.on("someEvent", new EventCallback() {
                    @Override
                    public void onEvent(JSONArray arguments, Acknowledge acknowledge) {
                        System.out.println("args: " + arguments.toString());
                    }
                });

                client.setDisconnectCallback(new DisconnectCallback() {
                    @Override
                    public void onDisconnect(Exception e) {
                        Log.d(TAG, "Socket disconnected");
                        connected = false;
                        client = null;
                    }
                });

                // Once the socket is connected, perform a post to get the connection and endpoint IDs for this client
                sendRESTMessage("post", "/v1/endpointconnections", null, new Acknowledge() {
                    @Override
                    public void acknowledge(JSONArray arguments) {
                        if ((arguments != null) && (arguments.length() == 1))
                        {
                            try {
                                String responseString = (String) arguments.get(0);
                                JSONObject response = new JSONObject(responseString);
                                connectionID = response.getString("id");
                                String endpointID = response.getString("endpointId");
                            }
                            catch (JSONException e)
                            {
                                Log.d(TAG, "Unexpected response from server");
                            }
                        } else {
                            Log.d(TAG, "Unexpected response from server");
                        }
                    }
                });
            }
        });
    }


    public void sendRESTMessage(String httpMethod, String url, Object data, Acknowledge acknowledge) {
        if (connected) {
            JSONArray array = new JSONArray();

            try
            {
                JSONObject message = new JSONObject("{'headers':{'App-Token':'" + appToken + "'},'url':'" + url + "'}");
                array.put(message);
            } catch (JSONException e) {
                Log.d(TAG, "Unable to JSON encode message");
            }

            client.emit(httpMethod, array, acknowledge);
        }
    }

}
