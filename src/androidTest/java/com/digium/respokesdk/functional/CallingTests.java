package com.digium.respokesdk.functional;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdk.RespokeTestCase;

import java.util.Date;

/**
 * Created by jasonadams on 1/29/15.
 */
public class CallingTests extends RespokeTestCase implements RespokeClient.Listener, RespokeEndpoint.Listener, RespokeCall.Listener {
    static final String TEST_BOT_HELLO_MESSAGE = "Hi testbot!";
    static final String TEST_BOT_HELLO_REPLY = "Hey pal!";
    static final String TEST_BOT_CALL_ME_MESSAGE = "Testbot! Call me sometime! Or now!";
    static final String TEST_BOT_HANGUP_MESSAGE = "Hang up dude. I'm done talking.";

    private boolean callbackDidSucceed;
    private boolean messageReceived;
    private boolean testbotIsListening;
    private boolean didConnect;
    private boolean didHangup;
    private boolean incomingCallReceived;
    private RespokeCall incomingCall;


    public void testVoiceCalling() {
        // Create a client to test with
        final RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", client);
        client.baseURL = TEST_RESPOKE_BASE_URL;

        final String testEndpointID = generateTestEndpointID();
        assertNotNull("Should create test endpoint id", testEndpointID);

        asyncTaskDone = false;
        client.setListener(this);
        client.connect(testEndpointID, RespokeTestCase.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
            assertTrue("Should successfully connect", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Test client should connect", client.isConnected());


        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        RespokeEndpoint testbotEndpoint = client.getEndpoint(TEST_BOT_ENDPOINT_ID, false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        asyncTaskDone = false;
        callbackDidSucceed = false;
        testbotEndpoint.sendMessage(TEST_BOT_HELLO_MESSAGE, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                callbackDidSucceed = true;
                asyncTaskDone = messageReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Testbot web UI is not running. Please start it and try again.", testbotIsListening);

        // If the web testbot is not running, don't bother trying the rest since the test has already
        if (testbotIsListening) {
            // Try to call the testbot, which should automatically answer
            asyncTaskDone = false;
            RespokeCall call = testbotEndpoint.startCall(this, getContext(), null, true);

            assertTrue("Test timed out", waitForCompletion(RespokeTestCase.CALL_TEST_TIMEOUT));
            assertTrue("Call should be established", didConnect);
            assertTrue("Call should indicate that it is the caller", call.isCaller());
            assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == call.endpoint);
            assertTrue("Should indicate this is an audio-only call", call.audioOnly);

            // Let the call run for a while to make sure it is stable
            asyncTaskDone = false;
            waitForCompletion(1);

            // Mute the audio
            call.muteAudio(true);
            asyncTaskDone = false;
            waitForCompletion(1);

            assertTrue("Should not have hung up the call", !didHangup);

            // Un-mute the audio
            call.muteAudio(false);
            asyncTaskDone = false;
            waitForCompletion(1);

            assertTrue("Should not have hung up the call", !didHangup);

            asyncTaskDone = false;
            call.hangup(true);
            waitForCompletion(RespokeTestCase.TEST_TIMEOUT);

            assertTrue("Should have hung up the call", didHangup);
        }
    }


    public void testVoiceAnswering() {
        // Create a client to test with
        final RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", client);
        client.baseURL = TEST_RESPOKE_BASE_URL;

        final String testEndpointID = generateTestEndpointID();
        assertNotNull("Should create test endpoint id", testEndpointID);

        asyncTaskDone = false;
        client.setListener(this);
        client.connect(testEndpointID, RespokeTestCase.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Test client should connect", client.isConnected());


        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        RespokeEndpoint testbotEndpoint = client.getEndpoint(TEST_BOT_ENDPOINT_ID, false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        asyncTaskDone = false;
        callbackDidSucceed = false;
        incomingCallReceived = false;
        didConnect = false;
        didHangup = false;
        testbotEndpoint.sendMessage(TEST_BOT_CALL_ME_MESSAGE, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                callbackDidSucceed = true;
                asyncTaskDone = incomingCallReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Should have received an incoming call signal.", incomingCallReceived);
        assertNotNull("Should have created a call object to represent the incoming call", incomingCall);
        assertTrue("Should be the recipient of the call, not the caller", !incomingCall.isCaller());
        assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == incomingCall.endpoint);

        asyncTaskDone = false;
        incomingCall.answer(getContext(), this);
        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.CALL_TEST_TIMEOUT));
        assertTrue("Call should be established", didConnect);
        assertTrue("Should indicate this is an audio-only call", incomingCall.audioOnly);

        // Let the call run for a while to make sure it is stable
        asyncTaskDone = false;
        waitForCompletion(1);
        assertTrue("Should not have hung up the call", !didHangup);

        // Send a message to the testbot asking it to hangup the call so that we can test detecting that event
        asyncTaskDone = false;
        callbackDidSucceed = false;
        testbotEndpoint.sendMessage(TEST_BOT_HANGUP_MESSAGE, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                callbackDidSucceed = true;
                asyncTaskDone = didHangup;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Should have hung up", didHangup);
    }

    // RespokeClient.Listener methods


    public void onConnect(RespokeClient sender) {
        asyncTaskDone = true;
    }


    public void onDisconnect(RespokeClient sender, boolean reconnecting) {

    }


    public void onError(RespokeClient sender, String errorMessage) {
        assertTrue("Should not produce any client errors during endpoint testing", false);
        asyncTaskDone = true;
    }


    public void onCall(RespokeClient sender, RespokeCall call) {
        incomingCall = call;
        incomingCallReceived = true;
        asyncTaskDone = callbackDidSucceed;
    }


    public void onIncomingDirectConnection(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint sender) {
        testbotIsListening = message.equals(TEST_BOT_HELLO_REPLY);
        messageReceived = true;
        asyncTaskDone = callbackDidSucceed;
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        // Not under test
    }


    // RespokeCall.Listener methods


    public void onError(String errorMessage, RespokeCall sender) {
        assertTrue("Should perform a call without any errors. Error: " + errorMessage, false);
        asyncTaskDone = true;
    }


    public void onHangup(RespokeCall sender) {
        didHangup = true;

        if (null != incomingCall) {
            asyncTaskDone = callbackDidSucceed;
        } else {
            asyncTaskDone = true;
        }
    }


    public void onConnected(RespokeCall sender) {
        didConnect = true;
        asyncTaskDone = true;
    }


    public void directConnectionAvailable(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }

}
