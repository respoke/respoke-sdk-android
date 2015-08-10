/**
 * Copyright 2015, Digium, Inc.
 * All rights reserved.
 *
 * This source code is licensed under The MIT License found in the
 * LICENSE file in the root directory of this source tree.
 *
 * For all details and documentation:  https://www.respoke.io
 */

package com.digium.respokesdktest.functional;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdk.RespokeGroup;
import com.digium.respokesdktest.MainActivity;
import com.digium.respokesdktest.RespokeActivityTestCase;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HangupTests extends RespokeActivityTestCase<MainActivity> implements RespokeClient.Listener, RespokeEndpoint.Listener, RespokeCall.Listener {

    private boolean callbackDidSucceed;
    private boolean firstCallDidHangup;
    private boolean secondCallDidHangup;
    private RespokeCall firstIncomingCall;
    private RespokeCall secondIncomingCall;
    private RespokeEndpoint testbotEndpoint;
    private RespokeClient firstClient;
    private RespokeClient secondClient;


    public HangupTests() {
        super(MainActivity.class);
    }


    public void testCallDeclineCCSelf() throws Throwable {
        // Create a client to test with
        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        firstClient = createTestClient(testEndpointID, this, getActivity());
        secondClient = createTestClient(testEndpointID, this, getActivity());

        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically initiate a call when asked via Respoke message

        testbotEndpoint = firstClient.getEndpoint(RespokeTestCase.getTestBotEndpointId(getActivity()), false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(CallingTests.TEST_BOT_CALL_ME_MESSAGE, false, true, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                        callbackDidSucceed = true;
                        if ((null != firstIncomingCall) && (null != secondIncomingCall)) {
                            asyncTaskSignal.countDown();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                        asyncTaskSignal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", asyncTaskSignal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertNotNull("Should have created a call object to represent the incoming call", firstIncomingCall);
        assertTrue("Should be the recipient of the call, not the caller", !firstIncomingCall.isCaller());
        assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == firstIncomingCall.endpoint);
        assertTrue("Should indicate this is an audio-only call", firstIncomingCall.audioOnly);
        assertNotNull("Should have created a call object to represent the incoming call", secondIncomingCall);
        assertTrue("Should be the recipient of the call, not the caller", !secondIncomingCall.isCaller());
        assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint.getEndpointID().equals(secondIncomingCall.endpoint.getEndpointID()));
        assertTrue("Should indicate this is an audio-only call", secondIncomingCall.audioOnly);

        // the incoming call has been detected by both clients. Decline the call on the first client, and wait for the hangup signal to be received by the 2nd client
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                firstIncomingCall.hangup(true);
            }
        });

        assertTrue("Test timed out", asyncTaskSignal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("First client should have hung up the call", firstCallDidHangup);
        assertTrue("Second client should have been notified of the hangup", secondCallDidHangup);

        firstClient.disconnect();
        Respoke.sharedInstance().unregisterClient(firstClient);
        secondClient.disconnect();
        Respoke.sharedInstance().unregisterClient(secondClient);
    }


    // RespokeClient.Listener methods


    public void onConnect(RespokeClient sender) {
        asyncTaskSignal.countDown();
    }


    public void onDisconnect(RespokeClient sender, boolean reconnecting) {

    }


    public void onError(RespokeClient sender, String errorMessage) {
        assertTrue("Should not produce any client errors during endpoint testing", false);
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        asyncTaskSignal.countDown();
    }


    public void onCall(RespokeClient sender, RespokeCall call) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());

        if (sender == firstClient) {
            firstIncomingCall = call;
            firstIncomingCall.setListener(this);
        } else if (sender == secondClient) {
            secondIncomingCall = call;
            secondIncomingCall.setListener(this);
        } else {
            assertTrue("Unrecognized client received an incoming call", false);
        }

        if ((callbackDidSucceed) && (null != firstIncomingCall) && (null != secondIncomingCall)) {
            asyncTaskSignal.countDown();
        }
    }


    public void onIncomingDirectConnection(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }


    public void onMessage(String message, RespokeEndpoint sender, RespokeGroup group, Date timestamp, Boolean didSend) {
        // Not under test
    }


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint endpoint, boolean didSend) {
        // Not under test
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        // Not under test
    }


    // RespokeCall.Listener methods


    public void onError(String errorMessage, RespokeCall sender) {
        assertTrue("Should perform a call without any errors. Error: " + errorMessage, false);
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        asyncTaskSignal.countDown();
    }


    public void onHangup(RespokeCall sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        
        if (sender == firstIncomingCall) {
            firstCallDidHangup = true;
        } else if (sender == secondIncomingCall) {
            secondCallDidHangup = true;
        } else {
            assertTrue("Unrecognized call received a hangup signal", false);
        }

        if (firstCallDidHangup && secondCallDidHangup) {
            asyncTaskSignal.countDown();
        }
    }


    public void onConnected(RespokeCall sender) {
        // Not under test
    }


    public void directConnectionAvailable(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }

}
