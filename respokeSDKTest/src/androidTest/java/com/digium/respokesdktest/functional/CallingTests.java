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

import android.opengl.GLSurfaceView;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.widget.RelativeLayout;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdk.RespokeGroup;
import com.digium.respokesdktest.MainActivity;
import com.digium.respokesdktest.R;
import com.digium.respokesdktest.RespokeActivityTestCase;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class CallingTests extends RespokeActivityTestCase<MainActivity> implements RespokeClient.Listener, RespokeEndpoint.Listener, RespokeCall.Listener {
    static final String TEST_BOT_HELLO_MESSAGE = "Hi testbot!";
    static final String TEST_BOT_HELLO_REPLY = "Hey pal!";
    static final String TEST_BOT_CALL_ME_MESSAGE = "Testbot! Call me sometime! Or now!";
    static final String TEST_BOT_CALL_ME_VIDEO_MESSAGE = "Testbot! Call me using video!";
    static final String TEST_BOT_HANGUP_MESSAGE = "Hang up dude. I'm done talking.";

    private boolean callbackDidSucceed;
    private boolean messageReceived;
    private boolean testbotIsListening;
    private boolean didConnect;
    private boolean didHangup;
    private boolean incomingCallReceived;
    private RespokeCall incomingCall;
    private RespokeCall call;
    private RespokeEndpoint testbotEndpoint;

    private MainActivity mainActivity;
    private RelativeLayout mainLayout;
    private GLSurfaceView videoView;


    public CallingTests() {
        super(MainActivity.class);
    }


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mainActivity = getActivity();
        mainLayout = (RelativeLayout) mainActivity.findViewById(R.id.mainlayout);
    }


    /// Programmatically add an OpenGL renderer to the Activity layout to support video. This must be called from the UI thread.
    private void addGLView() {
        videoView = new GLSurfaceView(mainActivity);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        mainLayout.addView(videoView, params);
    }


    /// Remove the OpenGL view from the layout when it is no longer needed
    private void removeGLView() {
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        try {
            // The openGL view must be removed from the UI thread
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainLayout.removeAllViews();
                    asyncTaskSignal.countDown();
                }
            });

            // Wait for the asynchronous task to complete
            asyncTaskSignal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }


    public void testPreconditions() {
        assertNotNull("mainActivity is null", mainActivity);
    }


    public void testVoiceCalling() throws Throwable {
        // Create a client to test with
        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        final RespokeClient client = createTestClient(testEndpointID, this, mainActivity);

        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        final RespokeEndpoint testbotEndpoint = client.getEndpoint(RespokeTestCase.getTestBotEndpointId(getActivity()), false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        testbotIsListening = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_HELLO_MESSAGE, false, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                        callbackDidSucceed = true;
                        if (messageReceived) {
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
        assertTrue("Testbot web UI is not running. Please start it and try again.", testbotIsListening);

        // If the web testbot is not running, don't bother trying the rest since the test has already
        if (testbotIsListening) {
            // Try to call the testbot, which should automatically answer
            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call = testbotEndpoint.startCall(CallingTests.this, mainActivity, null, true);
                }
            });

            assertTrue("Test timed out", asyncTaskSignal.await(RespokeTestCase.CALL_TEST_TIMEOUT, TimeUnit.SECONDS));
            assertTrue("Call should be established", didConnect);
            assertTrue("Call should indicate that it is the caller", call.isCaller());
            assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == call.endpoint);
            assertTrue("Should indicate this is an audio-only call", call.audioOnly);

            // Let the call run for a while to make sure it is stable
            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            asyncTaskSignal.await(1, TimeUnit.SECONDS);
            assertTrue("Audio should not be muted", !call.audioIsMuted());
            assertTrue("Video should be considered muted", call.videoIsMuted());

            // Mute the audio
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.muteAudio(true);
                }
            });

            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            asyncTaskSignal.await(1, TimeUnit.SECONDS);

            assertTrue("Audio should now be muted", call.audioIsMuted());
            assertTrue("Video should be considered muted", call.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            // Un-mute the audio
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.muteAudio(false);
                }
            });

            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            asyncTaskSignal.await(1, TimeUnit.SECONDS);

            assertTrue("Audio should not be muted", !call.audioIsMuted());
            assertTrue("Video should be considered muted", call.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.hangup(true);
                }
            });

            assertTrue("Test timed out", asyncTaskSignal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));

            assertTrue("Should have hung up the call", didHangup);
        }
    }


    public void testVideoCalling() throws Throwable {
        // Create a client to test with
        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        final RespokeClient client = createTestClient(testEndpointID, this, mainActivity);

        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        final RespokeEndpoint testbotEndpoint = client.getEndpoint(RespokeTestCase.getTestBotEndpointId(getActivity()), false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        testbotIsListening = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_HELLO_MESSAGE, false, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                        callbackDidSucceed = true;
                        if (messageReceived) {
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
        assertTrue("Testbot web UI is not running. Please start it and try again.", testbotIsListening);

        // If the web testbot is not running, don't bother trying the rest since the test has already
        if (testbotIsListening) {
            // Try to call the testbot, which should automatically answer
            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addGLView();
                    call = testbotEndpoint.startCall(CallingTests.this, mainActivity, videoView, false);
                }
            });

            assertTrue("Test timed out", asyncTaskSignal.await(RespokeTestCase.CALL_TEST_TIMEOUT, TimeUnit.SECONDS));
            assertTrue("Call should be established", didConnect);
            assertTrue("Call should indicate that it is the caller", call.isCaller());
            assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == call.endpoint);
            assertTrue("Should indicate this is a video call", !call.audioOnly);

            // Let the call run for a while to make sure it is stable
            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            asyncTaskSignal.await(1, TimeUnit.SECONDS);
            assertTrue("Audio should not be muted", !call.audioIsMuted());
            assertTrue("Video should not be muted", !call.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            // Mute the audio & video
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.muteAudio(true);
                    call.muteVideo(true);
                }
            });

            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            asyncTaskSignal.await(1, TimeUnit.SECONDS);

            assertTrue("Audio should now be muted", call.audioIsMuted());
            assertTrue("Video should now be muted", call.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            // Un-mute the video
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.muteVideo(false);
                }
            });

            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            asyncTaskSignal.await(1, TimeUnit.SECONDS);

            assertTrue("Audio should still be muted", call.audioIsMuted());
            assertTrue("Video should no longer be muted", !call.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.hangup(true);
                }
            });

            assertTrue("Test timed out", asyncTaskSignal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));

            assertTrue("Should have hung up the call", didHangup);

            removeGLView();
        }
    }


    public void testVoiceAnswering() throws Throwable {
        // Create a client to test with
        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        final RespokeClient client = createTestClient(testEndpointID, this, mainActivity);

        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        testbotEndpoint = client.getEndpoint(RespokeTestCase.getTestBotEndpointId(getActivity()), false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        incomingCallReceived = false;
        didConnect = false;
        didHangup = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_CALL_ME_MESSAGE, false, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                        callbackDidSucceed = true;
                        if (incomingCallReceived) {
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
        assertTrue("Should have received an incoming call signal.", incomingCallReceived);
        assertNotNull("Should have created a call object to represent the incoming call", incomingCall);
        assertTrue("Should be the recipient of the call, not the caller", !incomingCall.isCaller());
        assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == incomingCall.endpoint);
        assertTrue("Should indicate this is an audio-only call", incomingCall.audioOnly);

        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                incomingCall.answer(mainActivity, CallingTests.this);
            }
        });

        assertTrue("Test timed out", asyncTaskSignal.await(RespokeTestCase.CALL_TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Call should be established", didConnect);

        // Let the call run for a while to make sure it is stable
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        asyncTaskSignal.await(1, TimeUnit.SECONDS);
        assertTrue("Should not have hung up the call", !didHangup);
        assertTrue("Audio should not be muted", !incomingCall.audioIsMuted());
        assertTrue("Video should be considered muted", incomingCall.videoIsMuted());

        // Send a message to the testbot asking it to hangup the call so that we can test detecting that event
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_HANGUP_MESSAGE, false, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                        callbackDidSucceed = true;
                        if (didHangup) {
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
        assertTrue("Should have hung up", didHangup);
    }


    public void testVideoAnswering() throws Throwable {
        // Create a client to test with
        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        final RespokeClient client = createTestClient(testEndpointID, this, mainActivity);

        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        testbotEndpoint = client.getEndpoint(RespokeTestCase.getTestBotEndpointId(getActivity()), false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        incomingCallReceived = false;
        didConnect = false;
        didHangup = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_CALL_ME_VIDEO_MESSAGE, false, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                        callbackDidSucceed = true;
                        if (incomingCallReceived) {
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
        assertTrue("Should have received an incoming call signal.", incomingCallReceived);
        assertNotNull("Should have created a call object to represent the incoming call", incomingCall);
        assertTrue("Should be the recipient of the call, not the caller", !incomingCall.isCaller());
        assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == incomingCall.endpoint);

        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                addGLView();
                incomingCall.attachVideoRenderer(videoView);
                incomingCall.answer(mainActivity, CallingTests.this);
            }
        });

        assertTrue("Test timed out", asyncTaskSignal.await(RespokeTestCase.CALL_TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Call should be established", didConnect);
        assertTrue("Should indicate this is a video call", !incomingCall.audioOnly);

        // Let the call run for a while to make sure it is stable
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        asyncTaskSignal.await(1, TimeUnit.SECONDS);
        assertTrue("Audio should not be muted", !incomingCall.audioIsMuted());
        assertTrue("Video should not be muted", !incomingCall.videoIsMuted());
        assertTrue("Should not have hung up the call", !didHangup);

        // Send a message to the testbot asking it to hangup the call so that we can test detecting that event
        asyncTaskSignal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_HANGUP_MESSAGE, false, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                        callbackDidSucceed = true;
                        if (didHangup) {
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
        assertTrue("Should have hung up", didHangup);

        removeGLView();
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
        incomingCall = call;
        incomingCallReceived = true;

        if (callbackDidSucceed) {
            asyncTaskSignal.countDown();
        }
    }


    public void onIncomingDirectConnection(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }


    public void onMessage(String message, RespokeEndpoint sender, RespokeGroup group, Date timestamp) {
        // Not under test
    }


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        testbotIsListening = message.equals(TEST_BOT_HELLO_REPLY);
        messageReceived = true;

        if (callbackDidSucceed) {
            asyncTaskSignal.countDown();
        }
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
        didHangup = true;

        if (null != incomingCall) {
            if (callbackDidSucceed) {
                asyncTaskSignal.countDown();
            }
        } else {
            asyncTaskSignal.countDown();
        }
    }


    public void onConnected(RespokeCall sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        didConnect = true;
        asyncTaskSignal.countDown();
    }


    public void directConnectionAvailable(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }

}
