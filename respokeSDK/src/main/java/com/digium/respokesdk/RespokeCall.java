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
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 *  WebRTC Call including getUserMedia, path and codec negotation, and call state.
 */
public class RespokeCall {

    private final static String TAG = "RespokeCall";
    private WeakReference<Listener> listenerReference;
    private RespokeSignalingChannel signalingChannel;
    private ArrayList<PeerConnection.IceServer> iceServers;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoSource videoSource;
    private ArrayList<IceCandidate> queuedRemoteCandidates;
    private ArrayList<IceCandidate> queuedLocalCandidates;
    private Semaphore queuedRemoteCandidatesSemaphore;
    private org.webrtc.VideoRenderer.Callbacks localRender;
    private org.webrtc.VideoRenderer.Callbacks remoteRender;
    private boolean caller;
    private boolean waitingForAnswer;
    private JSONObject incomingSDP;

    /* Addressing and call identification fields */
    private String sessionID;
    private String toConnection;
    private String toEndpointId;
    /* Options are web, conference, did, or sip */
    private String toType;


    /* Used for direct connections */
    public RespokeEndpoint endpoint;
    public boolean audioOnly;
    public Date timestamp;
    private final PCObserver pcObserver = new PCObserver();
    private final SDPObserver sdpObserver = new SDPObserver();
    private boolean videoSourceStopped;
    private MediaStream localStream;
    private boolean directConnectionOnly;
    private RespokeDirectConnection directConnection;
    private boolean isHangingUp;


    /**
     *  A delegate protocol to notify the receiver of events occurring with the call
     */
    public interface Listener {


        /**
         *  Receive a notification that an error has occurred while on a call
         *
         *  @param errorMessage A human-readable description of the error.
         *  @param sender       The RespokeCall that experienced the error
         */
        public void onError(String errorMessage, RespokeCall sender);


        /**
         *  When on a call, receive notification the call has been hung up
         *
         *  @param sender The RespokeCall that has hung up
         */
        public void onHangup(RespokeCall sender);


        /**
         *  When on a call, receive remote media when it becomes available. This is what you will need to provide if you want
         *  to show the user the other party's video during a call.
         *
         *  @param sender The RespokeCall that has connected
         */
        public void onConnected(RespokeCall sender);


        /**
         *  This event is fired when the local end of the directConnection is available. It still will not be
         *  ready to send and receive messages until the 'open' event fires.
         *
         *  @param directConnection The direct connection object
         *  @param endpoint         The remote endpoint
         */
        public void directConnectionAvailable(RespokeDirectConnection directConnection, RespokeEndpoint endpoint);
    }


    public static boolean sdpHasVideo(JSONObject sdp) {
        boolean hasVideo = false;

        if (null != sdp) {
            try {
                String sdpString = sdp.getString("sdp");
                hasVideo = sdpString.contains("m=video");
            } catch (JSONException e) {
                // Bad SDP?
                Log.d(TAG, "ERROR: Incoming call appears to have an invalid SDP");
            }
        }

        return hasVideo;
    }

    public RespokeCall(RespokeSignalingChannel channel) {
        commonConstructor(channel);
    }

    public RespokeCall(RespokeSignalingChannel channel, String remoteEndpoint, String remoteType) {
        commonConstructor(channel);
        toEndpointId = remoteEndpoint;
        toType = remoteType;
    }

    /* Used for outbound calls */
    public RespokeCall(RespokeSignalingChannel channel, RespokeEndpoint newEndpoint, boolean directConnectionOnly) {
        commonConstructor(channel);

        endpoint = newEndpoint;
        toEndpointId = newEndpoint.getEndpointID();
        toType = "web";

        this.directConnectionOnly = directConnectionOnly;
    }


    /* Assuming it's typically used for inbound calls */
    public RespokeCall(RespokeSignalingChannel channel, JSONObject sdp, String newSessionID, String newConnectionID, String endpointID, String fromType, RespokeEndpoint newEndpoint, boolean directConnectionOnly, Date newTimestamp) {
        commonConstructor(channel);

        incomingSDP = sdp;
        sessionID = newSessionID;
        endpoint = newEndpoint;
        toEndpointId = endpointID;
        toType = fromType;

        if (fromType == null) {
            toType = "web";
        }

        toConnection = newConnectionID;
        this.directConnectionOnly = directConnectionOnly;
        timestamp = newTimestamp;
        audioOnly = !RespokeCall.sdpHasVideo(sdp);

        if ((directConnectionOnly) && (endpoint != null)) {
            actuallyAddDirectConnection();
        }
    }


    public void commonConstructor(RespokeSignalingChannel channel) {
        signalingChannel = channel;
        iceServers = new ArrayList<PeerConnection.IceServer>();
        queuedLocalCandidates = new ArrayList<IceCandidate>();
        queuedRemoteCandidates = new ArrayList<IceCandidate>();
        sessionID = Respoke.makeGUID();
        timestamp = new Date();
        queuedRemoteCandidatesSemaphore = new Semaphore(1); // Create a mutex for managing the remote candidates queue

        if (null != signalingChannel) {
            RespokeSignalingChannel.Listener signalingChannelListener = signalingChannel.GetListener();
            if (null != signalingChannelListener) {
                signalingChannelListener.callCreated(this);
            }
        }

        //TODO resign active handler?
    }


    public void setListener(Listener listener) {
        listenerReference = new WeakReference<Listener>(listener);
    }


    public String getSessionID() {
        return sessionID;
    }


    public void disconnect() {
        localStream = null;
        localRender = null;
        remoteRender = null;

        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (null != directConnection) {
            directConnection.setListener(null);
            directConnection = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        if (null != signalingChannel) {
            RespokeSignalingChannel.Listener signalingChannelListener = signalingChannel.GetListener();
            if (null != signalingChannelListener) {
                signalingChannelListener.callTerminated(this);
            }
        }

        listenerReference = null;
        endpoint = null;
        toEndpointId = null;
        toType = null;
        signalingChannel = null;
    }


    public void startCall(final Context context, GLSurfaceView glView, boolean isAudioOnly) {
        caller = true;
        waitingForAnswer = true;
        audioOnly = isAudioOnly;

        if (directConnectionOnly) {
            if (null == directConnection) {
                actuallyAddDirectConnection();
            }

            directConnectionDidAccept(context);
        } else {
            attachVideoRenderer(glView);

            getTurnServerCredentials(new Respoke.TaskCompletionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Got TURN credentials");
                    initializePeerConnection(context);
                    addLocalStreams(context);
                    createOffer();
                }

                @Override
                public void onError(String errorMessage) {
                    postErrorToListener(errorMessage);
                }
            });
        }
    }


    public void attachVideoRenderer(GLSurfaceView glView) {
        if (null != glView) {
            VideoRendererGui.setView(glView, new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "VideoRendererGui GL Context ready");
                }
            });

            remoteRender = VideoRendererGui.create(0, 0, 100, 100,
                    VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
            localRender = VideoRendererGui.create(70, 5, 25, 25,
                    VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
        }
    }


    public void answer(final Context context, Listener newListener) {
        if (!caller) {
            listenerReference = new WeakReference<Listener>(newListener);

            getTurnServerCredentials(new Respoke.TaskCompletionListener() {
                @Override
                public void onSuccess() {
                    initializePeerConnection(context);
                    addLocalStreams(context);
                    processRemoteSDP();
                }

                @Override
                public void onError(String errorMessage) {
                    postErrorToListener(errorMessage);
                }
            });
        }
    }


    public void hangup(boolean shouldSendHangupSignal) {
        if (!isHangingUp) {
            isHangingUp = true;

            if (shouldSendHangupSignal) {

                try {
                    JSONObject data = new JSONObject("{'signalType':'bye','version':'1.0'}");
                    data.put("target", directConnectionOnly ? "directConnection" : "call");
                    data.put("sessionId", sessionID);
                    data.put("signalId", Respoke.makeGUID());

                    // Keep a second reference to the listener since the disconnect method will clear it before the success handler is fired
                    final WeakReference<Listener> hangupListener = listenerReference;

                    signalingChannel.sendSignal(data, toEndpointId, toConnection, toType, new Respoke.TaskCompletionListener() {
                        @Override
                        public void onSuccess() {
                            if (null != hangupListener) {
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    public void run() {
                                        Listener listener = hangupListener.get();
                                        if (null != listener) {
                                            listener.onHangup(RespokeCall.this);
                                        }
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            postErrorToListener(errorMessage);
                        }
                    });
                } catch (JSONException e) {
                    postErrorToListener("Error encoding signal to json");
                }
            }

            disconnect();
        }
    }


    public void muteVideo(boolean mute) {
        if (!audioOnly && (null != localStream)) {
            for (MediaStreamTrack eachTrack : localStream.videoTracks) {
                eachTrack.setEnabled(!mute);
            }
        }
    }


    public boolean videoIsMuted() {
        boolean isMuted = true;

        if (!audioOnly && (null != localStream)) {
            for (MediaStreamTrack eachTrack : localStream.videoTracks) {
                if (eachTrack.enabled()) {
                    isMuted = false;
                }
            }
        }

        return isMuted;
    }


    public void muteAudio(boolean mute) {
        if (null != localStream) {
            for (MediaStreamTrack eachTrack : localStream.audioTracks) {
                eachTrack.setEnabled(!mute);
            }
        }
    }


    public boolean audioIsMuted() {
        boolean isMuted = true;

        if (null != localStream) {
            for (MediaStreamTrack eachTrack : localStream.audioTracks) {
                if (eachTrack.enabled()) {
                    isMuted = false;
                }
            }
        }

        return isMuted;
    }


    public void pause() {
        if (videoSource != null) {
            videoSource.stop();
            videoSourceStopped = true;
        }
    }


    public void resume() {
        if (videoSource != null && videoSourceStopped) {
            videoSource.restart();
        }
    }


    public void hangupReceived() {
        if (!isHangingUp) {
            isHangingUp = true;

            if (null != listenerReference) {
                // Disconnect will clear the listenerReference, so grab a reference to the
                // listener while it's still alive since the listener will be notified in a
                // different (UI) thread
                final Listener listener = listenerReference.get();

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        if (null != listener) {
                            listener.onHangup(RespokeCall.this);
                        }
                    }
                });
            }

            disconnect();
        }
    }


    public void answerReceived(JSONObject remoteSDP, String remoteConnection) {
        incomingSDP = remoteSDP;
        toConnection = remoteConnection;

        try {
            JSONObject signalData = new JSONObject("{'signalType':'connected','version':'1.0'}");
            signalData.put("target", directConnectionOnly ? "directConnection" : "call");
            signalData.put("connectionId", toConnection);
            signalData.put("sessionId", sessionID);
            signalData.put("signalId", Respoke.makeGUID());

            signalingChannel.sendSignal(signalData, toEndpointId, toConnection, toType, new Respoke.TaskCompletionListener() {
                @Override
                public void onSuccess() {
                    processRemoteSDP();

                    if (null != listenerReference) {
                        final Listener listener = listenerReference.get();
                        if (null != listener) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                public void run() {
                                    listener.onConnected(RespokeCall.this);
                                }
                            });
                        }
                    }
                }

                @Override
                public void onError(final String errorMessage) {
                    postErrorToListener(errorMessage);
                }
            });
        } catch (JSONException e) {
            postErrorToListener("Error encoding answer signal");
        }
    }


    public void connectedReceived() {
        if (null != listenerReference) {
            final Listener listener = listenerReference.get();
            if (null != listener) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        listener.onConnected(RespokeCall.this);
                    }
                });
            }
        }
    }


    public void iceCandidatesReceived(JSONArray candidates) {
        for (int ii = 0; ii < candidates.length(); ii++) {
            try {
                JSONObject eachCandidate = (JSONObject) candidates.get(ii);
                String mid = eachCandidate.getString("sdpMid");
                int sdpLineIndex = eachCandidate.getInt("sdpMLineIndex");
                String sdp = eachCandidate.getString("candidate");

                IceCandidate rtcCandidate = new IceCandidate(mid, sdpLineIndex, sdp);

                try {
                    // Start critical block
                    queuedRemoteCandidatesSemaphore.acquire();

                    if (null != queuedRemoteCandidates) {
                        queuedRemoteCandidates.add(rtcCandidate);
                    } else {
                        peerConnection.addIceCandidate(rtcCandidate);
                    }

                    // End critical block
                    queuedRemoteCandidatesSemaphore.release();
                } catch (InterruptedException e) {
                    Log.d(TAG, "Error with remote candidates semaphore");
                }

            } catch (JSONException e) {
                Log.d(TAG, "Error processing remote ice candidate data");
            }
        }
    }


    public boolean isCaller() {
        return caller;
    }


    public PeerConnection getPeerConnection() {
        return peerConnection;
    }


    private void processRemoteSDP() {
        try {
            String type = incomingSDP.getString("type");
            String sdpString = incomingSDP.getString("sdp");

            SessionDescription sdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), preferISAC(sdpString));
            peerConnection.setRemoteDescription(this.sdpObserver, sdp);
        } catch (JSONException e) {
            postErrorToListener("Error processing remote SDP.");
        }
    }


    private void getTurnServerCredentials(final Respoke.TaskCompletionListener completionListener) {
        // get TURN server credentials
        signalingChannel.sendRESTMessage("get", "/v1/turn", null, new RespokeSignalingChannel.RESTListener() {
            @Override
            public void onSuccess(Object response) {
                JSONObject jsonResponse = (JSONObject) response;
                String username = "";
                String password = "";

                try {
                    username = jsonResponse.getString("username");
                    password = jsonResponse.getString("password");
                } catch (JSONException e) {
                    // No auth info? Must be accessible without TURN
                }

                try {
                    JSONArray uris = (JSONArray) jsonResponse.get("uris");

                    for (int ii = 0; ii < uris.length(); ii++) {
                        String eachUri = uris.getString(ii);

                        PeerConnection.IceServer server = new PeerConnection.IceServer(eachUri, username, password);
                        iceServers.add(server);
                    }

                    if (iceServers.size() > 0) {
                        completionListener.onSuccess();
                    } else {
                        completionListener.onError("No ICE servers were found");
                    }
                } catch (JSONException e) {
                    completionListener.onError("Unexpected response from server");
                }
            }

            @Override
            public void onError(String errorMessage) {
                completionListener.onError(errorMessage);
            }
        });
    }


    private void initializePeerConnection(Context context) {
        PeerConnectionFactory.initializeFieldTrials(null);

        if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, true, true, VideoRendererGui.getEGLContext())) {
            Log.d(TAG, "Failed to initializeAndroidGlobals");
        }

        peerConnectionFactory = new PeerConnectionFactory();

        if ((null == remoteRender) && (null == localRender)) {
            // If the client application did not provide UI elements on which to render video, force this to be an audio call
            audioOnly = true;
        }

        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", directConnectionOnly ? "false" : "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", (directConnectionOnly || audioOnly) ? "false" : "true"));
        sdpMediaConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpMediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, sdpMediaConstraints, pcObserver);
    }


    private void addLocalStreams(Context context) {
        AudioManager audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        // TODO(fischman): figure out how to do this Right(tm) and remove the suppression.
        @SuppressWarnings("deprecation")
        boolean isWiredHeadsetOn = audioManager.isWiredHeadsetOn();
        audioManager.setMode(isWiredHeadsetOn ? AudioManager.MODE_IN_CALL : AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(!isWiredHeadsetOn);

        localStream = peerConnectionFactory.createLocalMediaStream("ARDAMS");

        if (!audioOnly) {
            VideoCapturer capturer = getVideoCapturer();
            MediaConstraints videoConstraints = new MediaConstraints();
            videoSource = peerConnectionFactory.createVideoSource(capturer, videoConstraints);
            VideoTrack videoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource);
            videoTrack.addRenderer(new VideoRenderer(localRender));
            localStream.addTrack(videoTrack);
        }

        localStream.addTrack(peerConnectionFactory.createAudioTrack("ARDAMSa0", peerConnectionFactory.createAudioSource(new MediaConstraints())));

        peerConnection.addStream(localStream);
    }


    // Cycle through likely device names for the camera and return the first
    // capturer that works, or crash if none do.
    private VideoCapturer getVideoCapturer() {
        String[] cameraFacing = { "front", "back" };
        int[] cameraIndex = { 0, 1 };
        int[] cameraOrientation = { 0, 90, 180, 270 };
        for (String facing : cameraFacing) {
            for (int index : cameraIndex) {
                for (int orientation : cameraOrientation) {
                    String name = "Camera " + index + ", Facing " + facing +
                            ", Orientation " + orientation;
                    VideoCapturer capturer = VideoCapturer.create(name);
                    if (capturer != null) {
                        //logAndToast("Using camera: " + name);
                        Log.d(TAG, "Using camera: " + name);
                        return capturer;
                    }
                }
            }
        }
        throw new RuntimeException("Failed to open capturer");
    }


    private void createOffer() {
        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio", directConnectionOnly ? "false" : "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", (directConnectionOnly || audioOnly) ? "false" : "true"));

        peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
    }


    private void updateVideoViewLayout() {
        //TODO
    }


    private void actuallyAddDirectConnection() {
        if ((null != directConnection) && (directConnection.isActive())) {
            // There is already an active direct connection, so ignore this
        } else {
            directConnection = new RespokeDirectConnection(this);
            endpoint.setDirectConnection(directConnection);

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    if (null != listenerReference) {
                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            listener.directConnectionAvailable(directConnection, endpoint);
                        }
                    }
                }
            });

            if ((null != directConnection) && !caller && (null != signalingChannel)) {
                RespokeSignalingChannel.Listener signalingChannelListener = signalingChannel.GetListener();
                if (null != signalingChannelListener) {
                    // Inform the client that a remote endpoint is attempting to open a direct connection
                    signalingChannelListener.directConnectionAvailable(directConnection, endpoint);
                }
            }
        }
    }

    public void directConnectionDidAccept(final Context context) {
        getTurnServerCredentials(new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                initializePeerConnection(context);

                if (caller) {
                    directConnection.createDataChannel();
                    createOffer();
                } else {
                    processRemoteSDP();
                }
            }

            @Override
            public void onError(String errorMessage) {
                postErrorToListener(errorMessage);
            }
        });
    }


    public void directConnectionDidOpen(RespokeDirectConnection sender) {

    }


    public void directConnectionDidClose(RespokeDirectConnection sender) {
        if (sender == directConnection) {
            directConnection = null;

            if (null != endpoint) {
                endpoint.setDirectConnection(null);
            }
        }
    }


    // Implementation detail: observe ICE & stream changes and react accordingly.
    private class PCObserver implements PeerConnection.Observer {
        @Override public void onIceCandidate(final IceCandidate candidate){
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                Log.d(TAG, "onIceCandidate");

                if (caller && waitingForAnswer) {
                    queuedLocalCandidates.add(candidate);
                } else {
                    sendLocalCandidate(candidate);
                }
                }
            });
        }

        @Override public void onSignalingChange(
            PeerConnection.SignalingState newState) {
        }

        @Override public void onIceConnectionChange(
                PeerConnection.IceConnectionState newState) {
            if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                Log.d(TAG, "ICE Connection connected");
            } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                Log.d(TAG, "ICE Connection FAILED");

                if (null != listenerReference) {
                    // Disconnect will clear the listenerReference, so grab a reference to the
                    // listener while it's still alive since the listener will be notified in a
                    // different (UI) thread
                    final Listener listener = listenerReference.get();

                    if (null != listener) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                listener.onError("ICE Connection failed!", RespokeCall.this);
                                listener.onHangup(RespokeCall.this);
                            }
                        });
                    }
                }

                disconnect();
            } else {
                Log.d(TAG, "ICE Connection state: " + newState.toString());
            }
        }

        @Override public void onIceGatheringChange(
                PeerConnection.IceGatheringState newState) {
        }

        @Override public void onAddStream(final MediaStream stream){
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    if (stream.audioTracks.size() <= 1 && stream.videoTracks.size() <= 1) {
                        if (stream.videoTracks.size() == 1) {
                            stream.videoTracks.get(0).addRenderer(
                                    new VideoRenderer(remoteRender));
                        }
                    } else {
                        postErrorToListener("An invalid stream was added");
                    }
                }
            });
        }

        @Override public void onRemoveStream(final MediaStream stream){
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    stream.videoTracks.get(0).dispose();
                }
            });
        }

        @Override public void onDataChannel(final DataChannel dc) {
            if (null != directConnection) {
                directConnection.peerConnectionDidOpenDataChannel(dc);
            } else {
                Log.d(TAG, "Direct connection opened, but no object to handle it!");
            }
        }

        @Override public void onRenegotiationNeeded() {
            // No need to do anything; AppRTC follows a pre-agreed-upon
            // signaling/negotiation protocol.
        }
    }


    // Implementation detail: handle offer creation/signaling and answer setting,
    // as well as adding remote ICE candidates once the answer SDP is set.
    private class SDPObserver implements SdpObserver {

        @Override public void onCreateSuccess(final SessionDescription origSdp) {
            //abortUnless(localSdp == null, "multiple SDP create?!?");
            final SessionDescription sdp = new SessionDescription(
                    origSdp.type, preferISAC(origSdp.description));

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    Log.d(TAG, "onSuccess(Create SDP)");
                    peerConnection.setLocalDescription(sdpObserver, sdp);

                    try {
                        JSONObject data = new JSONObject("{'version':'1.0'}");
                        data.put("target", directConnectionOnly ? "directConnection" : "call");
                        String type = sdp.type.toString().toLowerCase();
                        data.put("signalType", type);
                        data.put("sessionId", sessionID);
                        data.put("signalId", Respoke.makeGUID());

                        JSONObject sdpJSON = new JSONObject();
                        sdpJSON.put("sdp", sdp.description);
                        sdpJSON.put("type", type);

                        data.put("sessionDescription", sdpJSON);

                        signalingChannel.sendSignal(data, toEndpointId, toConnection, toType, new Respoke.TaskCompletionListener() {
                            @Override
                            public void onSuccess() {
                                // Do nothing
                            }

                            @Override
                            public void onError(String errorMessage) {
                                postErrorToListener(errorMessage);
                            }
                        });
                    } catch (JSONException e) {
                        postErrorToListener("Error encoding sdp");
                    }
                }
            });
        }


        @Override public void onSetSuccess() {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    Log.d(TAG, "onSuccess(Set SDP)");
                    if (caller) {
                        if (peerConnection.getRemoteDescription() != null) {
                            // We've set our local offer and received & set the remote
                            // answer, so drain candidates.
                            waitingForAnswer = false;
                            drainRemoteCandidates();
                            drainLocalCandidates();
                        }
                    } else {
                        if (peerConnection.getLocalDescription() == null) {
                            // We just set the remote offer, time to create our answer.
                            MediaConstraints sdpMediaConstraints = new MediaConstraints();
                            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                                    "OfferToReceiveAudio", "true"));
                            sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                                    "OfferToReceiveVideo", audioOnly ? "false" : "true"));

                            peerConnection.createAnswer(SDPObserver.this, sdpMediaConstraints);
                        } else {
                            drainRemoteCandidates();
                        }
                    }
                }
            });
        }

        @Override public void onCreateFailure(final String error) {
            postErrorToListener("createSDP error: " + error);
        }

        @Override public void onSetFailure(final String error) {
            postErrorToListener("setSDP error: " + error);
        }

        private void drainRemoteCandidates() {
            try {
                // Start critical block
                queuedRemoteCandidatesSemaphore.acquire();

                for (IceCandidate candidate : queuedRemoteCandidates) {
                    peerConnection.addIceCandidate(candidate);
                }
                queuedRemoteCandidates = null;

                // End critical block
                queuedRemoteCandidatesSemaphore.release();
            } catch (InterruptedException e) {
                Log.d(TAG, "Error with remote candidates semaphore");
            }
        }

        private void drainLocalCandidates() {
            for (IceCandidate candidate : queuedLocalCandidates) {
                sendLocalCandidate(candidate);
            }
        }
    }


    private void sendLocalCandidate(IceCandidate candidate) {
        JSONObject candidateDict = new JSONObject();
        try {
            candidateDict.put("sdpMLineIndex", candidate.sdpMLineIndex);
            candidateDict.put("sdpMid", candidate.sdpMid);
            candidateDict.put("candidate", candidate.sdp);

            JSONArray candidateArray = new JSONArray();
            candidateArray.put(candidateDict);

            JSONObject signalData = new JSONObject("{'signalType':'iceCandidates','version':'1.0'}");
            signalData.put("target", directConnectionOnly ? "directConnection" : "call");
            signalData.put("sessionId", sessionID);
            signalData.put("signalId", Respoke.makeGUID());
            signalData.put("iceCandidates", candidateArray);

            signalingChannel.sendSignal(signalData, toEndpointId, toConnection, toType, new Respoke.TaskCompletionListener() {
                @Override
                public void onSuccess() {
                    // Do nothing
                }

                @Override
                public void onError(String errorMessage) {
                    postErrorToListener(errorMessage);
                }
            });
        } catch (JSONException e) {
            postErrorToListener("Unable to encode local candidate");
        }
    }


    // Mangle SDP to prefer ISAC/16000 over any other audio codec.
    private static String preferISAC(String sdpDescription) {
        String[] lines = sdpDescription.split("\r\n");
        int mLineIndex = -1;
        String isac16kRtpMap = null;
        Pattern isac16kPattern =
                Pattern.compile("^a=rtpmap:(\\d+) ISAC/16000[\r]?$");
        for (int i = 0;
             (i < lines.length) && (mLineIndex == -1 || isac16kRtpMap == null);
             ++i) {
            if (lines[i].startsWith("m=audio ")) {
                mLineIndex = i;
                continue;
            }
            Matcher isac16kMatcher = isac16kPattern.matcher(lines[i]);
            if (isac16kMatcher.matches()) {
                isac16kRtpMap = isac16kMatcher.group(1);
                //continue;
            }
        }
        if (mLineIndex == -1) {
            //Log.d(TAG, "No m=audio line, so can't prefer iSAC");
            return sdpDescription;
        }
        if (isac16kRtpMap == null) {
            //Log.d(TAG, "No ISAC/16000 line, so can't prefer iSAC");
            return sdpDescription;
        }
        String[] origMLineParts = lines[mLineIndex].split(" ");
        StringBuilder newMLine = new StringBuilder();
        int origPartIndex = 0;
        // Format is: m=<media> <port> <proto> <fmt> ...
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(origMLineParts[origPartIndex++]).append(" ");
        newMLine.append(isac16kRtpMap);
        for (; origPartIndex < origMLineParts.length; ++origPartIndex) {
            if (!origMLineParts[origPartIndex].equals(isac16kRtpMap)) {
                newMLine.append(" ").append(origMLineParts[origPartIndex]);
            }
        }
        lines[mLineIndex] = newMLine.toString();
        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }
        return newSdpDescription.toString();
    }


    private void postErrorToListener(final String errorMessage) {
        // All listener methods should be called from the UI thread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                if (null != listenerReference) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onError(errorMessage, RespokeCall.this);
                    }
                }
            }
        });
    }


}
