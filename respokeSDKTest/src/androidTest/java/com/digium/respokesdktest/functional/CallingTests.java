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
import com.digium.respokesdktest.MainActivity;
import com.digium.respokesdktest.R;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by jasonadams on 1/29/15.
 */
public class CallingTests extends ActivityInstrumentationTestCase2<MainActivity> implements RespokeClient.Listener, RespokeEndpoint.Listener, RespokeCall.Listener {
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

    private CountDownLatch signal = new CountDownLatch(1);


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
        signal = new CountDownLatch(1); // Reset the countdown signal
        try {
            // The openGL view must be removed from the UI thread
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainLayout.removeAllViews();
                    signal.countDown();
                }
            });

            // Wait for the asynchronous task to complete
            signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }


    public void testPreconditions() {
        assertNotNull("mainActivity is null", mainActivity);
    }


    public void testVoiceCalling() throws Throwable {
        // Create a client to test with
        final RespokeClient client = Respoke.sharedInstance().createClient(mainActivity);
        assertNotNull("Should create test client", client);
        client.baseURL = RespokeTestCase.TEST_RESPOKE_BASE_URL;

        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        assertNotNull("Should create test endpoint id", testEndpointID);

        signal = new CountDownLatch(1); // Reset the countdown signal
        client.setListener(this);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                client.connect(testEndpointID, RespokeTestCase.testAppID, true, null, mainActivity, new RespokeClient.ConnectCompletionListener() {
                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully connect. error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Test client should connect", client.isConnected());


        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        final RespokeEndpoint testbotEndpoint = client.getEndpoint(RespokeTestCase.TEST_BOT_ENDPOINT_ID, false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        signal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        testbotIsListening = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_HELLO_MESSAGE, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        callbackDidSucceed = true;
                        if (messageReceived) {
                            signal.countDown();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                        signal.countDown();
                   }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Testbot web UI is not running. Please start it and try again.", testbotIsListening);

        // If the web testbot is not running, don't bother trying the rest since the test has already
        if (testbotIsListening) {
            // Try to call the testbot, which should automatically answer
            signal = new CountDownLatch(1); // Reset the countdown signal
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call = testbotEndpoint.startCall(CallingTests.this, mainActivity, null, true);
                }
            });

            assertTrue("Test timed out", signal.await(RespokeTestCase.CALL_TEST_TIMEOUT, TimeUnit.SECONDS));
            assertTrue("Call should be established", didConnect);
            assertTrue("Call should indicate that it is the caller", call.isCaller());
            assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == call.endpoint);
            assertTrue("Should indicate this is an audio-only call", call.audioOnly);

            // Let the call run for a while to make sure it is stable
            signal = new CountDownLatch(1); // Reset the countdown signal
            signal.await(1, TimeUnit.SECONDS);
            assertTrue("Audio should not be muted", !call.audioIsMuted());
            assertTrue("Video should be considered muted", call.videoIsMuted());

            // Mute the audio
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.muteAudio(true);
                }
            });

            signal = new CountDownLatch(1); // Reset the countdown signal
            signal.await(1, TimeUnit.SECONDS);

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

            signal = new CountDownLatch(1); // Reset the countdown signal
            signal.await(1, TimeUnit.SECONDS);

            assertTrue("Audio should not be muted", !call.audioIsMuted());
            assertTrue("Video should be considered muted", call.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            signal = new CountDownLatch(1); // Reset the countdown signal
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.hangup(true);
                }
            });

            assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));

            assertTrue("Should have hung up the call", didHangup);
        }
    }


    public void testVideoCalling() throws Throwable {
        // Create a client to test with
        final RespokeClient client = Respoke.sharedInstance().createClient(mainActivity);
        assertNotNull("Should create test client", client);
        client.baseURL = RespokeTestCase.TEST_RESPOKE_BASE_URL;

        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        assertNotNull("Should create test endpoint id", testEndpointID);

        signal = new CountDownLatch(1); // Reset the countdown signal
        client.setListener(this);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                client.connect(testEndpointID, RespokeTestCase.testAppID, true, null, mainActivity, new RespokeClient.ConnectCompletionListener() {
                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully connect. error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Test client should connect", client.isConnected());


        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        final RespokeEndpoint testbotEndpoint = client.getEndpoint(RespokeTestCase.TEST_BOT_ENDPOINT_ID, false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        signal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        testbotIsListening = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_HELLO_MESSAGE, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        callbackDidSucceed = true;
                        if (messageReceived) {
                            signal.countDown();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Testbot web UI is not running. Please start it and try again.", testbotIsListening);

        // If the web testbot is not running, don't bother trying the rest since the test has already
        if (testbotIsListening) {
            // Try to call the testbot, which should automatically answer
            signal = new CountDownLatch(1); // Reset the countdown signal
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addGLView();
                    call = testbotEndpoint.startCall(CallingTests.this, mainActivity, videoView, false);
                }
            });

            assertTrue("Test timed out", signal.await(RespokeTestCase.CALL_TEST_TIMEOUT, TimeUnit.SECONDS));
            assertTrue("Call should be established", didConnect);
            assertTrue("Call should indicate that it is the caller", call.isCaller());
            assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == call.endpoint);
            assertTrue("Should indicate this is a video call", !call.audioOnly);

            // Let the call run for a while to make sure it is stable
            signal = new CountDownLatch(1); // Reset the countdown signal
            signal.await(1, TimeUnit.SECONDS);
            assertTrue("Audio should not be muted", !incomingCall.audioIsMuted());
            assertTrue("Video should not be muted", !incomingCall.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            // Mute the audio & video
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.muteAudio(true);
                    call.muteVideo(true);
                }
            });

            signal = new CountDownLatch(1); // Reset the countdown signal
            signal.await(1, TimeUnit.SECONDS);

            assertTrue("Audio should now be muted", incomingCall.audioIsMuted());
            assertTrue("Video should now be muted", incomingCall.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            // Un-mute the video
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.muteVideo(false);
                }
            });

            signal = new CountDownLatch(1); // Reset the countdown signal
            signal.await(1, TimeUnit.SECONDS);

            assertTrue("Audio should still be muted", incomingCall.audioIsMuted());
            assertTrue("Video should no longer be muted", !incomingCall.videoIsMuted());
            assertTrue("Should not have hung up the call", !didHangup);

            signal = new CountDownLatch(1); // Reset the countdown signal
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    call.hangup(true);
                }
            });

            assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));

            assertTrue("Should have hung up the call", didHangup);

            removeGLView();
        }
    }


    public void testVoiceAnswering() throws Throwable {
        // Create a client to test with
        final RespokeClient client = Respoke.sharedInstance().createClient(mainActivity);
        assertNotNull("Should create test client", client);
        client.baseURL = RespokeTestCase.TEST_RESPOKE_BASE_URL;

        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        assertNotNull("Should create test endpoint id", testEndpointID);

        signal = new CountDownLatch(1); // Reset the countdown signal
        client.setListener(this);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                client.connect(testEndpointID, RespokeTestCase.testAppID, true, null, mainActivity, new RespokeClient.ConnectCompletionListener() {
                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully connect. error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Test client should connect", client.isConnected());


        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        testbotEndpoint = client.getEndpoint(RespokeTestCase.TEST_BOT_ENDPOINT_ID, false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        signal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        incomingCallReceived = false;
        didConnect = false;
        didHangup = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_CALL_ME_MESSAGE, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        callbackDidSucceed = true;
                        if (incomingCallReceived) {
                            signal.countDown();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Should have received an incoming call signal.", incomingCallReceived);
        assertNotNull("Should have created a call object to represent the incoming call", incomingCall);
        assertTrue("Should be the recipient of the call, not the caller", !incomingCall.isCaller());
        assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == incomingCall.endpoint);

        signal = new CountDownLatch(1); // Reset the countdown signal
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                incomingCall.answer(mainActivity, CallingTests.this);
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.CALL_TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Call should be established", didConnect);
        assertTrue("Should indicate this is an audio-only call", incomingCall.audioOnly);

        // Let the call run for a while to make sure it is stable
        signal = new CountDownLatch(1); // Reset the countdown signal
        signal.await(1, TimeUnit.SECONDS);
        assertTrue("Should not have hung up the call", !didHangup);
        assertTrue("Audio should not be muted", !incomingCall.audioIsMuted());
        assertTrue("Video should be considered muted", incomingCall.videoIsMuted());

        // Send a message to the testbot asking it to hangup the call so that we can test detecting that event
        signal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_HANGUP_MESSAGE, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        callbackDidSucceed = true;
                        if (didHangup) {
                            signal.countDown();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Should have hung up", didHangup);
    }


    public void testVideoAnswering() throws Throwable {
        // Create a client to test with
        final RespokeClient client = Respoke.sharedInstance().createClient(mainActivity);
        assertNotNull("Should create test client", client);
        client.baseURL = RespokeTestCase.TEST_RESPOKE_BASE_URL;

        final String testEndpointID = RespokeTestCase.generateTestEndpointID();
        assertNotNull("Should create test endpoint id", testEndpointID);

        signal = new CountDownLatch(1); // Reset the countdown signal
        client.setListener(this);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                client.connect(testEndpointID, RespokeTestCase.testAppID, true, null, mainActivity, new RespokeClient.ConnectCompletionListener() {
                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully connect. error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Test client should connect", client.isConnected());


        // If things went well, there should be a web page open on the test host running a Transporter app that is logged in as testbot. It is set up to automatically answer any calls placed to it for testing purposes.

        testbotEndpoint = client.getEndpoint(RespokeTestCase.TEST_BOT_ENDPOINT_ID, false);
        assertNotNull("Should create endpoint instance", testbotEndpoint);
        testbotEndpoint.setListener(this);

        // Send a quick message to make sure the test UI is running and produce a meaningful test error message
        signal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        incomingCallReceived = false;
        didConnect = false;
        didHangup = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_CALL_ME_VIDEO_MESSAGE, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        callbackDidSucceed = true;
                        if (incomingCallReceived) {
                            signal.countDown();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Should have received an incoming call signal.", incomingCallReceived);
        assertNotNull("Should have created a call object to represent the incoming call", incomingCall);
        assertTrue("Should be the recipient of the call, not the caller", !incomingCall.isCaller());
        assertTrue("Should indicate call is with the endpoint that the call was started from", testbotEndpoint == incomingCall.endpoint);

        signal = new CountDownLatch(1); // Reset the countdown signal
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                addGLView();
                incomingCall.attachVideoRenderer(videoView);
                incomingCall.answer(mainActivity, CallingTests.this);
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.CALL_TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("Call should be established", didConnect);
        assertTrue("Should indicate this is a video call", !incomingCall.audioOnly);

        // Let the call run for a while to make sure it is stable
        signal = new CountDownLatch(1); // Reset the countdown signal
        signal.await(1, TimeUnit.SECONDS);
        assertTrue("Audio should not be muted", !incomingCall.audioIsMuted());
        assertTrue("Video should not be muted", !incomingCall.videoIsMuted());
        assertTrue("Should not have hung up the call", !didHangup);

        // Send a message to the testbot asking it to hangup the call so that we can test detecting that event
        signal = new CountDownLatch(1); // Reset the countdown signal
        callbackDidSucceed = false;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                testbotEndpoint.sendMessage(TEST_BOT_HANGUP_MESSAGE, new Respoke.TaskCompletionListener() {
                    @Override
                    public void onSuccess() {
                        callbackDidSucceed = true;
                        if (didHangup) {
                            signal.countDown();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully send a message. Error: " + errorMessage, false);
                        signal.countDown();
                    }
                });
            }
        });

        assertTrue("Test timed out", signal.await(RespokeTestCase.TEST_TIMEOUT, TimeUnit.SECONDS));
        assertTrue("sendMessage should call onSuccess", callbackDidSucceed);
        assertTrue("Should have hung up", didHangup);

        removeGLView();
    }

    // RespokeClient.Listener methods


    public void onConnect(RespokeClient sender) {
        signal.countDown();
    }


    public void onDisconnect(RespokeClient sender, boolean reconnecting) {

    }


    public void onError(RespokeClient sender, String errorMessage) {
        assertTrue("Should not produce any client errors during endpoint testing", false);
        signal.countDown();
    }


    public void onCall(RespokeClient sender, RespokeCall call) {
        incomingCall = call;
        incomingCallReceived = true;

        if (callbackDidSucceed) {
            signal.countDown();
        }
    }


    public void onIncomingDirectConnection(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }


    // RespokeEndpoint.Listener methods


    public void onMessage(String message, Date timestamp, RespokeEndpoint sender) {
        testbotIsListening = message.equals(TEST_BOT_HELLO_REPLY);
        messageReceived = true;

        if (callbackDidSucceed) {
            signal.countDown();
        }
    }


    public void onPresence(Object presence, RespokeEndpoint sender) {
        // Not under test
    }


    // RespokeCall.Listener methods


    public void onError(String errorMessage, RespokeCall sender) {
        assertTrue("Should perform a call without any errors. Error: " + errorMessage, false);
        signal.countDown();
    }


    public void onHangup(RespokeCall sender) {
        didHangup = true;

        if (null != incomingCall) {
            if (callbackDidSucceed) {
                signal.countDown();
            }
        } else {
            signal.countDown();
        }
    }


    public void onConnected(RespokeCall sender) {
        didConnect = true;
        signal.countDown();
    }


    public void directConnectionAvailable(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }

}
