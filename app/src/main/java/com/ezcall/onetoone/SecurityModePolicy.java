package com.ezcall.onetoone;

final class SecurityModePolicy {
    private SecurityModePolicy() {
    }

    static boolean isCallerAllowed(
            boolean securityModeEnabled,
            boolean callerIsInReceiverContacts
    ) {
        return !securityModeEnabled || callerIsInReceiverContacts;
    }
}
