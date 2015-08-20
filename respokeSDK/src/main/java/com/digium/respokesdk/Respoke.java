/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRendererGui;

import java.util.ArrayList;

/**
 *  A global static class which provides access to the Respoke functionality.
 */
public class Respoke {

    public final static int GUID_STRING_LENGTH = 36; // The length of GUID strings

    private static Respoke _instance;
    private static boolean factoryStaticInitialized;
    private String pushToken;
    private ArrayList<RespokeClient> instances;
    private Context context;


    /**
     *  A listener interface to allow the receiver to be notified of the success or failure of an asynchronous operation
     */
    public interface TaskCompletionListener {


        /**
         *  Receive a notification that the asynchronous operation completed successfully
         */
        void onSuccess();


        /**
         *  Receive a notification that the asynchronous operation failed
         *
         *  @param errorMessage  A human-readable description of the error that was encountered
         */
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


    /**
     *  The private constructor for the Respoke singleton
     */
    private Respoke()
    {
        instances = new ArrayList<RespokeClient>();
    }


    /**
     *  Retrieve the globally shared instance of the Respoke SDK
     *
     *  @return Respoke SDK instance
     */
    public static Respoke sharedInstance()
    {
        if (_instance == null)
        {
            _instance = new Respoke();
        }

        return _instance;
    }


    /**
     *  This is one of two possible entry points for interacting with the library. This method creates a new Client object
     *  which represents your app's connection to the cloud infrastructure.  This method does NOT automatically call the
     *  client.connect() method after the client is created, so your app will need to call it when it is ready to
     *  connect.
     *
     *  @return A Respoke Client instance
     */
    public RespokeClient createClient(Context appContext)
    {
        context = appContext;

        RespokeClient newClient = new RespokeClient();
        instances.add(newClient);

        return newClient;
    }


    /**
     *  Unregister a client that is no longer active so that it's resources can be
     *  deallocated
     *
     *  @param client  The client to unregister
     */
    public void unregisterClient(RespokeClient client) {
        instances.remove(client);
    }


    /**
     *  Create a globally unique identifier for naming instances
     *
     *  @return New globally unique identifier
     */
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


    /**
     *  Notify the Respoke SDK that this device should register itself for push notifications
     *
     *  @param token  The token that identifies the device to GCMS.
     */
    public void registerPushToken(String token) {
        pushToken = token;

        if (instances.size() > 0) {
            registerPushServices();
        }
    }


    /**
     *  Unregister this device from the Respoke push notification service and stop any future notifications until it is re-registered
     *
     *  @param completionListener  A listener to be notified of the success of the asynchronous unregistration operation
     */
    public void unregisterPushServices(TaskCompletionListener completionListener) {
        RespokeClient activeInstance = null;

        // If there are already client instances running, check if any of them have already connected
        for (RespokeClient eachInstance : instances) {
            if (eachInstance.isConnected()) {
                // The push service only supports one endpoint per device, so the token only needs to be registered for the first active client (if there is more than one)
                activeInstance = eachInstance;
                break;
            }
        }

        if (null != activeInstance) {
            activeInstance.unregisterFromPushServices(completionListener);
        } else {
            postTaskError(completionListener, "There is no active client to unregister");
        }
    }


    /**
     *  Notify the shared SDK instance that the specified client has connected. This is for internal use only, and should never be called by your client application.
     *
     *  @param client  The client that just connected
     */
    public void clientConnected(RespokeClient client) {
        if (null != pushToken) {
            registerPushServices();
        }

        if (!factoryStaticInitialized) {
            // Perform a one-time WebRTC global initialization
            PeerConnectionFactory.initializeAndroidGlobals(context, true, true, true, VideoRendererGui.getEGLContext());
            factoryStaticInitialized = true;
        }
    }


    //** Private methods


    /**
     *  Attempt to register push services for this device
     */
    private void registerPushServices() {
        RespokeClient activeInstance = null;

        // If there are already client instances running, check if any of them have already connected
        for (RespokeClient eachInstance : instances) {
            if (eachInstance.isConnected()) {
                // The push service only supports one endpoint per device, so the token only needs to be registered for the first active client (if there is more than one)
                activeInstance = eachInstance;
            }
        }

        if (null != activeInstance) {
            // Notify the Respoke servers that this device is eligible to receive notifications directed at this endpointID
            activeInstance.registerPushServicesWithToken(pushToken);
        }
    }

}
