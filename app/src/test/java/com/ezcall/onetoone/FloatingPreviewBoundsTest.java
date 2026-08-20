package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FloatingPreviewBoundsTest {
    @Test
    public void keepsTranslationInsideStartEdge() {
        assertEquals(
                -40f,
                FloatingPreviewBounds.clampTranslation(
                        -200f,
                        56,
                        168,
                        400,
                        16,
                        16
                ),
                0.001f
        );
    }

    @Test
    public void keepsTranslationInsideEndEdge() {
        assertEquals(
                96f,
                FloatingPreviewBounds.clampTranslation(
                        200f,
                        176,
                        288,
                        400,
                        16,
                        16
                ),
                0.001f
        );
    }

    @Test
    public void preservesTranslationThatIsAlreadyVisible() {
        assertEquals(
                24f,
                FloatingPreviewBounds.clampTranslation(
                        24f,
                        176,
                        288,
                        400,
                        16,
                        16
                ),
                0.001f
        );
    }
}
