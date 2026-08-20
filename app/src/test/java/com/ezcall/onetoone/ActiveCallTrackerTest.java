package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class ActiveCallTrackerTest {
    @After
    public void clearTracker() {
        ActiveCallTracker.clear(ActiveCallTracker.activeCallId());
    }

    @Test
    public void distinguishesCurrentCallFromWaitingCall() {
        ActiveCallTracker.markActive("active-call");

        assertTrue(ActiveCallTracker.isActive("active-call"));
        assertFalse(ActiveCallTracker.hasDifferentActiveCall("active-call"));
        assertTrue(ActiveCallTracker.hasDifferentActiveCall("waiting-call"));
    }

    @Test
    public void onlyMatchingCallCanClearTracker() {
        ActiveCallTracker.markActive("active-call");

        ActiveCallTracker.clear("another-call");
        assertEquals("active-call", ActiveCallTracker.activeCallId());

        ActiveCallTracker.clear("active-call");
        assertEquals("", ActiveCallTracker.activeCallId());
    }
}
