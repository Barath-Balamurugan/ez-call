package com.ezcall.onetoone;

import java.util.Locale;

final class AppThemePalette {
    static final String ACCENT_BRAND = "brand";
    static final String ACCENT_OCEAN = "ocean";
    static final String ACCENT_SKY = "sky";
    static final String ACCENT_TEAL = "teal";
    static final String ACCENT_EMERALD = "emerald";

    private static final Accent[] ACCENTS = {
            new Accent(ACCENT_BRAND, "EZ Green", "#33FF64", "#62FE8A", "#ADFB9C"),
            new Accent(ACCENT_OCEAN, "Ocean", "#367BF5", "#1D4ED8", "#6EA0FF"),
            new Accent(ACCENT_SKY, "Sky", "#168FC7", "#08638F", "#4DB9E8"),
            new Accent(ACCENT_TEAL, "Teal", "#0F9E9A", "#0A6F73", "#43C7C2"),
            new Accent(ACCENT_EMERALD, "Emerald", "#1A9C68", "#0F6F4A", "#4CCB94")
    };

    private AppThemePalette() {
    }

    static String[] accentKeys() {
        String[] keys = new String[ACCENTS.length];
        for (int index = 0; index < ACCENTS.length; index++) {
            keys[index] = ACCENTS[index].key;
        }
        return keys;
    }

    static String normalizeAccent(String key) {
        return accent(key).key;
    }

    static String accentLabel(String key) {
        return accent(key).label;
    }

    static String accentPrimary(String key) {
        return accent(key).primary;
    }

    static String accentToken(String key, String token) {
        if (token == null) {
            return "#000000";
        }
        Accent selected = accent(key);
        switch (token.toUpperCase(Locale.ROOT)) {
            case "#7C5CFC":
                return selected.primary;
            case "#4F35CC":
                return selected.dark;
            case "#9C86FF":
            case "#9D83FF":
            case "#A99AFF":
                return selected.soft;
            case "#267C5CFC":
                return withAlpha(selected.primary, "26");
            case "#557C5CFC":
                return withAlpha(selected.primary, "55");
            case "#667C5CFC":
                return withAlpha(selected.primary, "66");
            default:
                return token;
        }
    }

    private static Accent accent(String key) {
        if (key != null) {
            for (Accent accent : ACCENTS) {
                if (accent.key.equals(key)) {
                    return accent;
                }
            }
        }
        return ACCENTS[0];
    }

    private static String withAlpha(String color, String alpha) {
        return "#" + alpha + color.substring(1);
    }

    static String lightToken(String darkToken) {
        if (darkToken == null) {
            return "#000000";
        }
        switch (darkToken.toUpperCase(Locale.ROOT)) {
            case "#05060C":
                return "#F0EEF5";
            case "#07080F":
            case "#070812":
                return "#FAF9FD";
            case "#080910":
                return "#F6F4F9";
            case "#0C0D1E":
                return "#FFFFFF";
            case "#0F101C":
            case "#111827":
                return "#FFFFFF";
            case "#F20B0C17":
                return "#FAFFFFFF";
            case "#BF05060C":
                return "#E8FFFFFF";
            case "#08FFFFFF":
                return "#08000000";
            case "#0FFFFFFF":
                return "#0A000000";
            case "#10FFFFFF":
                return "#0C000000";
            case "#12FFFFFF":
                return "#0E000000";
            case "#14FFFFFF":
                return "#10000000";
            case "#18FFFFFF":
                return "#12000000";
            case "#1AFFFFFF":
                return "#16000000";
            case "#20FFFFFF":
                return "#18000000";
            case "#22FFFFFF":
                return "#1A000000";
            case "#26FFFFFF":
                return "#20000000";
            case "#20202A":
                return "#ECE9F2";
            case "#2B3545":
                return "#D8D4E2";
            case "#454451":
                return "#ACA8B4";
            case "#FFFFFF":
                return "#191720";
            case "#E5E7EB":
                return "#2D2A34";
            case "#D8D5E0":
                return "#3B3842";
            case "#CBC8D4":
                return "#494650";
            case "#C7D2E1":
                return "#5D5968";
            case "#B5B3BE":
                return "#807C88";
            case "#A5A2B0":
                return "#5E5B66";
            case "#8E8B99":
                return "#64616D";
            case "#85838E":
                return "#62606B";
            case "#777582":
            case "#777681":
                return "#6F6B78";
            case "#666570":
                return "#73707B";
            case "#0F1F1A":
                return "#ECF8F1";
            case "#1C5F43":
                return "#9DD7BD";
            default:
                return darkToken;
        }
    }

    private static final class Accent {
        final String key;
        final String label;
        final String primary;
        final String dark;
        final String soft;

        Accent(String key, String label, String primary, String dark, String soft) {
            this.key = key;
            this.label = label;
            this.primary = primary;
            this.dark = dark;
            this.soft = soft;
        }
    }
}
