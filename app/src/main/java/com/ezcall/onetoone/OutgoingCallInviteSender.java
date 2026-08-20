package com.ezcall.onetoone;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;

final class OutgoingCallInviteSender {
    private static final String TAG = "OutgoingCallInvite";

    private OutgoingCallInviteSender() {
    }

    static Task<Void> send(
            Context context,
            String contactName,
            String phoneNumber,
            String contactUid,
            String callId
    ) {
        Log.i(
                TAG,
                "Outgoing video call invite for " + contactName
                        + " at " + phoneNumber
                        + " using call " + callId
        );
        return FirebaseCallRepository.createOutgoingCallInvite(
                context,
                FirebaseCallRepository.currentPhoneNumberOrFallback(context),
                contactName,
                phoneNumber,
                contactUid,
                callId
        );
    }
}
