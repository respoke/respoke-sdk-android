package com.digium.respokesdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.digium.respokesdk.RestAPI.APIRegisterPushToken;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRendererGui;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Random;

/**
 *  A global static class which provides access to the Respoke functionality.
 */
public class Respoke {

    public final static int GUID_STRING_LENGTH = 36;
    private static Respoke _instance;
    private static boolean factoryStaticInitialized;
    private String pushToken;
    private ArrayList<RespokeClient> instances;
    private Context context;


    public interface TaskCompletionListener {

        void onSuccess();

        void onError(String errorMessage);

    }


    /**
     * A helper function to post success to a TaskCompletionListener on the UI thread
     *
     * @param completionListener The TaskCompletionListener to notify
     */
    public static void postTaskSuccess(final TaskCompletionListener completionListener) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != completionListener) {
                    completionListener.onSuccess();
                }
            }
        });
    }


    /**
     * A helper function to post an error message to a TaskCompletionListener on the UI thread
     *
     * @param completionListener The TaskCompletionListener to notify
     * @param errorMessage       The error message to post
     */
    public static void postTaskError(final TaskCompletionListener completionListener, final String errorMessage) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (null != completionListener) {
                    completionListener.onError(errorMessage);
                }
            }
        });
    }


    private Respoke()
    {
        instances = new ArrayList<RespokeClient>();
    }


    public static Respoke sharedInstance()
    {
        if (_instance == null)
        {
            _instance = new Respoke();
        }

        return _instance;
    }


    public RespokeClient createClient(Context appContext)
    {
        context = appContext;

        RespokeClient newClient = new RespokeClient();
        instances.add(newClient);

        return newClient;
    }


    public void unregisterClient(RespokeClient client) {
        instances.remove(client);
    }


    public static String makeGUID() {
        String uuid = "";
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int rnd = 0;
        int r;

        for (int i = 0; i < GUID_STRING_LENGTH; i += 1) {
            if (i == 8 || i == 13 ||  i == 18 || i == 23) {
                uuid = uuid + "-";
            } else if (i == 14) {
                uuid = uuid + "4";
            } else {
                if (rnd <= 0x02) {
                    rnd = (int) (0x2000000 + Math.round(java.lang.Math.random() * 0x1000000));
                }
                r = rnd & 0xf;
                rnd = rnd >> 4;

                if (i == 19) {
                    uuid = uuid + chars.charAt((r & 0x3) | 0x8);
                } else {
                    uuid = uuid + chars.charAt(r);
                }
            }
        }
        return uuid;
    }


    public void clientConnected(RespokeClient client, String endpointID) {
        if (null != pushToken) {
            registerPushServices();
        }

        if (!factoryStaticInitialized) {
            PeerConnectionFactory.initializeAndroidGlobals(context, true, true, VideoRendererGui.getEGLContext());
            factoryStaticInitialized = true;
        }
    }


    public void registerPushToken(String token) {
        pushToken = token;

        if (instances.size() > 0) {
            registerPushServices();
        }
    }


    public void registerPushServices() {
        ArrayList<String> endpointIDArray = new ArrayList<String>();

        // If there are already client instances running, check if any of them have already connected
        for (RespokeClient eachInstance : instances) {
            if (eachInstance.isConnected()) {
                // This client has already connected, so notify the Respoke servers that this device is eligible to receive notifications directed at this endpointID
                endpointIDArray.add(eachInstance.getEndpointID());
            }
        }

        APIRegisterPushToken request = new APIRegisterPushToken(context) {
            @Override
            public void transactionComplete() {
                super.transactionComplete();

                if (success) {
                    Log.d("", "Successfully registered push token");
                } else {
                    Log.d("", "Push register failed: " + errorMessage);
                }
            }
        };

        request.endpointIDArray = endpointIDArray;
        request.token = pushToken;
        request.go();
    }


}
