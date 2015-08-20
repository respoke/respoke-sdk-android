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

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.PeerConnection;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * A direct connection via RTCDataChannel, including state and path negotation.
 */
public class RespokeDirectConnection implements org.webrtc.DataChannel.Observer {

    private WeakReference<Listener> listenerReference;
    private WeakReference<RespokeCall> callReference;
    private DataChannel dataChannel;


    /**
     *  A listener interface to notify the receiver of events occurring with the direct connection
     */
    public interface Listener {

        /**
         *  The direct connection setup has begun. This does NOT mean it's ready to send messages yet. Listen to
         *  onOpen for that notification.
         *
         *  @param sender  The direct connection for which the event occurred
         */
        public void onStart(RespokeDirectConnection sender);

        /**
         *  Called when the direct connection is opened.
         *
         *  @param sender  The direct connection for which the event occurred
         */
        public void onOpen(RespokeDirectConnection sender);

        /**
         *  Called when the direct connection is closed.
         *
         *  @param sender  The direct connection for which the event occurred
         */
        public void onClose(RespokeDirectConnection sender);

        /**
         *  Called when a message is received over the direct connection.
         *
         *  @param sender  The direct connection for which the event occurred
         */
        public void onMessage(String message, RespokeDirectConnection sender);

    }


    /**
     *  The constructor for this class
     *
     *  @param call  The call instance with which this direct connection is associated
     */
    public RespokeDirectConnection(RespokeCall call) {
        callReference = new WeakReference<RespokeCall>(call);
    }


    /**
     *  Set a receiver for the Listener interface
     *
     *  @param listener  The new receiver for events from the Listener interface for this instance
     */
    public void setListener(Listener listener) {
        if (null != listener) {
            listenerReference = new WeakReference<Listener>(listener);
        } else {
            listenerReference = null;
        }
    }


    /**
     *  Accept the direct connection and start the process of obtaining media. 
     *
     *  @param context       An application context with which to access system resources
     */
    public void accept(Context context) {
        if (null != callReference) {
            RespokeCall call = callReference.get();
            if (null != call) {
                call.directConnectionDidAccept(context);
            }
        }
    }


    /**
     *  Indicate whether a datachannel is being setup or is in progress.
     *
     *  @return  True the direct connection is active, false otherwise
     */
    public boolean isActive() {
        return ((null != dataChannel) && (dataChannel.state() == DataChannel.State.OPEN));
    }


    /**
     *  Get the call object associated with this direct connection
     *
     *  @return  The call instance
     */
    public RespokeCall getCall() {
        if (null != callReference) {
            return callReference.get();
        } else {
            return null;
        }
    }


    /**
     *  Send a message to the remote client through the direct connection.
     *
     *  @param message             The message to send
     *  @param completionListener  A listener to receive a notification on the success of the asynchronous operation
     */
    public void sendMessage(String message, final Respoke.TaskCompletionListener completionListener) {
        if (isActive()) {
            JSONObject jsonMessage = new JSONObject();
            try {
                jsonMessage.put("message", message);
                byte[] rawMessage = jsonMessage.toString().getBytes(Charset.forName("UTF-8"));
                ByteBuffer directData = ByteBuffer.allocateDirect(rawMessage.length);
                directData.put(rawMessage);
                directData.flip();
                DataChannel.Buffer data = new DataChannel.Buffer(directData, false);

                if (dataChannel.send(data)) {
                    Respoke.postTaskSuccess(completionListener);
                } else {
                    Respoke.postTaskError(completionListener, "Error sending message");
                }
            } catch (JSONException e) {
                Respoke.postTaskError(completionListener, "Unable to encode message to JSON");
            }
        } else {
            Respoke.postTaskError(completionListener, "DataChannel not in an open state");
        }
    }


    /**
     *  Establish a new direct connection instance with the peer connection for the call. This is used internally to the SDK and should not be called directly by your client application.
     */
    public void createDataChannel() {
        if (null != callReference) {
            RespokeCall call = callReference.get();
            if (null != call) {
                PeerConnection peerConnection = call.getPeerConnection();
                dataChannel = peerConnection.createDataChannel("respokeDataChannel", new DataChannel.Init());
                dataChannel.registerObserver(this);
            }
        }
    }


    /**
     *  Notify the direct connection instance that the peer connection has opened the specified data channel
     *
     *  @param dataChannel    The DataChannel that has opened
     */
    public void peerConnectionDidOpenDataChannel(DataChannel newDataChannel) {
        if (null != dataChannel) {
            // Replacing the previous connection, so disable observer messages from the old instance
            dataChannel.unregisterObserver();
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    if (null != listenerReference) {
                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            listener.onStart(RespokeDirectConnection.this);
                        }
                    }
                }
            });
        }

        dataChannel = newDataChannel;
        newDataChannel.registerObserver(this);
    }


    // org.webrtc.DataChannel.Observer methods


    public void onStateChange() {
        switch (dataChannel.state()) {
            case CONNECTING:
                break;

            case OPEN: {
                    if (null != callReference) {
                        RespokeCall call = callReference.get();
                        if (null != call) {
                            call.directConnectionDidOpen(this);
                        }
                    }

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        if (null != listenerReference) {
                            Listener listener = listenerReference.get();
                            if (null != listener) {
                                listener.onOpen(RespokeDirectConnection.this);
                            }
                        }
                    }
                });
                }
                break;

            case CLOSING:
                break;

            case CLOSED: {
                    if (null != callReference) {
                        RespokeCall call = callReference.get();
                        if (null != call) {
                            call.directConnectionDidClose(this);
                        }
                    }

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            if (null != listenerReference) {
                                Listener listener = listenerReference.get();
                                if (null != listener) {
                                    listener.onClose(RespokeDirectConnection.this);
                                }
                            }
                        }
                    });
                }
                break;
        }
    }


    public void onMessage(org.webrtc.DataChannel.Buffer buffer) {
        if (buffer.binary) {
            // TODO
        } else {
            Charset charset = Charset.forName("UTF-8");
            CharsetDecoder decoder = charset.newDecoder();
            try {
                String message = decoder.decode( buffer.data ).toString();

                try {
                    JSONObject jsonMessage = new JSONObject(message);
                    final String messageText = jsonMessage.getString("message");

                    if (null != messageText) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                if (null != listenerReference) {
                                    Listener listener = listenerReference.get();
                                    if (null != listener) {
                                        listener.onMessage(messageText, RespokeDirectConnection.this);
                                    }
                                }
                            }
                        });
                    }
                } catch (JSONException e) {
                    // If it is not valid json, ignore the message
                }
            } catch (CharacterCodingException e) {
                // If the message can not be decoded, ignore it
            }
        }
    }


}
