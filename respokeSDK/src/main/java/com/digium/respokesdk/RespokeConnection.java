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

import java.lang.ref.WeakReference;

/**
 *  Represents remote Connections which belong to an Endpoint. An Endpoint can be authenticated from multiple devices,
 *  browsers, or tabs. Each of these separate authentications is a Connection. The client can interact
 *  with connections by calling them or sending them messages.
 */
public class RespokeConnection {

    public String connectionID;
    private WeakReference<RespokeEndpoint> endpointReference;
    public Object presence;


    /**
     *  The constructor for this class
     *
     *  @param newConnectionID  The ID for this connection
     *  @param newEndpoint      The endpoint to which this connection belongs
     */
    public RespokeConnection(String newConnectionID, RespokeEndpoint newEndpoint) {
        connectionID = newConnectionID;
        endpointReference = new WeakReference<RespokeEndpoint>(newEndpoint);
    }


    /**
     *  Get the endpoint to which this connection belongs
     *
     *  @return  The endpoint instance
     */
    public RespokeEndpoint getEndpoint() {
        return endpointReference.get();
    }
}
