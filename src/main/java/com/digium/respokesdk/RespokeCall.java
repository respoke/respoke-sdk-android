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
import java.util.Map;
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
    private org.webrtc.VideoRenderer.Callbacks localRender;
    private org.webrtc.VideoRenderer.Callbacks remoteRender;
    private boolean caller;
    private boolean waitingForAnswer;
    private JSONObject incomingSDP;
    private String sessionID;
    private String toConnection;
    public RespokeEndpoint endpoint;
    public boolean audioOnly;
    private final PCObserver pcObserver = new PCObserver();
    private final SDPObserver sdpObserver = new SDPObserver();
    private boolean videoSourceStopped;
    private MediaStream localStream;


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


    }

    public RespokeCall(RespokeSignalingChannel channel) {
        commonConstructor(channel);
    }


    public RespokeCall(RespokeSignalingChannel channel, RespokeEndpoint newEndpoint) {
        commonConstructor(channel);

        endpoint = newEndpoint;
    }


    public RespokeCall(RespokeSignalingChannel channel, JSONObject sdp, String newSessionID, String newConnectionID, RespokeEndpoint newEndpoint) {
        commonConstructor(channel);

        incomingSDP = sdp;
        sessionID = newSessionID;
        endpoint = newEndpoint;
        toConnection = newConnectionID;
    }


    public void commonConstructor(RespokeSignalingChannel channel) {
        signalingChannel = channel;
        iceServers = new ArrayList<PeerConnection.IceServer>();
        queuedLocalCandidates = new ArrayList<IceCandidate>();
        queuedRemoteCandidates = new ArrayList<IceCandidate>();
        sessionID = Respoke.makeGUID();

        peerConnectionFactory = new PeerConnectionFactory();

        RespokeSignalingChannel.Listener signalingChannelListener = signalingChannel.GetListener();
        if (null != signalingChannelListener) {
            signalingChannelListener.callCreated(this);
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
        // Workaround due to a bug in WebRTC for deallocating the video renderers, fixed in a later revision (7202?)
        muteVideo(true);

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

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        RespokeSignalingChannel.Listener signalingChannelListener = signalingChannel.GetListener();
        if (null != signalingChannelListener) {
            signalingChannelListener.callTerminated(this);
        }

        listenerReference = null;
        endpoint = null;
        signalingChannel = null;
    }


    public void startCall(Context context, GLSurfaceView glView, boolean isAudioOnly) {
        caller = true;
        waitingForAnswer = true;
        audioOnly = isAudioOnly;

        if (null != glView) {
            VideoRendererGui.setView(glView);
            remoteRender = VideoRendererGui.create(0, 0, 100, 100);
            localRender = VideoRendererGui.create(70, 5, 25, 25);
        }

        addLocalStreams(context);

        getTurnServerCredentials(new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Got TURN credentials");
                createOffer();
            }

            @Override
            public void onError(String errorMessage) {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onError(errorMessage, RespokeCall.this);
                }
            }
        });
    }


    public void answer(final Context context, Listener newListener, GLSurfaceView glView) {
        if (!caller) {
            listenerReference = new WeakReference<Listener>(newListener);

            if (null != glView) {
                VideoRendererGui.setView(glView);
                remoteRender = VideoRendererGui.create(0, 0, 100, 100);
                localRender = VideoRendererGui.create(70, 5, 25, 25);
            }

            getTurnServerCredentials(new Respoke.TaskCompletionListener() {
                @Override
                public void onSuccess() {
                    addLocalStreams(context);
                    processRemoteSDP();
                }

                @Override
                public void onError(String errorMessage) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onError(errorMessage, RespokeCall.this);
                    }
                }
            });
        }
    }


    public void hangup(boolean shouldSendHangupSignal) {
        if (shouldSendHangupSignal) {
            JSONObject data = null;
            try {
                data = new JSONObject("{'signalType':'bye','target':'call','version':'1.0'}");
                data.put("to", endpoint.getEndpointID());
                data.put("sessionId", sessionID);
                data.put("signalId", Respoke.makeGUID());

                signalingChannel.sendSignal(data, endpoint.getEndpointID(), new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        // Do nothing
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            listener.onError(errorMessage, RespokeCall.this);
                        }
                    }
                });
            } catch (JSONException e) {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onError("Error encoding signal to json", RespokeCall.this);
                }
            }
        }

        disconnect();
    }


    public void muteVideo(boolean mute) {
        if (!audioOnly && (null != localStream)) {
            for (MediaStreamTrack eachTrack : localStream.videoTracks) {
                eachTrack.setEnabled(!mute);
            }
        }
    }


    public void muteAudio(boolean mute) {
        if (null != localStream) {
            for (MediaStreamTrack eachTrack : localStream.audioTracks) {
                eachTrack.setEnabled(!mute);
            }
        }
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
        Listener listener = listenerReference.get();
        if (null != listener) {
            listener.onHangup(this);
        }

        disconnect();
    }


    public void answerReceived(JSONObject remoteSDP, String remoteConnection) {
        incomingSDP = remoteSDP;
        toConnection = remoteConnection;

        try {
            JSONObject signalData = new JSONObject("{'signalType':'connected','target':'call','version':'1.0'}");
            signalData.put("to", endpoint.getEndpointID());
            signalData.put("connectionId", toConnection);
            signalData.put("sessionId", sessionID);
            signalData.put("signalId", Respoke.makeGUID());

            signalingChannel.sendSignal(signalData, endpoint.getEndpointID(), new Respoke.TaskCompletionListener() {
                @Override
                public void onSuccess() {
                    processRemoteSDP();

                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onConnected(RespokeCall.this);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onError(errorMessage, RespokeCall.this);
                    }
                }
            });
        } catch (JSONException e) {
            Listener listener = listenerReference.get();
            if (null != listener) {
                listener.onError("Error encoding answer signal", this);
            }
        }
    }


    public void connectedReceived() {
        Listener listener = listenerReference.get();
        if (null != listener) {
            listener.onConnected(this);
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

                if (null != queuedRemoteCandidates) {
                    queuedRemoteCandidates.add(rtcCandidate);
                } else {
                    peerConnection.addIceCandidate(rtcCandidate);
                }

            } catch (JSONException e) {
                Log.d(TAG, "Error processing remote ice candidate data");
            }
        }
    }


    private void processRemoteSDP() {
        try {
            String type = incomingSDP.getString("type");
            String sdpString = incomingSDP.getString("sdp");

            SessionDescription sdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), preferISAC(sdpString));
            peerConnection.setRemoteDescription(this.sdpObserver, sdp);
        } catch (JSONException e) {
            Listener listener = listenerReference.get();
            if (null != listener) {
                listener.onError("Error processing remote SDP.", this);
            }
        }
    }


    private void getTurnServerCredentials(final Respoke.TaskCompletionListener completionListener) {
        // get TURN server credentials
        signalingChannel.sendRESTMessage("get", "/v1/turn", null, new RespokeSignalingChannel.RESTListener() {
            @Override
            public void onSuccess(Object response) {
                JSONObject jsonResponse = (JSONObject) response;

                try {
                    String username = jsonResponse.getString("username");
                    String password = jsonResponse.getString("password");
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


    private void addLocalStreams(Context context) {
        //TODO not sure about these - Jason
        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", audioOnly ? "false" : "true"));
        sdpMediaConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpMediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        //sdpMediaConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));

        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, sdpMediaConstraints, pcObserver);

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

        localStream.addTrack(peerConnectionFactory.createAudioTrack("ARDAMSa0", peerConnectionFactory.createAudioSource(sdpMediaConstraints)));

        peerConnection.addStream(localStream, new MediaConstraints());

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
                "OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo", audioOnly ? "false" : "true"));

        peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
    }


    private void updateVideoViewLayout() {
        //TODO
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

        @Override public void onError(){
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onError("PeerConnection failed", RespokeCall.this);
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

                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onError("Ice Connection failed!", RespokeCall.this);
                }

                disconnect();

                if (null != listener) {
                    listener.onHangup(RespokeCall.this);
                }
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
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onError("An invalid stream was added", RespokeCall.this);
                    }
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
            /*new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    throw new RuntimeException(
                            "AppRTC doesn't use data channels, but got: " + dc.label() +
                                    " anyway!");
                }
            });*/
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
                        JSONObject data = new JSONObject("{'target':'call','version':'1.0'}");
                        String type = sdp.type.toString().toLowerCase();
                        data.put("signalType", type);
                        data.put("to", endpoint.getEndpointID());
                        data.put("sessionId", sessionID);
                        data.put("signalId", Respoke.makeGUID());

                        JSONObject sdpJSON = new JSONObject();
                        sdpJSON.put("sdp", sdp.description);
                        sdpJSON.put("type", type);

                        data.put("sessionDescription", sdpJSON);

                        signalingChannel.sendSignal(data, endpoint.getEndpointID(), new Respoke.TaskCompletionListener() {
                            @Override
                            public void onSuccess() {
                                // Do nothing
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Listener listener = listenerReference.get();
                                if (null != listener) {
                                    listener.onError(errorMessage, RespokeCall.this);
                                }
                            }
                        });
                    } catch (JSONException e) {
                        Listener listener = listenerReference.get();
                        if (null != listener) {
                            listener.onError("Error encoding sdp", RespokeCall.this);
                        }
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
                                    "OfferToReceiveVideo", "true"));

                            peerConnection.createAnswer(SDPObserver.this, sdpMediaConstraints);
                        } else {
                            drainRemoteCandidates();
                        }
                    }
                }
            });
        }

        @Override public void onCreateFailure(final String error) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onError("createSDP error: " + error, RespokeCall.this);
                }
                }
            });
        }

        @Override public void onSetFailure(final String error) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                Listener listener = listenerReference.get();
                if (null != listener) {
                    listener.onError("setSDP error: " + error, RespokeCall.this);
                }
                }
            });
        }

        private void drainRemoteCandidates() {
            for (IceCandidate candidate : queuedRemoteCandidates) {
                peerConnection.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
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

            JSONObject signalData = new JSONObject("{'signalType':'iceCandidates','target':'call','version':'1.0'}");
            signalData.put("to", endpoint.getEndpointID());
            signalData.put("toConnection", toConnection);
            signalData.put("sessionId", sessionID);
            signalData.put("signalId", Respoke.makeGUID());
            signalData.put("iceCandidates", candidateArray);

            signalingChannel.sendSignal(signalData, endpoint.getEndpointID(), new Respoke.TaskCompletionListener() {
                @Override
                public void onSuccess() {
                    // Do nothing
                }

                @Override
                public void onError(String errorMessage) {
                    Listener listener = listenerReference.get();
                    if (null != listener) {
                        listener.onError(errorMessage, RespokeCall.this);
                    }
                }
            });
        } catch (JSONException e) {
            Listener listener = listenerReference.get();
            if (null != listener) {
                listener.onError("Unable to encode local candidate", this);
            }
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


}
