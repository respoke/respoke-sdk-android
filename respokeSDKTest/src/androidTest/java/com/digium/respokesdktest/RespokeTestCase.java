package com.digium.respokesdktest;

import android.test.AndroidTestCase;
import android.util.Log;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeClient;

/**
 * A test case base class to provide commonly used methods
 *
 * Created by jasonadams on 1/20/15.
 */
public abstract class RespokeTestCase extends AndroidTestCase {

    public final static String TEST_RESPOKE_BASE_URL = "https://api-int.respoke.io";
    public final static String testAppID = "57ac5f3a-0513-40b5-ba42-b80939e69436";

    public static int TEST_TIMEOUT = 60;  // Timeout in seconds
    public static int CALL_TEST_TIMEOUT = 60; // Timeout in seconds for calling tests (which take longer to setup)
    public final static String TEST_BOT_ENDPOINT_ID = "testbot";
    public boolean asyncTaskDone;

    private static final String TAG = "RespokeTestCase";


    public static String generateTestEndpointID() {
        String uuid = "";
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int rnd = 0;
        int r;

        for (int i = 0; i < 6; i += 1) {
            if (rnd <= 0x02) {
                rnd = (int) (0x2000000 + Math.round(java.lang.Math.random() * 0x1000000));
            }
            r = rnd & 0xf;
            rnd = rnd >> 4;
            uuid = uuid + chars.charAt(r);
        }
        return "test_user_" + uuid;
    }


    public boolean waitForCompletion(long timeoutSecs) {
        long timeoutDate = System.currentTimeMillis() + (timeoutSecs * 1000);

        try {
            do {
                if (System.currentTimeMillis() > timeoutDate) {
                    Log.e(TAG, "network timeout");
                    Log.e(TAG, "inside TapInspectTestCase : class = " + this.getClass().getSimpleName());
                    break;
                }
                synchronized (this) {
                    this.wait(100);
                }
            } while (!asyncTaskDone);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        return asyncTaskDone;
    }


    public RespokeClient createTestClient(String endpointID, RespokeClient.Listener listener) {
        final RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", client);
        client.baseURL = TEST_RESPOKE_BASE_URL;

        asyncTaskDone = false;
        client.setListener(listener);
        client.connect(endpointID, RespokeTestCase.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect", false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Client connect timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Test client should connect", client.isConnected());

        return client;
    }

    
}
