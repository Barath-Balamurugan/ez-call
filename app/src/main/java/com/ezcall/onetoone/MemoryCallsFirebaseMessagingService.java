package com.ezcall.onetoone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.app.Person;
import android.provider.Settings;
import android.telecom.DisconnectCause;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MemoryCallsFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "IncomingCallPush";
    private static final String NOTIFICATION_STATE_PREFERENCES =
            "incoming_call_notification_state";
    private static final String ACTIVE_NOTIFICATION_CALL_ID = "active_call_id";
    private static final String ACTIVE_NOTIFICATION_ID = "active_notification_id";
    private static final long INCOMING_CALL_TIMEOUT_MILLIS = 120_000L;
    private static final long PHOTO_LOOKUP_TIMEOUT_MILLIS = 350L;
    private static final int NOTIFICATION_PHOTO_SIZE_PX = 256;
    private static final int ANSWER_COLOR = Color.rgb(48, 209, 88);
    private static final int DECLINE_COLOR = Color.rgb(255, 59, 48);

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        FirebaseCallRepository.registerDeviceForPhoneNumber(
                this,
                FirebaseCallRepository.currentPhoneNumberOrFallback(this)
        );
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        long receivedAtMillis = System.currentTimeMillis();
        Map<String, String> data = message.getData();
        String messageType = valueOrDefault(data.get("type"), "");
        if ("call_status".equals(messageType)) {
            handleCallStatusUpdate(data);
            return;
        }
        if (!"incoming_call".equals(messageType)) {
            return;
        }

        String callerPhoneNumber = valueOrDefault(data.get("callerPhoneNumber"), "Unknown number");
        DeviceContactNumbers.Snapshot deviceContacts =
                DeviceContactNumbers.readSnapshot(getContentResolver());
        String callerName = DeviceContactNumbers.displayName(
                deviceContacts.displayNamesByNumber,
                callerPhoneNumber,
                valueOrDefault(data.get("callerName"), "Incoming caller")
        );
        String callerPhotoBase64 = valueOrDefault(data.get("callerPhotoBase64"), "");
        String callId = valueOrDefault(data.get("callId"), "memory-calls-default");
        int notificationId = IncomingCallNotificationIds.forCallId(callId);
        long pushSentAtMillis = longValue(data.get("pushSentAtMillis"));
        long inviteCreatedAtMillis = longValue(data.get("inviteCreatedAtMillis"));
        Log.i(TAG, "Incoming-call push received callId=" + callId
                + " fcmDeliveryMillis=" + elapsedMillis(receivedAtMillis, pushSentAtMillis)
                + " endToEndMillis=" + elapsedMillis(receivedAtMillis, inviteCreatedAtMillis)
                + " firebaseSentTimeMillis=" + message.getSentTime());
        if (CallSessionGuard.shouldIgnoreIncomingCall(this, callId)) {
            return;
        }
        if (ActiveCallTracker.isActive(callId)) {
            Log.i(TAG, "Ignoring duplicate push for active callId=" + callId);
            return;
        }
        if (!AppSettings.shouldAllowIncomingCall(
                this,
                callerPhoneNumber,
                deviceContacts.phoneNumbers
        )) {
            FirebaseCallRepository.markCallInviteStatus(this, callId, "declined")
                    .addOnFailureListener(error -> Log.w(
                            TAG,
                            "Could not decline a caller blocked by Security Mode",
                            error
                    ));
            return;
        }

        NotificationInviteSnapshot inviteSnapshot =
                loadNotificationInvite(callerPhotoBase64, callId);
        callerPhotoBase64 = inviteSnapshot.callerPhotoBase64;
        if (!IncomingCallStatusUpdate.shouldPresentIncomingCall(inviteSnapshot.status)) {
            handleCallStatusUpdate(
                    callId,
                    inviteSnapshot.status,
                    callerName,
                    callerPhotoBase64
            );
            return;
        }
        Intent intent = new Intent(this, VideoCallActivity.class);
        intent.putExtra(MainActivity.EXTRA_NAME, callerName);
        intent.putExtra(MainActivity.EXTRA_PHONE_NUMBER, callerPhoneNumber);
        intent.putExtra(MainActivity.EXTRA_PHOTO_BASE64, callerPhotoBase64);
        intent.putExtra(MainActivity.EXTRA_CALL_ID, callId);
        intent.putExtra(MainActivity.EXTRA_INCOMING_CALL, true);
        intent.putExtra(MainActivity.EXTRA_INCOMING_NOTIFICATION_ID, notificationId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        EzCallTelecomManager.registerIncoming(
                this,
                callId,
                callerName,
                callerPhoneNumber
        );

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        boolean callWaiting = ActiveCallTracker.hasDifferentActiveCall(callId);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                flags
        );

        Intent answerIntent = new Intent(intent);
        answerIntent.putExtra(MainActivity.EXTRA_AUTO_ACCEPT_INCOMING_CALL, true);
        answerIntent.putExtra(MainActivity.EXTRA_END_CURRENT_AND_ACCEPT, callWaiting);
        PendingIntent answerPendingIntent = PendingIntent.getActivity(
                this,
                notificationId ^ 0x10000000,
                answerIntent,
                flags
        );

        Intent declineIntent = new Intent(this, IncomingCallActionReceiver.class);
        declineIntent.setAction(IncomingCallActionReceiver.ACTION_DECLINE);
        declineIntent.putExtra(MainActivity.EXTRA_CALL_ID, callId);
        declineIntent.putExtra(MainActivity.EXTRA_INCOMING_NOTIFICATION_ID, notificationId);
        PendingIntent declinePendingIntent = PendingIntent.getBroadcast(
                this,
                notificationId ^ 0x20000000,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        String channelId = getString(R.string.incoming_call_channel_id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Incoming video calls",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts for incoming EZ Call video calls.");
            Uri ringtoneUri = Settings.System.DEFAULT_RINGTONE_URI;
            channel.setSound(ringtoneUri, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 700, 450, 700, 450});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        replacePreviousNotification(manager, callId, notificationId);
        postIncomingCallNotification(
                manager,
                notificationId,
                channelId,
                callerName,
                callerPhotoBase64,
                pendingIntent,
                answerPendingIntent,
                declinePendingIntent,
                callWaiting
        );
        FirebaseCallRepository.markCallInviteDelivered(this, callId)
                .addOnFailureListener(error -> Log.w(
                        TAG,
                        "Could not acknowledge incoming-call notification delivery.",
                        error
                ));

    }

    private void handleCallStatusUpdate(Map<String, String> data) {
        String callId = valueOrDefault(data.get("callId"), "");
        String status = valueOrDefault(data.get("status"), "");
        String callerPhoneNumber = valueOrDefault(data.get("callerPhoneNumber"), "");
        String callerName = localContactName(
                callerPhoneNumber,
                valueOrDefault(data.get("callerName"), "Unknown caller")
        );
        NotificationInviteSnapshot inviteSnapshot = loadNotificationInvite(
                valueOrDefault(data.get("callerPhotoBase64"), ""),
                callId
        );
        handleCallStatusUpdate(
                callId,
                status,
                callerName,
                inviteSnapshot.callerPhotoBase64
        );
    }

    private void handleCallStatusUpdate(
            String callId,
            String status,
            String callerName,
            String callerPhotoBase64
    ) {
        if (callId.isEmpty() || !IncomingCallStatusUpdate.shouldDismissRinging(status)) {
            return;
        }

        int notificationId = IncomingCallNotificationIds.forCallId(callId);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.cancel(notificationId);
        clearActiveNotificationState(callId, notificationId);
        CallSessionGuard.suppressFinishedCall(this, callId);
        if (TelecomDisconnectPolicy.shouldDisconnectForInviteStatus(status)) {
            EzCallTelecomManager.disconnect(
                    callId,
                    TelecomDisconnectPolicy.causeForInviteStatus(
                            status,
                            DisconnectCause.REMOTE
                    )
            );
        }
        Log.i(TAG, "Dismissed incoming-call notification for " + callId
                + " after status changed to " + status);

        if (IncomingCallStatusUpdate.shouldShowMissedCall(status)) {
            postMissedCallNotification(
                    manager,
                    notificationId,
                    callerName,
                    callerPhotoBase64
            );
        }
    }

    private void clearActiveNotificationState(String callId, int notificationId) {
        SharedPreferences preferences = getSharedPreferences(
                NOTIFICATION_STATE_PREFERENCES,
                MODE_PRIVATE
        );
        if (!callId.equals(preferences.getString(ACTIVE_NOTIFICATION_CALL_ID, ""))
                || notificationId != preferences.getInt(ACTIVE_NOTIFICATION_ID, 0)) {
            return;
        }
        preferences.edit()
                .remove(ACTIVE_NOTIFICATION_CALL_ID)
                .remove(ACTIVE_NOTIFICATION_ID)
                .apply();
    }

    private void replacePreviousNotification(
            NotificationManager manager,
            String callId,
            int notificationId
    ) {
        SharedPreferences preferences = getSharedPreferences(
                NOTIFICATION_STATE_PREFERENCES,
                MODE_PRIVATE
        );
        String activeCallId = preferences.getString(ACTIVE_NOTIFICATION_CALL_ID, "");
        int activeNotificationId = preferences.getInt(ACTIVE_NOTIFICATION_ID, 0);
        if (callId.equals(activeCallId) && notificationId == activeNotificationId) {
            return;
        }

        if (activeNotificationId != 0) {
            manager.cancel(activeNotificationId);
        }
        manager.cancel(IncomingCallNotificationIds.LEGACY_NOTIFICATION_ID);
        preferences.edit()
                .putString(ACTIVE_NOTIFICATION_CALL_ID, callId)
                .putInt(ACTIVE_NOTIFICATION_ID, notificationId)
                .apply();
    }

    private long longValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private long elapsedMillis(long endMillis, long startMillis) {
        if (startMillis <= 0L || endMillis < startMillis) {
            return -1L;
        }
        return endMillis - startMillis;
    }

    private NotificationInviteSnapshot loadNotificationInvite(
            String pushPhotoBase64,
            String callId
    ) {
        String sanitizedPushPhoto = ProfilePhotoUtils.sanitizeBase64(pushPhotoBase64);
        if (callId.isEmpty() || "memory-calls-default".equals(callId)) {
            return new NotificationInviteSnapshot(sanitizedPushPhoto, "");
        }

        try {
            DocumentSnapshot snapshot = Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection("callInvites")
                            .document(callId)
                            .get(),
                    PHOTO_LOOKUP_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS
            );
            if (sanitizedPushPhoto.isEmpty()) {
                Object storedValue = snapshot.get("callerPhotoBase64");
                if (storedValue instanceof String) {
                    sanitizedPushPhoto = ProfilePhotoUtils.sanitizeBase64(
                            (String) storedValue
                    );
                }
            }
            return new NotificationInviteSnapshot(
                    sanitizedPushPhoto,
                    valueOrDefault(snapshot.getString("status"), "")
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new NotificationInviteSnapshot(sanitizedPushPhoto, "");
        } catch (ExecutionException | TimeoutException error) {
            Log.d(TAG, "Call details were not immediately available for " + callId);
            return new NotificationInviteSnapshot(sanitizedPushPhoto, "");
        }
    }

    private void postMissedCallNotification(
            NotificationManager manager,
            int notificationId,
            String callerName,
            String callerPhotoBase64
    ) {
        String channelId = getString(R.string.missed_call_channel_id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Missed video calls",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Missed EZ Call video calls.");
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this,
                notificationId ^ 0x30000000,
                contentIntent,
                flags
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification_call)
                .setContentTitle("Missed video call")
                .setContentText(callerName + " called you")
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);

        Bitmap callerPhoto = notificationPhoto(callerPhotoBase64);
        if (callerPhoto != null) {
            builder.setLargeIcon(callerPhoto);
        }
        manager.notify(notificationId, builder.build());
    }

    private void postIncomingCallNotification(
            NotificationManager manager,
            int notificationId,
            String channelId,
            String callerName,
            String callerPhotoBase64,
            PendingIntent contentPendingIntent,
            PendingIntent answerPendingIntent,
            PendingIntent declinePendingIntent,
            boolean callWaiting
    ) {
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(this, channelId)
                : new android.app.Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_notification_call)
                .setContentTitle("Video call from " + callerName)
                .setContentText("Incoming video call")
                .setCategory(android.app.Notification.CATEGORY_CALL)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(INCOMING_CALL_TIMEOUT_MILLIS);
        if (!callWaiting) {
            builder.setFullScreenIntent(contentPendingIntent, true);
        } else {
            builder.setContentText("End the current call to answer");
        }

        Bitmap callerPhoto = notificationPhoto(callerPhotoBase64);
        if (callWaiting) {
            if (callerPhoto != null) {
                builder.setLargeIcon(callerPhoto);
            }
            builder.addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent)
                    .addAction(R.drawable.ic_videocam, "End & accept", answerPendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person.Builder callerBuilder = new Person.Builder()
                    .setName(callerName)
                    .setImportant(true);
            if (callerPhoto != null) {
                callerBuilder.setIcon(Icon.createWithBitmap(callerPhoto));
            }
            Person caller = callerBuilder.build();
            Notification.CallStyle callStyle = Notification.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent
            );
            callStyle.setAnswerButtonColorHint(ANSWER_COLOR);
            callStyle.setDeclineButtonColorHint(DECLINE_COLOR);
            builder.setStyle(callStyle).addPerson(caller);
        } else {
            if (callerPhoto != null) {
                builder.setLargeIcon(callerPhoto);
            }
            builder.addAction(R.drawable.ic_videocam, "Accept", answerPendingIntent)
                    .addAction(R.drawable.ic_call_end, "Decline", declinePendingIntent);
        }

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_INSISTENT;
        manager.notify(notificationId, notification);
    }

    private Bitmap notificationPhoto(String photoBase64) {
        Bitmap decoded = ProfilePhotoUtils.decodeBase64(photoBase64);
        if (decoded == null) {
            return null;
        }

        int side = Math.min(decoded.getWidth(), decoded.getHeight());
        int left = Math.max(0, (decoded.getWidth() - side) / 2);
        int top = Math.max(0, (decoded.getHeight() - side) / 2);
        Bitmap square = Bitmap.createBitmap(decoded, left, top, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(
                square,
                NOTIFICATION_PHOTO_SIZE_PX,
                NOTIFICATION_PHOTO_SIZE_PX,
                true
        );

        Bitmap circular = Bitmap.createBitmap(
                NOTIFICATION_PHOTO_SIZE_PX,
                NOTIFICATION_PHOTO_SIZE_PX,
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(circular);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new BitmapShader(
                scaled,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
        ));
        float radius = NOTIFICATION_PHOTO_SIZE_PX / 2f;
        canvas.drawCircle(radius, radius, radius, paint);
        return circular;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String localContactName(String phoneNumber, String fallback) {
        return DeviceContactNumbers.displayName(
                getContentResolver(),
                phoneNumber,
                fallback
        );
    }

    private static final class NotificationInviteSnapshot {
        final String callerPhotoBase64;
        final String status;

        NotificationInviteSnapshot(String callerPhotoBase64, String status) {
            this.callerPhotoBase64 = callerPhotoBase64;
            this.status = status;
        }
    }
}
