package com.ezcall.onetoone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputType;

import org.junit.Test;

public class PasswordVisibilityStateTest {
    @Test
    public void hiddenPasswordIsNotReportedAsVisible() {
        assertFalse(PasswordVisibilityState.isVisible(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        ));
    }

    @Test
    public void visiblePasswordIsReportedAsVisible() {
        assertTrue(PasswordVisibilityState.isVisible(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        ));
    }
}
