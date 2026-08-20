package com.ezcall.onetoone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IncomingCallStatusUpdateTest {
    @Test
    public void activeInviteStatusesCanStillRing() {
        assertTrue(IncomingCallStatusUpdate.shouldPresentIncomingCall(""));
        assertTrue(IncomingCallStatusUpdate.shouldPresentIncomingCall("ringing"));
        assertTrue(IncomingCallStatusUpdate.shouldPresentIncomingCall("sent"));
        assertTrue(IncomingCallStatusUpdate.shouldPresentIncomingCall("delivered"));
    }

    @Test
    public void terminalStatusesDismissRinging() {
        assertTrue(IncomingCallStatusUpdate.shouldDismissRinging("missed"));
        assertTrue(IncomingCallStatusUpdate.shouldDismissRinging("ended"));
        assertTrue(IncomingCallStatusUpdate.shouldDismissRinging("declined"));
        assertTrue(IncomingCallStatusUpdate.shouldDismissRinging("answered"));
    }

    @Test
    public void onlyMissedStatusCreatesMissedCallNotification() {
        assertTrue(IncomingCallStatusUpdate.shouldShowMissedCall("missed"));
        assertFalse(IncomingCallStatusUpdate.shouldShowMissedCall("ended"));
        assertFalse(IncomingCallStatusUpdate.shouldShowMissedCall("declined"));
    }
}
