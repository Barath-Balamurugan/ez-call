package com.ezcall.onetoone;

import android.text.InputType;

final class PasswordVisibilityState {
    private PasswordVisibilityState() {
    }

    static boolean isVisible(int inputType) {
        return (inputType & InputType.TYPE_MASK_VARIATION)
                == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    }
}
