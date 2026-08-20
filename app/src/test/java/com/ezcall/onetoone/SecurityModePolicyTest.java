package com.ezcall.onetoone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SecurityModePolicyTest {
    @Test
    public void allowsEveryCallerWhenSecurityModeIsOff() {
        assertTrue(SecurityModePolicy.isCallerAllowed(
                false,
                false
        ));
    }

    @Test
    public void allowsSavedContactWhenSecurityModeIsOn() {
        assertTrue(SecurityModePolicy.isCallerAllowed(
                true,
                true
        ));
    }

    @Test
    public void blocksUnknownCallerWhenSecurityModeIsOn() {
        assertFalse(SecurityModePolicy.isCallerAllowed(
                true,
                false
        ));
    }
}
