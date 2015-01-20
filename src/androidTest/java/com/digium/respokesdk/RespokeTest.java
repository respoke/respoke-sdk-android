package com.digium.respokesdk;

import android.app.Application;
import android.test.ApplicationTestCase;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class RespokeTest extends ApplicationTestCase<Application> {

    public final static String testAppID = "57ac5f3a-0513-40b5-ba42-b80939e69436";

    public RespokeTest() {
        super(Application.class);
    }


    public void testMakeGUID() {
        String guid1 = Respoke.makeGUID();
        String guid2 = Respoke.makeGUID();
        String guid3 = Respoke.makeGUID();
        String guid4 = Respoke.makeGUID();

        assertTrue("GUIDs should be " + Respoke.GUID_STRING_LENGTH + " characters", Respoke.GUID_STRING_LENGTH == guid1.length());
        assertTrue("GUIDs should be " + Respoke.GUID_STRING_LENGTH + " characters", Respoke.GUID_STRING_LENGTH == guid2.length());
        assertTrue("GUIDs should be " + Respoke.GUID_STRING_LENGTH + " characters", Respoke.GUID_STRING_LENGTH == guid3.length());
        assertTrue("GUIDs should be " + Respoke.GUID_STRING_LENGTH + " characters", Respoke.GUID_STRING_LENGTH == guid4.length());

        assertFalse("Should create unique GUIDs every time", guid1.equals(guid2));
        assertFalse("Should create unique GUIDs every time", guid1.equals(guid3));
        assertFalse("Should create unique GUIDs every time", guid1.equals(guid4));

        assertFalse("Should create unique GUIDs every time", guid2.equals(guid3));
        assertFalse("Should create unique GUIDs every time", guid2.equals(guid4));

        assertFalse("Should create unique GUIDs every time", guid3.equals(guid4));
    }


}