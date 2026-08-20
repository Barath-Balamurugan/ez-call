package com.ezcall.onetoone;

import android.telecom.DisconnectCause;

final class TelecomDisconnectPolicy {
    private TelecomDisconnectPolicy() {
    }

    static boolean shouldDisconnectForInviteStatus(String status) {
        return "declined".equals(status)
                || "missed".equals(status)
                || "ended".equals(status)
                || "notification_failed".equals(status)
                || "callee_not_registered".equals(status)
                || "callee_missing_uid".equals(status)
                || "callee_missing_fcm_token".equals(status);
    }

    static int causeForInviteStatus(String status, int fallbackCause) {
        if ("declined".equals(status)) {
            return DisconnectCause.REJECTED;
        }
        if ("missed".equals(status)) {
            return DisconnectCause.MISSED;
        }
        if ("notification_failed".equals(status)
                || "callee_not_registered".equals(status)
                || "callee_missing_uid".equals(status)
                || "callee_missing_fcm_token".equals(status)) {
            return DisconnectCause.ERROR;
        }
        return fallbackCause;
    }

    static String inviteStatusForSystemDisconnect(
            int disconnectCode,
            boolean incomingCall,
            boolean connected
    ) {
        if (disconnectCode == DisconnectCause.REJECTED) {
            return "declined";
        }
        if (disconnectCode == DisconnectCause.MISSED) {
            return "missed";
        }
        return connected ? "ended" : CallEndStatus.forLocalExit(incomingCall, false);
    }
}
