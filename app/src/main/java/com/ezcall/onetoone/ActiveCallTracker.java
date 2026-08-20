package com.ezcall.onetoone;

final class ActiveCallTracker {
    private static String activeCallId = "";

    private ActiveCallTracker() {
    }

    static synchronized void markActive(String callId) {
        activeCallId = normalized(callId);
    }

    static synchronized String activeCallId() {
        return activeCallId;
    }

    static synchronized boolean isActive(String callId) {
        String candidate = normalized(callId);
        return !candidate.isEmpty() && candidate.equals(activeCallId);
    }

    static synchronized boolean hasDifferentActiveCall(String callId) {
        return !activeCallId.isEmpty() && !activeCallId.equals(normalized(callId));
    }

    static synchronized void clear(String callId) {
        if (activeCallId.equals(normalized(callId))) {
            activeCallId = "";
        }
    }

    private static String normalized(String callId) {
        return callId == null ? "" : callId.trim();
    }
}
