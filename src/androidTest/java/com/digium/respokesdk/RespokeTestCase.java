package com.digium.respokesdk;

import android.test.AndroidTestCase;
import android.util.Log;

/**
 * A test case base class to provide commonly used methods
 *
 * Created by jasonadams on 1/20/15.
 */
public class RespokeTestCase extends AndroidTestCase {

    public static int TEST_TIMEOUT = 60;  // Timeout in seconds
    public boolean asyncTaskDone;

    private static final String TAG = "RespokeTestCase";


    public String generateTestEndpointID() {
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


    void testSomething() {
        // An empty test case to make Android Studio behave properly. A test case class without any test cases confuses it.
    }

}
