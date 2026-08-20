package com.ezcall.onetoone;

final class IncomingCallNotificationIds {
    static final int LEGACY_NOTIFICATION_ID = 1001;

    private static final int CALL_NOTIFICATION_ID_BASE = 10_000;
    private static final int CALL_NOTIFICATION_ID_RANGE = 1_000_000;

    private IncomingCallNotificationIds() {
    }

    static int forCallId(String callId) {
        if (callId == null || callId.trim().isEmpty()) {
            return LEGACY_NOTIFICATION_ID;
        }
        return CALL_NOTIFICATION_ID_BASE
                + Math.floorMod(callId.trim().hashCode(), CALL_NOTIFICATION_ID_RANGE);
    }
}
