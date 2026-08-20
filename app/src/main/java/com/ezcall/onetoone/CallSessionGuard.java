package com.ezcall.onetoone;

import android.content.Context;
import android.content.SharedPreferences;

final class CallSessionGuard {
    private static final String PREFS_NAME = "ez_call_session_guard";
    private static final String KEY_FINISHED_CALL_ID = "finished_call_id";
    private static final String KEY_FINISHED_AT_MILLIS = "finished_at_millis";
    static final long SUPPRESSION_WINDOW_MILLIS = 5 * 60 * 1000L;

    private CallSessionGuard() {
    }

    static void suppressFinishedCall(Context context, String callId) {
        if (callId == null || callId.trim().isEmpty()) {
            return;
        }
        preferences(context).edit()
                .putString(KEY_FINISHED_CALL_ID, callId)
                .putLong(KEY_FINISHED_AT_MILLIS, System.currentTimeMillis())
                .apply();
    }

    static boolean shouldIgnoreIncomingCall(Context context, String callId) {
        SharedPreferences preferences = preferences(context);
        String finishedCallId = preferences.getString(KEY_FINISHED_CALL_ID, "");
        long finishedAtMillis = preferences.getLong(KEY_FINISHED_AT_MILLIS, 0L);
        long nowMillis = System.currentTimeMillis();
        boolean shouldIgnore = isSuppressed(
                callId,
                finishedCallId,
                finishedAtMillis,
                nowMillis
        );
        if (!shouldIgnore && nowMillis - finishedAtMillis > SUPPRESSION_WINDOW_MILLIS) {
            preferences.edit()
                    .remove(KEY_FINISHED_CALL_ID)
                    .remove(KEY_FINISHED_AT_MILLIS)
                    .apply();
        }
        return shouldIgnore;
    }

    static boolean isSuppressed(
            String candidateCallId,
            String finishedCallId,
            long finishedAtMillis,
            long nowMillis
    ) {
        return candidateCallId != null
                && !candidateCallId.trim().isEmpty()
                && candidateCallId.equals(finishedCallId)
                && finishedAtMillis > 0
                && nowMillis >= finishedAtMillis
                && nowMillis - finishedAtMillis <= SUPPRESSION_WINDOW_MILLIS;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
