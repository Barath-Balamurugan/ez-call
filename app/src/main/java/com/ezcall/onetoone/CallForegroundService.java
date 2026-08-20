package com.ezcall.onetoone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class CallForegroundService extends Service {
    private static final int NOTIFICATION_ID = 2001;
    private static final String EXTRA_INCOMING = "call_service_incoming";
    private static final String EXTRA_CONNECTED = "call_service_connected";

    static void start(
            Context context,
            String contactName,
            String phoneNumber,
            String callId,
            boolean incomingCall,
            boolean connectedCall
    ) {
        Intent intent = new Intent(context, CallForegroundService.class);
        intent.putExtra(MainActivity.EXTRA_NAME, contactName);
        intent.putExtra(MainActivity.EXTRA_PHONE_NUMBER, phoneNumber);
        intent.putExtra(MainActivity.EXTRA_CALL_ID, callId);
        intent.putExtra(EXTRA_INCOMING, incomingCall);
        intent.putExtra(EXTRA_CONNECTED, connectedCall);
        context.startForegroundService(intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, CallForegroundService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String contactName = valueOrDefault(intent.getStringExtra(MainActivity.EXTRA_NAME), "your contact");
        String phoneNumber = valueOrDefault(
                intent.getStringExtra(MainActivity.EXTRA_PHONE_NUMBER),
                "unknown number"
        );
        String callId = valueOrDefault(
                intent.getStringExtra(MainActivity.EXTRA_CALL_ID),
                "memory-calls-default"
        );
        boolean incomingCall = intent.getBooleanExtra(EXTRA_INCOMING, false);
        boolean connectedCall = intent.getBooleanExtra(EXTRA_CONNECTED, false);

        createNotificationChannel();
        Notification notification = buildNotification(
                contactName,
                phoneNumber,
                callId,
                incomingCall,
                connectedCall
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification(
            String contactName,
            String phoneNumber,
            String callId,
            boolean incomingCall,
            boolean connectedCall
    ) {
        Intent returnIntent = new Intent(this, VideoCallActivity.class);
        returnIntent.putExtra(MainActivity.EXTRA_NAME, contactName);
        returnIntent.putExtra(MainActivity.EXTRA_PHONE_NUMBER, phoneNumber);
        returnIntent.putExtra(MainActivity.EXTRA_CALL_ID, callId);
        returnIntent.putExtra(MainActivity.EXTRA_INCOMING_CALL, incomingCall);
        returnIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_ID,
                returnIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent endIntent = new Intent(this, IncomingCallActionReceiver.class);
        endIntent.setAction(IncomingCallActionReceiver.ACTION_END);
        endIntent.putExtra(MainActivity.EXTRA_CALL_ID, callId);
        endIntent.putExtra(
                IncomingCallActionReceiver.EXTRA_PRE_ANSWER,
                !incomingCall && !connectedCall
        );
        PendingIntent endPendingIntent = PendingIntent.getBroadcast(
                this,
                NOTIFICATION_ID ^ callId.hashCode(),
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(
                this,
                getString(R.string.ongoing_call_channel_id)
        )
                .setSmallIcon(R.drawable.ic_notification_call)
                .setContentTitle("Call with " + contactName)
                .setContentText(incomingCall ? "Video call in progress" : "Calling " + contactName)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person contact = new Person.Builder()
                    .setName(contactName)
                    .setImportant(true)
                    .build();
            builder.setStyle(Notification.CallStyle.forOngoingCall(contact, endPendingIntent))
                    .addPerson(contact);
        } else {
            builder.addAction(R.drawable.ic_call_end, "End call", endPendingIntent);
        }
        return builder.build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                getString(R.string.ongoing_call_channel_id),
                "Ongoing calls",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps EZ Call audio active while a call is in progress.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
