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

import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdktest.RespokeTestCase;

import org.json.JSONException;
import org.json.JSONObject;


public class RespokeCallTests extends RespokeTestCase {


    public void testSdpHasVideo() {
        try {
            JSONObject audioOnlySDP = new JSONObject("{\"type\":\"offer\",\"sdp\":\"v=0\\r\\no=- 6116182927273193777 2 IN IP4 127.0.0.1\\r\\ns=-\\r\\nt=0 0\\r\\na=group:BUNDLE audio\\r\\na=msid-semantic: WMS HxDm7ZdgkDE9aJpjeytJhT3stMgXxapdk8DY\\r\\nm=audio 9 RTP\\/SAVPF 111 103 104 9 0 8 106 105 13 126\\r\\nc=IN IP4 0.0.0.0\\r\\na=rtcp:9 IN IP4 0.0.0.0\\r\\na=ice-ufrag:AdddxlRYoMW8UICE\\r\\na=ice-pwd:BVFvVAKconjagjwsLlc8eoOq\\r\\na=fingerprint:sha-256 21:26:14:08:05:AD:D0:07:2F:8A:CC:D8:40:A9:A5:B9:72:5D:62:6D:83:1A:AF:76:35:F3:EA:4C:12:28:37:13\\r\\na=setup:actpass\\r\\na=mid:audio\\r\\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\\r\\na=extmap:3 http:\\/\\/www.webrtc.org\\/experiments\\/rtp-hdrext\\/abs-send-time\\r\\na=sendrecv\\r\\na=rtcp-mux\\r\\na=rtpmap:111 opus\\/48000\\/2\\r\\na=fmtp:111 minptime=10; useinbandfec=1\\r\\na=rtpmap:103 ISAC\\/16000\\r\\na=rtpmap:104 ISAC\\/32000\\r\\na=rtpmap:9 G722\\/8000\\r\\na=rtpmap:0 PCMU\\/8000\\r\\na=rtpmap:8 PCMA\\/8000\\r\\na=rtpmap:106 CN\\/32000\\r\\na=rtpmap:105 CN\\/16000\\r\\na=rtpmap:13 CN\\/8000\\r\\na=rtpmap:126 telephone-event\\/8000\\r\\na=maxptime:60\\r\\na=ssrc:3260245544 cname:EU2GZuWLO3m0+MJp\\r\\na=ssrc:3260245544 msid:HxDm7ZdgkDE9aJpjeytJhT3stMgXxapdk8DY 4c809bee-6b56-438a-ac90-d7053c0c4770\\r\\na=ssrc:3260245544 mslabel:HxDm7ZdgkDE9aJpjeytJhT3stMgXxapdk8DY\\r\\na=ssrc:3260245544 label:4c809bee-6b56-438a-ac90-d7053c0c4770\\r\\n\"}");
            JSONObject videoSDP = new JSONObject("{\"type\":\"offer\",\"sdp\":\"v=0\\r\\no=- 2269581771901782266 2 IN IP4 127.0.0.1\\r\\ns=-\\r\\nt=0 0\\r\\na=group:BUNDLE audio video\\r\\na=msid-semantic: WMS g7oxcNrUqwuwbUOvVLFMKO5OvWTOi8ee8ND6\\r\\nm=audio 9 RTP\\/SAVPF 111 103 104 9 0 8 106 105 13 126\\r\\nc=IN IP4 0.0.0.0\\r\\na=rtcp:9 IN IP4 0.0.0.0\\r\\na=ice-ufrag:0\\/4ED6Lq6RjLjTek\\r\\na=ice-pwd:BQdH\\/GJRrIOmFnrIEn0NRpzb\\r\\na=fingerprint:sha-256 21:26:14:08:05:AD:D0:07:2F:8A:CC:D8:40:A9:A5:B9:72:5D:62:6D:83:1A:AF:76:35:F3:EA:4C:12:28:37:13\\r\\na=setup:actpass\\r\\na=mid:audio\\r\\na=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\\r\\na=extmap:3 http:\\/\\/www.webrtc.org\\/experiments\\/rtp-hdrext\\/abs-send-time\\r\\na=sendrecv\\r\\na=rtcp-mux\\r\\na=rtpmap:111 opus\\/48000\\/2\\r\\na=fmtp:111 minptime=10; useinbandfec=1\\r\\na=rtpmap:103 ISAC\\/16000\\r\\na=rtpmap:104 ISAC\\/32000\\r\\na=rtpmap:9 G722\\/8000\\r\\na=rtpmap:0 PCMU\\/8000\\r\\na=rtpmap:8 PCMA\\/8000\\r\\na=rtpmap:106 CN\\/32000\\r\\na=rtpmap:105 CN\\/16000\\r\\na=rtpmap:13 CN\\/8000\\r\\na=rtpmap:126 telephone-event\\/8000\\r\\na=maxptime:60\\r\\na=ssrc:2445183102 cname:QOl3hq3Z4\\/FooJ+H\\r\\na=ssrc:2445183102 msid:g7oxcNrUqwuwbUOvVLFMKO5OvWTOi8ee8ND6 24650e1f-14f9-46ea-ba5a-6f5f86262d31\\r\\na=ssrc:2445183102 mslabel:g7oxcNrUqwuwbUOvVLFMKO5OvWTOi8ee8ND6\\r\\na=ssrc:2445183102 label:24650e1f-14f9-46ea-ba5a-6f5f86262d31\\r\\nm=video 9 RTP\\/SAVPF 100 116 117 96\\r\\nc=IN IP4 0.0.0.0\\r\\na=rtcp:9 IN IP4 0.0.0.0\\r\\na=ice-ufrag:0\\/4ED6Lq6RjLjTek\\r\\na=ice-pwd:BQdH\\/GJRrIOmFnrIEn0NRpzb\\r\\na=fingerprint:sha-256 21:26:14:08:05:AD:D0:07:2F:8A:CC:D8:40:A9:A5:B9:72:5D:62:6D:83:1A:AF:76:35:F3:EA:4C:12:28:37:13\\r\\na=setup:actpass\\r\\na=mid:video\\r\\na=extmap:2 urn:ietf:params:rtp-hdrext:toffset\\r\\na=extmap:3 http:\\/\\/www.webrtc.org\\/experiments\\/rtp-hdrext\\/abs-send-time\\r\\na=sendrecv\\r\\na=rtcp-mux\\r\\na=rtpmap:100 VP8\\/90000\\r\\na=rtcp-fb:100 ccm fir\\r\\na=rtcp-fb:100 nack\\r\\na=rtcp-fb:100 nack pli\\r\\na=rtcp-fb:100 goog-remb\\r\\na=rtpmap:116 red\\/90000\\r\\na=rtpmap:117 ulpfec\\/90000\\r\\na=rtpmap:96 rtx\\/90000\\r\\na=fmtp:96 apt=100\\r\\na=ssrc-group:FID 3991731061 1826556079\\r\\na=ssrc:3991731061 cname:QOl3hq3Z4\\/FooJ+H\\r\\na=ssrc:3991731061 msid:g7oxcNrUqwuwbUOvVLFMKO5OvWTOi8ee8ND6 b8e33db0-d73b-4695-ade9-9fea843a0111\\r\\na=ssrc:3991731061 mslabel:g7oxcNrUqwuwbUOvVLFMKO5OvWTOi8ee8ND6\\r\\na=ssrc:3991731061 label:b8e33db0-d73b-4695-ade9-9fea843a0111\\r\\na=ssrc:1826556079 cname:QOl3hq3Z4\\/FooJ+H\\r\\na=ssrc:1826556079 msid:g7oxcNrUqwuwbUOvVLFMKO5OvWTOi8ee8ND6 b8e33db0-d73b-4695-ade9-9fea843a0111\\r\\na=ssrc:1826556079 mslabel:g7oxcNrUqwuwbUOvVLFMKO5OvWTOi8ee8ND6\\r\\na=ssrc:1826556079 label:b8e33db0-d73b-4695-ade9-9fea843a0111\\r\\n\"}");

            assertTrue("Should indicate that SDP has video", RespokeCall.sdpHasVideo(videoSDP));
            assertTrue("Should indicate that SDP does not have video", !RespokeCall.sdpHasVideo(audioOnlySDP));
        } catch (JSONException e) {
            assertTrue("Could not build test SDPs", false);
        }
    }

}
