package com.digium.respokesdk.functional;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeCall;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeConnection;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdk.RespokeGroup;
import com.digium.respokesdk.RespokeTestCase;

import java.util.ArrayList;

/**
 * Created by jasonadams on 1/26/15.
 */
public class GroupTests extends RespokeTestCase implements RespokeClient.Listener, RespokeGroup.Listener {

    private final static String TEST_GROUP_MESSAGE = "What is going on in this group?";
    private String firstTestEndpointID;
    private String secondTestEndpointID;
    private RespokeGroup firstClientGroup;
    private RespokeGroup secondClientGroup;
    private boolean callbackSucceeded;
    private boolean membershipChanged;
    private boolean messageReceived;
    private RespokeEndpoint secondEndpoint;


    public void testGroupMembershipAndMessaging() {
        // Create a client to test with
        RespokeClient firstClient = Respoke.sharedInstance().createClient(getContext());
        assertNotNull(firstClient);
        firstClient.baseURL = TEST_RESPOKE_BASE_URL;

        firstTestEndpointID = generateTestEndpointID();
        assertNotNull("Should create test endpoint id", firstTestEndpointID);

        asyncTaskDone = false;

        asyncTaskDone = false;
        firstClient.setListener(this);
        firstClient.connect(firstTestEndpointID, RespokeTestCase.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("First client should connect", firstClient.isConnected());


        // Create a second client to test with
        final RespokeClient secondClient = Respoke.sharedInstance().createClient(getContext());
        assertNotNull("Should create test client", secondClient);
        secondClient.baseURL = TEST_RESPOKE_BASE_URL;

        secondTestEndpointID = generateTestEndpointID();
        assertNotNull("Should create test endpoint id", secondTestEndpointID);

        asyncTaskDone = false;
        secondClient.setListener(this);
        secondClient.connect(secondTestEndpointID, RespokeTestCase.testAppID, true, null, getContext(), new RespokeClient.ConnectCompletionListener() {
            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully connect", false);
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Second client should connect", secondClient.isConnected());


        // Have each client join the same group and discover each other


        String testGroupID = "group" + generateTestEndpointID();

        asyncTaskDone = false;
        ArrayList<String> groupList = new ArrayList<String>();
        groupList.add(testGroupID);
        firstClient.joinGroups(groupList, new RespokeClient.JoinGroupCompletionListener() {
            @Override
            public void onSuccess(ArrayList<RespokeGroup> groups) {
                assertNotNull("Group list should not be null", groups);
                assertTrue("There should be one group that was joined", 1 == groups.size());
                firstClientGroup = groups.get(0);
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully join the test group. error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertNotNull("Group object should have been found", firstClientGroup);
        assertTrue("Group should have the correct ID", firstClientGroup.getGroupID().equals(testGroupID));
        assertTrue("Group should indicate that it is currently joined", firstClientGroup.isJoined());
        firstClientGroup.setListener(this);

        // Get the list of group members while firstClient is the only one there

        asyncTaskDone = false;
        firstClientGroup.getMembers(new RespokeGroup.GetGroupMembersCompletionListener() {
            @Override
            public void onSuccess(ArrayList<RespokeConnection> memberArray) {
                assertNotNull("Member list should not be null", memberArray);
                assertTrue("There should be 0 members in the group initially", 0 == memberArray.size());
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully get the list of group members. Error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));


        asyncTaskDone = false;
        callbackSucceeded = false;
        membershipChanged = false;
        secondClient.joinGroups(groupList, new RespokeClient.JoinGroupCompletionListener() {
            @Override
            public void onSuccess(ArrayList<RespokeGroup> groups) {
                assertNotNull("Group list should not be null", groups);
                assertTrue("There should be one group that was joined", 1 == groups.size());
                secondClientGroup = groups.get(0);
                callbackSucceeded = true;
                asyncTaskDone = membershipChanged;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully join the test group. error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertNotNull("Group object should have been found", secondClientGroup);
        assertTrue("Group should have the correct ID", secondClientGroup.getGroupID().equals(testGroupID));
        assertTrue("Group should indicate that it is currently joined", secondClientGroup.isJoined());

        // Get the list of group members now that the second client has joined

        asyncTaskDone = false;
        firstClientGroup.getMembers(new RespokeGroup.GetGroupMembersCompletionListener() {
            @Override
            public void onSuccess(ArrayList<RespokeConnection> memberArray) {
                assertNotNull("Member list should not be null", memberArray);
                assertTrue("There should be 1 members in the group initially", 1 == memberArray.size());
                RespokeConnection connection = memberArray.get(0);
                assertNotNull("Connection ID should be not null", connection.connectionID);
                secondEndpoint = connection.getEndpoint();
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully get the list of group members. Error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertNotNull("Should have found the reference to the other client", secondEndpoint);
        assertTrue("Should have the correct endpoint ID", secondEndpoint.getEndpointID().equals(secondTestEndpointID));


        // Test sending and receiving a group message
        asyncTaskDone = false;
        callbackSucceeded = false;
        messageReceived = false;
        secondClientGroup.sendMessage(TEST_GROUP_MESSAGE, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                callbackSucceeded = true;
                asyncTaskDone = messageReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully send a group message. Error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("MEssage should be received", messageReceived);


        // Test receiving a leave notification

        asyncTaskDone = false;
        callbackSucceeded = false;
        membershipChanged = false;
        secondClientGroup.leave(new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                callbackSucceeded = true;
                asyncTaskDone = membershipChanged;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully leave a group. Error: " + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Leave notification should have been received", membershipChanged);
        assertTrue("Should indicate group is no longer joined", !secondClientGroup.isJoined());
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
        // Not under test
    }


    public void onIncomingDirectConnection(RespokeDirectConnection directConnection, RespokeEndpoint endpoint) {
        // Not under test
    }


    // RespokeGroup.Listener methods


    public void onJoin(RespokeConnection connection, RespokeGroup sender) {
        assertTrue("Sender should be correct", sender == firstClientGroup);
        assertNotNull("Connection should not be nil", connection);
        assertTrue("Connection should be associated with the correct endpoint ID", secondTestEndpointID.equals(connection.getEndpoint().getEndpointID()));
        membershipChanged = true;
        asyncTaskDone = callbackSucceeded;
    }


    public void onLeave(RespokeConnection connection, RespokeGroup sender) {
        assertTrue("Sender should be correct", sender == firstClientGroup);
        assertNotNull("Connection should not be nil", connection);
        assertTrue("Connection should be associated with the correct endpoint ID", connection.getEndpoint().getEndpointID().equals(secondTestEndpointID));
        membershipChanged = true;
        asyncTaskDone = callbackSucceeded;
    }


    public void onGroupMessage(String message, RespokeEndpoint endpoint, RespokeGroup sender) {
        assertNotNull("Message should not be null", message);
        assertTrue("Message should be correct", message.equals(TEST_GROUP_MESSAGE));
        assertTrue("Should reference the same endpoint object object that sent the message", endpoint == secondEndpoint);
        messageReceived = true;
        asyncTaskDone = callbackSucceeded;
    }


}
