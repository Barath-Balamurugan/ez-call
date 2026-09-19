package com.ezcall.onetoone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneVerificationPromptTest {
    @Test
    public void wrongOrExpiredCodeKeepsTheDialogOpenForAnotherAttempt() {
        // FirebaseAuthInvalidCredentialsException covers a mistyped code.
        assertTrue(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(true, ""));
        assertTrue(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(
                true,
                "ERROR_INVALID_VERIFICATION_CODE"
        ));
        // An expired session is recoverable through the dialog's Resend button.
        assertTrue(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(
                false,
                "ERROR_SESSION_EXPIRED"
        ));
        assertTrue(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(
                false,
                "ERROR_INVALID_VERIFICATION_ID"
        ));
    }

    @Test
    public void aNumberOwnedByAnotherAccountEndsTheSession() {
        assertFalse(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(
                false,
                "ERROR_CREDENTIAL_ALREADY_IN_USE"
        ));
        assertFalse(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(
                false,
                "ERROR_PHONE_NUMBER_ALREADY_EXISTS"
        ));
    }

    @Test
    public void quotaAndUnknownFailuresEndTheSession() {
        assertFalse(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(false, ""));
        assertFalse(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(false, null));
        assertFalse(PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(
                false,
                "ERROR_TOO_MANY_REQUESTS"
        ));
    }

    @Test
    public void anUnchangedNumberSkipsTheSmsRoundTrip() {
        assertTrue(PhoneVerificationPrompt.sameNumber("+1 617 555 0123", "+16175550123"));
        assertTrue(PhoneVerificationPrompt.sameNumber("+16175550123", "+16175550123"));
    }

    @Test
    public void aChangedOrMissingNumberRequiresVerification() {
        assertFalse(PhoneVerificationPrompt.sameNumber("+16175550123", "+16175550124"));
        // An account with no phone provider yet must still verify before claiming a number.
        assertFalse(PhoneVerificationPrompt.sameNumber("+16175550123", null));
        assertFalse(PhoneVerificationPrompt.sameNumber("+16175550123", ""));
        assertFalse(PhoneVerificationPrompt.sameNumber(null, "+16175550123"));
    }
}
