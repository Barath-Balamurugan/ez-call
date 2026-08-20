package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

public class AppThemePaletteTest {
    @Test
    public void lightThemeMapsBackgroundAndTextTokens() {
        assertEquals("#FAF9FD", AppThemePalette.lightToken("#07080F"));
        assertEquals("#191720", AppThemePalette.lightToken("#FFFFFF"));
        assertEquals("#0C000000", AppThemePalette.lightToken("#10FFFFFF"));
    }

    @Test
    public void lightThemePreservesBrandColors() {
        assertEquals("#33FF64", AppThemePalette.lightToken("#33FF64"));
        assertEquals("#F95830", AppThemePalette.lightToken("#F95830"));
    }

    @Test
    public void accentPaletteProvidesFiveUniqueChoices() {
        String[] keys = AppThemePalette.accentKeys();
        HashSet<String> colors = new HashSet<>();
        for (String key : keys) {
            colors.add(AppThemePalette.accentPrimary(key));
        }

        assertEquals(5, keys.length);
        assertEquals(5, colors.size());
        assertEquals(
                Arrays.asList("brand", "ocean", "sky", "teal", "emerald"),
                Arrays.asList(keys)
        );
    }

    @Test
    public void accentTokensUseSelectedPaletteAndUnknownValuesFallbackToBrand() {
        assertEquals("#367BF5", AppThemePalette.accentToken("ocean", "#7C5CFC"));
        assertEquals("#1D4ED8", AppThemePalette.accentToken("ocean", "#4F35CC"));
        assertEquals("#26367BF5", AppThemePalette.accentToken("ocean", "#267C5CFC"));
        assertEquals("#55367BF5", AppThemePalette.accentToken("ocean", "#557C5CFC"));
        assertEquals("#33FF64", AppThemePalette.accentPrimary("unknown"));
    }

}
