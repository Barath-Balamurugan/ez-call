package com.ezcall.onetoone;

final class FloatingPreviewBounds {
    private FloatingPreviewBounds() {
    }

    static float clampTranslation(
            float proposedTranslation,
            int viewStart,
            int viewEnd,
            int parentSize,
            int startInset,
            int endInset
    ) {
        float minimum = startInset - viewStart;
        float maximum = parentSize - endInset - viewEnd;
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, proposedTranslation));
    }
}
