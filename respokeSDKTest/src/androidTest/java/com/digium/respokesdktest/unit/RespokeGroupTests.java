package com.digium.respokesdktest.unit;

import com.digium.respokesdk.Respoke;
import com.digium.respokesdk.RespokeClient;
import com.digium.respokesdk.RespokeConnection;
import com.digium.respokesdk.RespokeGroup;
import com.digium.respokesdktest.RespokeTestCase;

import java.util.ArrayList;

/**
 * Created by jasonadams on 1/26/15.
 */
public class RespokeGroupTests extends RespokeTestCase {

    private boolean callbackDidSucceed;


    public void testUnconnectedGroupBehaviour() {
        RespokeClient client = Respoke.sharedInstance().createClient(getContext());
        assertNotNull(client);

        String testGroupID = "myGroupID";
        RespokeGroup group = new RespokeGroup(testGroupID, "dummyTOKEN", null, client);

        assertNotNull("Group should not be null", group);
        assertTrue("Should indicate group is not joined", !group.isJoined());

        callbackDidSucceed = false;
        asyncTaskDone = false;
        group.sendMessage("A message", new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Should not send a message to a group that is not joined", false);
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                assertTrue("Should return the correct error message", errorMessage.equals("Not a member of this group anymore."));
                callbackDidSucceed = true;
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Should have called the error handler", callbackDidSucceed);

        callbackDidSucceed = false;
        asyncTaskDone = false;

        group.leave(new Respoke.TaskCompletionListener() {
            @Override
            public void onSuccess() {
                assertTrue("Leaving an unjoined group should fail", false);
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                assertTrue("Should return the correct error message", "Not a member of this group anymore.".equals(errorMessage));
                callbackDidSucceed = true;
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Should have called the error handler", callbackDidSucceed);

        callbackDidSucceed = false;
        asyncTaskDone = false;
        group.getMembers(new RespokeGroup.GetGroupMembersCompletionListener() {
            @Override
            public void onSuccess(ArrayList<RespokeConnection> memberArray) {
                assertTrue("Getting members of an unjoined group should fail", false);
                asyncTaskDone = true;
            }

            @Override
            public void onError(String errorMessage) {
                assertTrue("Should be called in UI thread", RespokeTestCase.currentlyOnUIThread());
                assertTrue("Should return the correct error message", errorMessage.equals("Not a member of this group anymore."));
                callbackDidSucceed = true;
                asyncTaskDone = true;
            }
        });

        assertTrue("Test timed out", waitForCompletion(RespokeTestCase.TEST_TIMEOUT));
        assertTrue("Should have called the error handler", callbackDidSucceed);
    }
}
