package com.ezcall.onetoone;

final class PhoneProfileOwnership {
    static final String ALREADY_REGISTERED_MESSAGE = "Already registered phone number.";

    private PhoneProfileOwnership() {
    }

    static boolean belongsToDifferentUser(
            boolean profileExists,
            String existingUid,
            String requestedUid
    ) {
        if (!profileExists) {
            return false;
        }
        String existing = existingUid == null ? "" : existingUid.trim();
        String requested = requestedUid == null ? "" : requestedUid.trim();
        return !existing.equals(requested);
    }

    static AlreadyRegisteredException alreadyRegisteredError() {
        return new AlreadyRegisteredException();
    }

    static final class AlreadyRegisteredException extends IllegalStateException {
        AlreadyRegisteredException() {
            super(ALREADY_REGISTERED_MESSAGE);
        }
    }
}
