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
import com.digium.respokesdk.RespokeConnection;
import com.digium.respokesdk.RespokeDirectConnection;
import com.digium.respokesdk.RespokeEndpoint;
import com.digium.respokesdk.RespokeGroup;
import com.digium.respokesdk.RespokeGroupMessage;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;


public class GroupTests extends RespokeTestCase implements RespokeClient.Listener, RespokeGroup.Listener {

    private final static String TEST_GROUP_MESSAGE = "What's going on in this group?";
    private String firstTestEndpointID;
    private String secondTestEndpointID;
    private RespokeGroup firstClientGroup;
    private RespokeGroup secondClientGroup;
    private boolean callbackSucceeded;
    private boolean membershipChanged;
    private boolean messageReceived;
    private boolean clientMessageReceived;
    private RespokeEndpoint secondEndpoint;


    public void testGroupMembershipAndMessaging() {
        // Create a client to test with
        firstTestEndpointID = generateTestEndpointID();
        final RespokeClient firstClient = createTestClient(firstTestEndpointID, this);

        // Create a second client to test with
        secondTestEndpointID = generateTestEndpointID();
        final RespokeClient secondClient = createTestClient(secondTestEndpointID, this);


        // Have each client join the same group and discover each other


        String testGroupID = "group" + generateTestEndpointID();

        asyncTaskDone = false;
        ArrayList<String> groupList = new ArrayList<String>();
        groupList.add(testGroupID);
        firstClient.joinGroups(groupList, new RespokeClient.JoinGroupCompletionListener() {
            @Override
            public void onSuccess(ArrayList<RespokeGroup> groups) {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
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

        assertTrue("Should return the same group instance", firstClientGroup == firstClient.getGroup(testGroupID));

        // Get the list of group members while firstClient is the only one there

        asyncTaskDone = false;
        firstClientGroup.getMembers(new RespokeGroup.GetGroupMembersCompletionListener() {
            @Override
            public void onSuccess(ArrayList<RespokeConnection> memberArray) {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
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
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
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
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
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
        clientMessageReceived = false;
        secondClientGroup.sendMessage(TEST_GROUP_MESSAGE, false, new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                callbackSucceeded = true;
                asyncTaskDone = messageReceived && clientMessageReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully send a group message. Error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Group.onMessage listener should be called", messageReceived);
        assertTrue("Client.onMessage listener should be called", clientMessageReceived);

        // Test sending message with history and retrieving multi-group history
        asyncTaskDone = false;
        callbackSucceeded = false;
        messageReceived = false;
        secondClientGroup.sendMessage(TEST_GROUP_MESSAGE, false, true,
                new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                callbackSucceeded = true;
                asyncTaskDone = messageReceived && clientMessageReceived;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully send a group message. Error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Sending message with persistence timed out",
            waitForCompletion(RespokeTestCase.TEST_TIMEOUT));

        asyncTaskDone = false;
        final ArrayList<String> groupIds = new ArrayList<>();
        groupIds.add(firstClientGroup.getGroupID());
        firstClient.getGroupHistories(groupIds, new RespokeClient.GroupHistoriesCompletionListener() {
            @Override
            public void onSuccess(Map<String, List<RespokeGroupMessage>> groupsToMessages) {
                assertTrue("Result should not be null", groupsToMessages != null);

                final Object rawGroupMessages = groupsToMessages.get(firstClientGroup.getGroupID());

                assertTrue("Result should have an entry for the groupId in question", rawGroupMessages != null);
                assertTrue("Messages should be an ArrayList", rawGroupMessages instanceof ArrayList);

                final ArrayList<RespokeGroupMessage> groupMessages = (ArrayList<RespokeGroupMessage>) rawGroupMessages;

                assertTrue("Messages should have at least one message", groupMessages.size() != 0);

                final RespokeGroupMessage message = groupMessages.get(0);

                assertTrue("The message should be the one we sent", message.message.equals(TEST_GROUP_MESSAGE));
                assertTrue("The message should be associated with a group", message.group != null);
                assertTrue("The message should be associated with an endpoint", message.endpoint != null);
                assertTrue("The message should have a timestamp", message.timestamp != null);
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully retrieve group history. Error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Retrieving multi-group message history timed out",
                waitForCompletion(RespokeTestCase.TEST_TIMEOUT));


        // test retrieving single-group history
        asyncTaskDone = false;
        firstClient.getGroupHistory(firstClientGroup.getGroupID(),
                new RespokeClient.GroupHistoryCompletionListener() {
            @Override
            public void onSuccess(List<RespokeGroupMessage> messageList) {
                assertTrue("Result should not be null", messageList != null);

                assertTrue("Messages should have at least one message", messageList.size() != 0);

                final RespokeGroupMessage message = messageList.get(0);

                assertTrue("The message should be the one we sent", message.message.equals(TEST_GROUP_MESSAGE));
                assertTrue("The message should be associated with a group", message.group != null);
                assertTrue("The message should be associated with an endpoint", message.endpoint != null);
                assertTrue("The message should have a timestamp", message.timestamp != null);
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should successfully retrieve group history. Error:" + errorMessage, false);
                asyncTaskDone = true;
            }
        });

        assertTrue("Retrieving single-group message history timed out",
                waitForCompletion(RespokeTestCase.TEST_TIMEOUT));

        // test specifying limit and before when pulling single-group history
        asyncTaskDone = false;
        firstClient.getGroupHistory(firstClientGroup.getGroupID(), 1,
                new GregorianCalendar(1975, 3, 22).getTime(),
                new RespokeClient.GroupHistoryCompletionListener() {
                    @Override
                    public void onSuccess(List<RespokeGroupMessage> messageList) {
                        assertTrue("Result should not be null", messageList != null);
                        assertTrue("Messages should be empty", messageList.size() == 0);
                        asyncTaskDone = true;
                    }

                    @Override
                    public void onError(String errorMessage) {
                        assertTrue("Should successfully retrieve group history. Error:" + errorMessage, false);
                        asyncTaskDone = true;
                    }
                });

        assertTrue("Retrieving single-group message history with specific count and before timed out",
                waitForCompletion(RespokeTestCase.TEST_TIMEOUT));

        // Test receiving a leave notification
        asyncTaskDone = false;
        callbackSucceeded = false;
        membershipChanged = false;
        secondClientGroup.leave(new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
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

        // Test that you can rejoin the group using the same object
        asyncTaskDone = false;
        callbackSucceeded = false;
        membershipChanged = false;
        secondClientGroup.join(new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
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
        assertTrue("Join notification should have been received", membershipChanged);
        assertTrue("Should indicate group is joined", secondClientGroup.isJoined());
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


    public void onMessage(String message, RespokeEndpoint endpoint, RespokeGroup group, Date timestamp, Boolean didSend) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertNotNull("Message should not be null", message);
        assertTrue("Message should be correct", message.equals(TEST_GROUP_MESSAGE));
        assertTrue("Should reference the same endpoint object that sent the message", endpoint == secondEndpoint);
        assertTrue("Should reference the same group that the message was sent to", group == firstClientGroup);
        assertNotNull("Should include a timestamp", timestamp);
        clientMessageReceived = true;
        asyncTaskDone = callbackSucceeded && messageReceived;
    }


    // RespokeGroup.Listener methods


    public void onJoin(RespokeConnection connection, RespokeGroup sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Sender should be correct", sender == firstClientGroup);
        assertNotNull("Connection should not be nil", connection);
        assertTrue("Connection should be associated with the correct endpoint ID", secondTestEndpointID.equals(connection.getEndpoint().getEndpointID()));
        membershipChanged = true;
        asyncTaskDone = callbackSucceeded;
    }


    public void onLeave(RespokeConnection connection, RespokeGroup sender) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertTrue("Sender should be correct", sender == firstClientGroup);
        assertNotNull("Connection should not be nil", connection);
        assertTrue("Connection should be associated with the correct endpoint ID", connection.getEndpoint().getEndpointID().equals(secondTestEndpointID));
        membershipChanged = true;
        asyncTaskDone = callbackSucceeded;
    }


    public void onGroupMessage(String message, RespokeEndpoint endpoint, RespokeGroup sender, Date timestamp) {
        assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
        assertNotNull("Message should not be null", message);
        assertTrue("Message should be correct", message.equals(TEST_GROUP_MESSAGE));
        assertTrue("Should reference the same endpoint object object that sent the message", endpoint == secondEndpoint);
        assertTrue("Should reference the same group that the message was sent to", sender == firstClientGroup);
        assertNotNull("Should include a timestamp", timestamp);
        messageReceived = true;
        asyncTaskDone = callbackSucceeded && clientMessageReceived;
    }


}
