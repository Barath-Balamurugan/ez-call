package com.ezcall.onetoone;

final class PhoneVerificationRetryPolicy {
    private PhoneVerificationRetryPolicy() {
    }

    /**
     * True when the user can still fix a rejected phone credential themselves, by retyping
     * the code or requesting a new one. False means the attempt is unrecoverable and the
     * verification session should end.
     */
    static boolean allowsAnotherCodeAttempt(boolean invalidCredential, String errorCode) {
        if (invalidCredential) {
            return true;
        }
        String code = errorCode == null ? "" : errorCode.trim();
        return "ERROR_INVALID_VERIFICATION_CODE".equals(code)
                || "ERROR_INVALID_VERIFICATION_ID".equals(code)
                || "ERROR_SESSION_EXPIRED".equals(code);
    }
}
