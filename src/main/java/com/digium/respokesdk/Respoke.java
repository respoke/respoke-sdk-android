package com.digium.respokesdk;

import java.lang.reflect.Array;
import java.util.Random;

/**
 * Created by jasonadams on 9/13/14.
 */
public class Respoke {

    private static Respoke _instance;


    private Respoke()
    {

    }


    public static Respoke sharedInstance()
    {
        if (_instance == null)
        {
            _instance = new Respoke();
        }

        return _instance;
    }


    public RespokeClient createClient()
    {
        return new RespokeClient();
    }


    public static String makeGUID() {
        String uuid = "";
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int rnd = 0;
        int r;

        for (int i = 0; i < 36; i += 1) {
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
}
