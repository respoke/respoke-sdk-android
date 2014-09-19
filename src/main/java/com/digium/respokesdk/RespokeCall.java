package com.digium.respokesdk;

import android.content.Context;
import android.media.AudioManager;
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
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jasonadams on 9/14/14.
 */
public class RespokeCall {

    private final static String TAG = "RespokeCall";
    public RespokeCallDelegate delegate;
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
    private RespokeEndpoint endpoint;
    private boolean audioOnly;
    private static boolean factoryStaticInitialized;
    private final PCObserver pcObserver = new PCObserver();
    private final SDPObserver sdpObserver = new SDPObserver();


    public RespokeCall(RespokeSignalingChannel channel) {
        commonConstructor(channel);
    }


    public RespokeCall(RespokeSignalingChannel channel, RespokeEndpoint newEndpoint, boolean newAudioOnly) {
        commonConstructor(channel);

        endpoint = newEndpoint;
        audioOnly = newAudioOnly;
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
        sessionID = Respoke.makeGUID();

        if (!factoryStaticInitialized) {
            PeerConnectionFactory.initializeAndroidGlobals(this, true, true);
            factoryStaticInitialized = true;
        }

        peerConnectionFactory = new PeerConnectionFactory();

        signalingChannel.delegate.callCreated(this);

        //TODO resign active handler?
    }


    public String getSessionID() {
        return sessionID;
    }


    public void disconnect() {
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

        signalingChannel.delegate.callTerminated(this);
    }


    public void startCall(final Context context) {
        caller = true;
        waitingForAnswer = true;

        getTurnServerCredentials(new RespokeTaskCompletionDelegate() {
            @Override
            public void onSuccess() {
                addLocalStreams(context);
                createOffer();
            }

            @Override
            public void onError(String errorMessage) {
                delegate.onError(errorMessage, RespokeCall.this);
            }
        });
    }


    public void answer(final Context context) {
        if (!caller) {
            getTurnServerCredentials(new RespokeTaskCompletionDelegate() {
                @Override
                public void onSuccess() {
                    addLocalStreams(context);
                    processRemoteSDP();
                }

                @Override
                public void onError(String errorMessage) {
                    delegate.onError(errorMessage, RespokeCall.this);
                }
            });
        }
    }


    public void hangup(boolean shouldSendHangupSignal) {
        if (shouldSendHangupSignal) {
            JSONObject data = null;
            try {
                data = new JSONObject("{'SignalType':'hangup','target':'call'}");
                data.put("to", endpoint.getEndpointID());
                data.put("sessionId", sessionID);
                data.put("signalId", Respoke.makeGUID());

                signalingChannel.sendSignal(data, endpoint.getEndpointID(), new RespokeTaskCompletionDelegate() {
                    @Override
                    public void onSuccess() {
                        // Do nothing
                    }

                    @Override
                    public void onError(String errorMessage) {
                        delegate.onError(errorMessage, RespokeCall.this);
                    }
                });
            } catch (JSONException e) {
                delegate.onError("Error encoding signal to json", RespokeCall.this);
            }
        }

        disconnect();
    }


    public void muteVideo(boolean mute) {
        // TODO
    }


    public void muteAudio(boolean mute) {
        // TODO
    }


    public void hangupReceived() {
        disconnect();
        delegate.onHangup(this);
    }


    public void answerReceived(JSONObject remoteSDP, String remoteConnection) {
        incomingSDP = remoteSDP;
        toConnection = remoteConnection;

        try {
            JSONObject signalData = new JSONObject("{'signalType':'connected','target':'call'}");
            signalData.put("to", endpoint.getEndpointID());
            signalData.put("toConnection", toConnection);
            signalData.put("sessionId", sessionID);
            signalData.put("signalId", Respoke.makeGUID());

            signalingChannel.sendSignal(signalData, endpoint.getEndpointID(), new RespokeTaskCompletionDelegate() {
                @Override
                public void onSuccess() {
                    processRemoteSDP();
                    delegate.onConnected(RespokeCall.this);
                }

                @Override
                public void onError(String errorMessage) {
                    delegate.onError(errorMessage, RespokeCall.this);
                }
            });
        } catch (JSONException e) {
            delegate.onError("Error encoding answer signal", this);
        }
    }


    public void connectedReceived() {
        delegate.onConnected(this);
    }


    public void iceCandidatesReceived(JSONArray candidates) {
        //TODO
    }


    private void processRemoteSDP() {
        //TODO
    }


    private void getTurnServerCredentials(final RespokeTaskCompletionDelegate completionDelegate) {
        // get TURN server credentials
        signalingChannel.sendRESTMessage("get", "/v1/turn", null, new RespokeSignalingChannelRESTDelegate() {
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
                        completionDelegate.onSuccess();
                    } else {
                        completionDelegate.onError("No ICE servers were found");
                    }
                } catch (JSONException e) {
                    completionDelegate.onError("Unexpected response from server");
                }
            }

            @Override
            public void onError(String errorMessage) {
                completionDelegate.onError(errorMessage);
            }
        });
    }


    private void addLocalStreams(Context context) {
        queuedRemoteCandidates = new ArrayList<IceCandidate>();

        //TODO not sure about these - Jason
        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", audioOnly ? "false" : "true"));
        //sdpMediaConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        //sdpMediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        sdpMediaConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));

        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, sdpMediaConstraints, pcObserver);

        AudioManager audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        // TODO(fischman): figure out how to do this Right(tm) and remove the suppression.
        @SuppressWarnings("deprecation")
        boolean isWiredHeadsetOn = audioManager.isWiredHeadsetOn();
        audioManager.setMode(isWiredHeadsetOn ? AudioManager.MODE_IN_CALL : AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(!isWiredHeadsetOn);

        MediaStream lMS = peerConnectionFactory.createLocalMediaStream("ARDAMS");

        if (!audioOnly) {
            VideoCapturer capturer = getVideoCapturer();
            videoSource = peerConnectionFactory.createVideoSource( capturer, sdpMediaConstraints);
            VideoTrack videoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource);
            videoTrack.addRenderer(new VideoRenderer(localRender));
            lMS.addTrack(videoTrack);
        }

        lMS.addTrack(peerConnectionFactory.createAudioTrack("ARDAMSa0", peerConnectionFactory.createAudioSource(sdpMediaConstraints)));

        peerConnection.addStream(lMS, new MediaConstraints());

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
                "OfferToReceiveVideo", "true"));

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
/*                    JSONObject json = new JSONObject();
                    jsonPut(json, "type", "candidate");
                    jsonPut(json, "label", candidate.sdpMLineIndex);
                    jsonPut(json, "id", candidate.sdpMid);
                    jsonPut(json, "candidate", candidate.sdp);
                    sendMessage(json);*/
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
                    delegate.onError("PeerConnection failed", RespokeCall.this);
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
            } else {
                delegate.onError("Ice Connection failed!", RespokeCall.this);
                disconnect();
                delegate.onHangup(RespokeCall.this);
                delegate = null;
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
                        delegate.onError("An invalid stream was added", RespokeCall.this);
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
                        JSONObject data = new JSONObject("{'target':'call'}");
                        data.put("signalType", sdp.type);
                        data.put("to", endpoint.getEndpointID());
                        data.put("sessionId", sessionID);
                        data.put("signalId", Respoke.makeGUID());

                        JSONObject sdpJSON = new JSONObject();
                        sdpJSON.put("sdp", sdp.description);
                        sdpJSON.put("type", sdp.type);

                        data.put("sdp", sdpJSON);

                        signalingChannel.sendSignal(data, endpoint.getEndpointID(), new RespokeTaskCompletionDelegate() {
                            @Override
                            public void onSuccess() {
                                // Do nothing
                            }

                            @Override
                            public void onError(String errorMessage) {
                                delegate.onError(errorMessage, RespokeCall.this);
                            }
                        });
                    } catch (JSONException e) {
                        delegate.onError("Error encoding sdp", RespokeCall.this);
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
                    delegate.onError("createSDP error: " + error, RespokeCall.this);
                }
            });
        }

        @Override public void onSetFailure(final String error) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    delegate.onError("setSDP error: " + error, RespokeCall.this);
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

            JSONObject signalData = new JSONObject("{'signalType':'iceCandidates','target':'call'}");
            signalData.put("to", endpoint.getEndpointID());
            signalData.put("toConnection", toConnection);
            signalData.put("sessionId", sessionID);
            signalData.put("signalId", Respoke.makeGUID());
            signalData.put("iceCandidates", candidateArray);

            signalingChannel.sendSignal(signalData, endpoint.getEndpointID(), new RespokeTaskCompletionDelegate() {
                @Override
                public void onSuccess() {
                    // Do nothing
                }

                @Override
                public void onError(String errorMessage) {
                    delegate.onError(errorMessage, RespokeCall.this);
                }
            });
        } catch (JSONException e) {
            delegate.onError("Unable to encode local candidate", this);
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
                continue;
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
