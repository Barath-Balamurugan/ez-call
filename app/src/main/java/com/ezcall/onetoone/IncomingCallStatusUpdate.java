package com.ezcall.onetoone;

final class IncomingCallStatusUpdate {
    private IncomingCallStatusUpdate() {
    }

    static boolean shouldPresentIncomingCall(String status) {
        return status == null
                || status.trim().isEmpty()
                || "ringing".equals(status)
                || "sent".equals(status)
                || "delivered".equals(status);
    }

    static boolean shouldDismissRinging(String status) {
        return !shouldPresentIncomingCall(status);
    }

    static boolean shouldShowMissedCall(String status) {
        return "missed".equals(status);
    }
}
