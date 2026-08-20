package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class IncomingCallNotificationIdTest {
    @Test
    public void callIdProducesStableNotificationId() {
        assertEquals(
                IncomingCallNotificationIds.forCallId("call-123"),
                IncomingCallNotificationIds.forCallId("call-123")
        );
    }

    @Test
    public void differentCallsUseDifferentNotificationIds() {
        assertNotEquals(
                IncomingCallNotificationIds.forCallId("call-123"),
                IncomingCallNotificationIds.forCallId("call-456")
        );
    }

    @Test
    public void missingCallIdUsesLegacyFallback() {
        assertEquals(
                IncomingCallNotificationIds.LEGACY_NOTIFICATION_ID,
                IncomingCallNotificationIds.forCallId("")
        );
    }
}
