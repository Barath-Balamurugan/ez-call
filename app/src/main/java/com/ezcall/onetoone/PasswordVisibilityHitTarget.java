package com.ezcall.onetoone;

final class PasswordVisibilityHitTarget {
    private PasswordVisibilityHitTarget() {
    }

    static boolean contains(float touchX, int fieldWidth, int totalEndPadding) {
        if (fieldWidth <= 0 || totalEndPadding <= 0) {
            return false;
        }
        return touchX >= fieldWidth - totalEndPadding && touchX <= fieldWidth;
    }
}
