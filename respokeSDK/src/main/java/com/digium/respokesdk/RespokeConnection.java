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
    private RespokeSignalingChannel signalingChannel;
    private WeakReference<RespokeEndpoint> endpointReference;
    public Object presence;

    public RespokeConnection(RespokeSignalingChannel channel, String newConnectionID, RespokeEndpoint newEndpoint) {
        signalingChannel = channel;
        connectionID = newConnectionID;
        endpointReference = new WeakReference<RespokeEndpoint>(newEndpoint);
    }


    public RespokeEndpoint getEndpoint() {
        return endpointReference.get();
    }
}
