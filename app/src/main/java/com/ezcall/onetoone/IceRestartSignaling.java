package com.ezcall.onetoone;

final class IceRestartSignaling {
    static final int INITIAL_GENERATION = 1;

    private IceRestartSignaling() {
    }

    static int generation(Object value) {
        int parsed = intValue(value, INITIAL_GENERATION);
        return Math.max(INITIAL_GENERATION, parsed);
    }

    static boolean shouldHandleOffer(int incomingGeneration, int handledGeneration, int handlingGeneration) {
        return incomingGeneration > handledGeneration && incomingGeneration != handlingGeneration;
    }

    static boolean shouldHandleAnswer(
            int incomingGeneration,
            int localOfferGeneration,
            int handledGeneration,
            int handlingGeneration
    ) {
        return incomingGeneration == localOfferGeneration
                && incomingGeneration > handledGeneration
                && incomingGeneration != handlingGeneration;
    }

    static boolean candidateMatches(int candidateGeneration, int remoteDescriptionGeneration) {
        return candidateGeneration == remoteDescriptionGeneration;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
