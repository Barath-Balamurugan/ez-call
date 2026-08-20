package com.ezcall.onetoone;

final class CallEndStatus {
    private CallEndStatus() {
    }

    static String forLocalExit(boolean incomingCall, boolean connected) {
        if (connected) {
            return "ended";
        }
        return incomingCall ? "declined" : "missed";
    }
}
