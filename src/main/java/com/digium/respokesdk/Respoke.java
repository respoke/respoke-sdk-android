package com.digium.respokesdk;

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
}
