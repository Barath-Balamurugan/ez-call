package com.ezcall.onetoone;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telecom.DisconnectCause;

import java.util.concurrent.atomic.AtomicBoolean;

public class IncomingCallActionReceiver extends BroadcastReceiver {
    static final String ACTION_DECLINE = "com.ezcall.onetoone.action.DECLINE_CALL";
    static final String ACTION_END = "com.ezcall.onetoone.action.END_CALL";
    static final String EXTRA_PRE_ANSWER = "call_action_pre_answer";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || (!ACTION_DECLINE.equals(intent.getAction())
                && !ACTION_END.equals(intent.getAction()))) {
            return;
        }
        boolean declining = ACTION_DECLINE.equals(intent.getAction());
        boolean preAnswer = intent.getBooleanExtra(EXTRA_PRE_ANSWER, false);
        String callId = intent.getStringExtra(MainActivity.EXTRA_CALL_ID);
        int notificationId = intent.getIntExtra(
                MainActivity.EXTRA_INCOMING_NOTIFICATION_ID,
                IncomingCallNotificationIds.forCallId(callId)
        );
        if (callId != null && !callId.trim().isEmpty()) {
            PendingResult pendingResult = goAsync();
            AtomicBoolean finished = new AtomicBoolean(false);
            Runnable finishOnce = () -> {
                if (finished.compareAndSet(false, true)) {
                    pendingResult.finish();
                }
            };
            FirebaseCallRepository.markCallInviteStatus(
                            context,
                            callId,
                            declining ? "declined" : preAnswer ? "missed" : "ended"
                    )
                    .addOnCompleteListener(unused -> finishOnce.run());
            new Handler(Looper.getMainLooper()).postDelayed(finishOnce, 8_000L);
            EzCallTelecomManager.disconnect(
                    callId,
                    declining
                            ? DisconnectCause.REJECTED
                            : preAnswer ? DisconnectCause.MISSED : DisconnectCause.LOCAL
            );
            CallSessionGuard.suppressFinishedCall(context, callId);
            ActiveCallTracker.clear(callId);
        }
        context.getSystemService(NotificationManager.class)
                .cancel(notificationId);
        if (!declining) {
            CallForegroundService.stop(context);
        }
        CallSoundPlayer.playEnded();
    }
}
