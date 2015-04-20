/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdktest.unit;

import android.app.Application;
import android.test.ApplicationTestCase;

import com.digium.respokesdk.Respoke;


public class RespokeTests extends ApplicationTestCase<Application> {

    public RespokeTests() {
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