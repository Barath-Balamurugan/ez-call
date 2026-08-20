package com.ezcall.onetoone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallSessionGuardTest {
    @Test
    public void suppressesRecentlyFinishedMatchingCall() {
        long finishedAt = 1_000L;

        assertTrue(CallSessionGuard.isSuppressed(
                "call-123",
                "call-123",
                finishedAt,
                finishedAt + CallSessionGuard.SUPPRESSION_WINDOW_MILLIS
        ));
    }

    @Test
    public void allowsDifferentCall() {
        assertFalse(CallSessionGuard.isSuppressed(
                "call-456",
                "call-123",
                1_000L,
                2_000L
        ));
    }

    @Test
    public void allowsMatchingCallAfterSuppressionExpires() {
        long finishedAt = 1_000L;

        assertFalse(CallSessionGuard.isSuppressed(
                "call-123",
                "call-123",
                finishedAt,
                finishedAt + CallSessionGuard.SUPPRESSION_WINDOW_MILLIS + 1
        ));
    }
}
