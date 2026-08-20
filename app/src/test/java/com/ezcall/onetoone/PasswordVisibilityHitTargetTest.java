package com.ezcall.onetoone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordVisibilityHitTargetTest {
    @Test
    public void acceptsTouchInsideTrailingIconArea() {
        assertTrue(PasswordVisibilityHitTarget.contains(280f, 300, 56));
    }

    @Test
    public void rejectsTouchInPasswordTextArea() {
        assertFalse(PasswordVisibilityHitTarget.contains(200f, 300, 56));
    }
}
