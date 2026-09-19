package com.ezcall.onetoone;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telecom.DisconnectCause;
import android.text.TextUtils;
import android.util.Log;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideoCallActivity extends ComponentActivity {
    static final String EXTRA_ANSWERED_BY_TELECOM = "answered_by_telecom";
    private static final String TAG = "VideoCallActivity";
    private static final int MEDIA_PERMISSION_REQUEST = 42;
    private static final long CALL_CONTROLS_AUTO_HIDE_MILLIS = 3000L;
    private static final long OUTGOING_CALL_TIMEOUT_MILLIS = 120_000L;
    private static final long SCREEN_WAKE_RECHECK_MILLIS = 350L;
    private static final long SCREEN_WAKE_FINAL_RECHECK_MILLIS = 1000L;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final CallSoundPlayer.CallingBeep outgoingCallingBeep =
            new CallSoundPlayer.CallingBeep();
    private final CallSoundPlayer.OutgoingRingback outgoingRingback =
            new CallSoundPlayer.OutgoingRingback();
    private final List<Animator> waitingButtonAnimators = new ArrayList<>();
    private LinearLayout root;
    private String contactName;
    private String contactUid;
    private String phoneNumber;
    private String photoBase64;
    private String callId;
    private int incomingCallNotificationId;
    private boolean incomingCall;
    private boolean outgoingInviteSent;
    private boolean webRtcStarted;
    private boolean webRtcStarting;
    private boolean cameraEnabled = true;
    private boolean cameraSuspendedForScreenOff;
    private boolean screenStateReceiverRegistered;
    private boolean pendingIncomingAcceptance;
    private boolean incomingAcceptanceInProgress;
    private boolean answerRequestedByTelecom;
    private boolean finalStatusWritten;
    private boolean callClosureInProgress;
    private boolean connectedSoundPlayed;
    private boolean endSoundPlayed;
    private ImageButton cameraButton;
    private LinearLayout callControls;
    private FrameLayout localPreviewContainer;
    private View localCameraOffOverlay;
    private AudioReactiveAvatar remoteAudioAvatar;
    private View connectedCallStage;
    private View connectedStatusPill;
    private TextView connectedStatusLabel;
    private TextView outgoingCallStateText;
    private TextView outgoingCallStatusText;
    private boolean callControlsVisible;
    private final Runnable hideCallControlsRunnable = this::hideCallControls;
    private final Runnable outgoingCallTimeoutRunnable = this::handleOutgoingCallTimeout;
    private WebRtcCallClient webRtcCallClient;
    private ListenerRegistration callStatusRegistration;
    private final EzCallTelecomManager.Listener telecomListener =
            new EzCallTelecomManager.Listener() {
                @Override
                public void onTelecomAnswerRequested(String requestedCallId) {
                    if (TextUtils.equals(callId, requestedCallId)) {
                        answerRequestedByTelecom = true;
                        uiHandler.post(VideoCallActivity.this::acceptIncomingCall);
                    }
                }

                @Override
                public void onTelecomDisconnectRequested(
                        String requestedCallId,
                        int disconnectCode
                ) {
                    if (!TextUtils.equals(callId, requestedCallId)) {
                        return;
                    }
                    uiHandler.post(() -> completeCallAndReturnHome(
                            statusForTelecomDisconnect(disconnectCode),
                            false
                    ));
                }

                @Override
                public void onTelecomSetActiveRequested(String requestedCallId) {
                    if (!TextUtils.equals(callId, requestedCallId)) {
                        return;
                    }
                    setCallAudioActive(true);
                    uiHandler.post(() -> {
                        if (incomingCall && !webRtcStarted && !finalStatusWritten) {
                            acceptIncomingCall();
                        }
                    });
                }

                @Override
                public void onTelecomSetInactiveRequested(String requestedCallId) {
                    if (TextUtils.equals(callId, requestedCallId)) {
                        setCallAudioActive(false);
                    }
                }

                @Override
                public void onTelecomMuteChanged(String requestedCallId, boolean muted) {
                    if (TextUtils.equals(callId, requestedCallId)) {
                        if (webRtcCallClient != null) {
                            webRtcCallClient.setMicrophoneEnabled(!muted);
                        }
                    }
                }
            };
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                setCameraSuspendedForScreenOff(true);
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                setCameraSuspendedForScreenOff(false);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                refreshCameraSuspensionAfterWake();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureLockScreenPresentation();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webRtcStarted && !finalStatusWritten) {
                    if (!enterPictureInPictureIfAvailable()) {
                        moveTaskToBack(true);
                    }
                    return;
                }
                completeCallAndReturnHome(
                        finalStatusWritten
                                ? null
                                : CallEndStatus.forLocalExit(incomingCall, webRtcStarted)
                );
            }
        });
        contactName = getIntent().getStringExtra(MainActivity.EXTRA_NAME);
        contactUid = getIntent().getStringExtra(MainActivity.EXTRA_UID);
        phoneNumber = getIntent().getStringExtra(MainActivity.EXTRA_PHONE_NUMBER);
        photoBase64 = getIntent().getStringExtra(MainActivity.EXTRA_PHOTO_BASE64);
        callId = getIntent().getStringExtra(MainActivity.EXTRA_CALL_ID);
        incomingCall = getIntent().getBooleanExtra(MainActivity.EXTRA_INCOMING_CALL, false);
        answerRequestedByTelecom = getIntent().getBooleanExtra(
                EXTRA_ANSWERED_BY_TELECOM,
                false
        );
        boolean autoAcceptIncomingCall = consumeAutoAccept(getIntent());
        cameraSuspendedForScreenOff = isScreenOff();
        registerScreenStateReceiver();

        if (contactName == null) {
            contactName = "your contact";
        }
        if (phoneNumber == null) {
            phoneNumber = "unknown number";
        }
        if (callId == null) {
            callId = "memory-calls-default";
        }
        incomingCallNotificationId = getIntent().getIntExtra(
                MainActivity.EXTRA_INCOMING_NOTIFICATION_ID,
                IncomingCallNotificationIds.forCallId(callId)
        );
        if (incomingCall) {
            EzCallTelecomManager.registerIncoming(
                    this,
                    callId,
                    contactName,
                    phoneNumber
            );
            EzCallTelecomManager.setListener(callId, telecomListener);
        }

        getWindow().setStatusBarColor(color("#080914"));
        getWindow().setNavigationBarColor(color("#05060C"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildShell();
        if (incomingCall) {
            listenForCallStatus();
        }

        if (incomingCall || hasMediaPermissions()) {
            beginCall();
        } else {
            showPermissionPrompt();
        }
        if (autoAcceptIncomingCall) {
            uiHandler.post(this::acceptIncomingCall);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(MainActivity.EXTRA_END_CURRENT_AND_ACCEPT, false)) {
            endCurrentCallAndAccept(intent);
            return;
        }
        if (consumeAutoAccept(intent)) {
            setIntent(intent);
            acceptIncomingCall();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCameraSuspensionAfterWake();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            refreshCameraSuspensionAfterWake();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updatePictureInPictureParams();
        } else {
            enterPictureInPictureIfAvailable();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode,
            Configuration newConfig
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        applyPictureInPictureUi(isInPictureInPictureMode);
    }

    private void configureLockScreenPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            return;
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        stopWaitingButtonAnimations();
        stopOutgoingCallTones();
        playEndSoundOnce();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        unregisterScreenStateReceiver();
        EzCallTelecomManager.removeListener(callId, telecomListener);
        CallForegroundService.stop(this);
        if (callStatusRegistration != null) {
            callStatusRegistration.remove();
            callStatusRegistration = null;
        }
        if (!finalStatusWritten) {
            EzCallTelecomManager.disconnect(callId, DisconnectCause.LOCAL);
            markFinalStatusWithAnsweredFallback(
                    CallEndStatus.forLocalExit(incomingCall, webRtcStarted)
            );
        }
        if (webRtcCallClient != null) {
            webRtcCallClient.stop();
            webRtcCallClient = null;
        }
        ActiveCallTracker.clear(callId);
        super.onDestroy();
    }

    private void endCurrentCallAndAccept(Intent waitingCallIntent) {
        if (callClosureInProgress) {
            return;
        }
        callClosureInProgress = true;
        finalStatusWritten = true;
        updatePictureInPictureParams();
        stopWaitingButtonAnimations();
        stopOutgoingCallTones();
        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
        uiHandler.removeCallbacks(hideCallControlsRunnable);
        CallSessionGuard.suppressFinishedCall(this, callId);
        ActiveCallTracker.clear(callId);
        CallForegroundService.stop(this);
        EzCallTelecomManager.disconnect(callId, DisconnectCause.LOCAL);
        FirebaseCallRepository.markCallInviteStatus(this, callId, "ended");

        Intent replacement = new Intent(waitingCallIntent);
        replacement.setClass(this, VideoCallActivity.class);
        replacement.removeExtra(MainActivity.EXTRA_END_CURRENT_AND_ACCEPT);
        replacement.putExtra(MainActivity.EXTRA_INCOMING_CALL, true);
        replacement.putExtra(MainActivity.EXTRA_AUTO_ACCEPT_INCOMING_CALL, true);
        replacement.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(replacement);
    }

    private void listenForCallStatus() {
        if (callStatusRegistration != null) {
            return;
        }
        callStatusRegistration = FirebaseCallRepository.listenForCallStatus(
                this,
                callId,
                status -> {
                    if (finalStatusWritten) {
                        return;
                    }
                    if ("answered".equals(status) && !incomingCall && !webRtcStarted) {
                        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
                        showConnectedCall();
                    } else if ("delivered".equals(status)
                            && !incomingCall
                            && !webRtcStarted) {
                        showOutgoingRingingState();
                    } else if ("declined".equals(status)) {
                        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
                        finalStatusWritten = true;
                        EzCallTelecomManager.disconnect(callId, DisconnectCause.REJECTED);
                        showCallError(contactName + " declined the call.");
                    } else if ("missed".equals(status)) {
                        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
                        finalStatusWritten = true;
                        EzCallTelecomManager.disconnect(callId, DisconnectCause.MISSED);
                        if (incomingCall) {
                            completeCallAndReturnHome(null);
                        } else {
                            showCallError(contactName + " did not answer.");
                        }
                    } else if ("notification_failed".equals(status)
                            || "callee_not_registered".equals(status)
                            || "callee_missing_uid".equals(status)
                            || "callee_missing_fcm_token".equals(status)) {
                        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
                        finalStatusWritten = true;
                        EzCallTelecomManager.disconnect(callId, DisconnectCause.ERROR);
                        showCallError("Could not notify " + contactName + " about this call.");
                    } else if ("ended".equals(status)) {
                        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
                        finalStatusWritten = true;
                        EzCallTelecomManager.disconnect(callId, DisconnectCause.REMOTE);
                        if (incomingCall) {
                            cancelIncomingCallNotification();
                        }
                        if (!isFinishing() && !isDestroyed()) {
                            completeCallAndReturnHome(null);
                        }
                    }
                }
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_REQUEST && hasMediaPermissions()) {
            if (incomingCall && pendingIncomingAcceptance) {
                acceptIncomingCall();
            } else {
                beginCall();
            }
        } else {
            showPermissionPrompt();
        }
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color("#080914"));
        setContentView(root);
    }

    private void showPermissionPrompt() {
        removeBodyViews();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setBackgroundColor(color("#EDF4F8"));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(22), dp(24), dp(22), dp(22));
        card.setBackground(rounded("#FFFFFF", dp(18), "#D7E2EA", 1));
        card.setElevation(dp(3));

        TextView title = text("Allow camera and microphone", 25, "#172033", Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView body = text("This lets " + contactName + " see and hear you during the video call.", 18, "#536174", Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.setMargins(0, dp(10), 0, dp(24));
        card.addView(body, bodyParams);

        Button allowButton = new Button(this);
        allowButton.setText("Allow video call");
        allowButton.setTextSize(20);
        allowButton.setTextColor(Color.BLACK);
        allowButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        allowButton.setBackground(rounded("#F95830", dp(999), "#F95830", 0));
        allowButton.setElevation(dp(2));
        allowButton.setOnClickListener(view -> requestPermissions(
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                MEDIA_PERMISSION_REQUEST
        ));
        card.addView(allowButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        panel.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void beginCall() {
        try {
            if (incomingCall) {
                showIncomingCallScreen();
            } else {
                beginOutgoingCall();
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not begin call", error);
            showCallError("Could not start this call. Check this contact's app setup and try again.");
        }
    }

    private void beginOutgoingCall() {
        if (FirebaseCallRepository.normalizePhoneNumber(phoneNumber).isEmpty()) {
            showCallError("This contact does not have a valid phone number for calls.");
            return;
        }
        if (!outgoingInviteSent) {
            outgoingInviteSent = true;
            OutgoingCallInviteSender.send(this, contactName, phoneNumber, contactUid, callId)
                    .addOnSuccessListener(unused -> {
                        if (!finalStatusWritten && !isFinishing() && !isDestroyed()) {
                            listenForCallStatus();
                        } else {
                            FirebaseCallRepository.markCallInviteStatus(this, callId, "missed");
                        }
                    })
                    .addOnFailureListener(error -> {
                        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
                        finalStatusWritten = true;
                        EzCallTelecomManager.disconnect(callId, DisconnectCause.ERROR);
                        if (!isFinishing() && !isDestroyed()) {
                            showCallError("Could not send this call. Check your connection and try again.");
                        }
                    });
        }

        showOutgoingCallingScreen();
        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
        uiHandler.postDelayed(outgoingCallTimeoutRunnable, OUTGOING_CALL_TIMEOUT_MILLIS);
    }

    private void showIncomingCallScreen() {
        showCallWaitingScreen(true);
    }

    private void acceptIncomingCall() {
        if (!incomingCall || webRtcStarted || finalStatusWritten || incomingAcceptanceInProgress) {
            return;
        }
        cancelIncomingCallNotification();
        if (!hasMediaPermissions()) {
            pendingIncomingAcceptance = true;
            showPermissionPrompt();
            return;
        }
        pendingIncomingAcceptance = false;
        incomingAcceptanceInProgress = true;
        if (!answerRequestedByTelecom) {
            EzCallTelecomManager.answer(callId);
        }
        answerRequestedByTelecom = false;
        FirebaseCallRepository.markCallInviteStatus(this, callId, "answered")
                .addOnSuccessListener(unused -> {
                    if (!finalStatusWritten && !isFinishing() && !isDestroyed()) {
                        showConnectedCall();
                    }
                })
                .addOnFailureListener(error -> {
                    incomingAcceptanceInProgress = false;
                    if (!isFinishing() && !isDestroyed()) {
                        showCallError("Could not accept this call. Check your connection and try again.");
                    }
                });
    }

    private void handleOutgoingCallTimeout() {
        if (incomingCall || webRtcStarted || finalStatusWritten || isFinishing() || isDestroyed()) {
            return;
        }
        FirebaseCallRepository.markCallInviteStatus(this, callId, "missed")
                .addOnSuccessListener(unused -> {
                    if (webRtcStarted || finalStatusWritten || isFinishing() || isDestroyed()) {
                        return;
                    }
                    finalStatusWritten = true;
                    EzCallTelecomManager.disconnect(callId, DisconnectCause.MISSED);
                    showCallError(contactName + " did not answer.");
                })
                .addOnFailureListener(error -> Log.i(
                        TAG,
                        "Outgoing timeout lost a race with another authoritative state.",
                        error
                ));
    }

    private boolean consumeAutoAccept(Intent intent) {
        if (intent == null
                || !intent.getBooleanExtra(MainActivity.EXTRA_AUTO_ACCEPT_INCOMING_CALL, false)) {
            return false;
        }
        intent.removeExtra(MainActivity.EXTRA_AUTO_ACCEPT_INCOMING_CALL);
        String requestedCallId = intent.getStringExtra(MainActivity.EXTRA_CALL_ID);
        return incomingCall
                && !webRtcStarted
                && !finalStatusWritten
                && TextUtils.equals(callId, requestedCallId);
    }

    private void declineIncomingCall() {
        completeCallAndReturnHome("declined");
    }

    private void cancelIncomingCallNotification() {
        getSystemService(NotificationManager.class)
                .cancel(incomingCallNotificationId);
    }

    private void showOutgoingCallingScreen() {
        stopOutgoingCallTones();
        outgoingCallingBeep.start();
        showCallWaitingScreen(false);
    }

    private void showCallWaitingScreen(boolean isIncoming) {
        stopWaitingButtonAnimations();
        outgoingCallStateText = null;
        outgoingCallStatusText = null;
        root.removeAllViews();

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(callScreenBackground(isIncoming));

        String accent = isIncoming ? "#FC5C7D" : "#7C5CFC";
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(
                dp(360),
                dp(360),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        glowParams.setMargins(0, -dp(70), 0, 0);
        stage.addView(ambientGlow(accent, dp(180)), glowParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(32), dp(24), dp(28));
        panel.setBackgroundColor(Color.TRANSPARENT);

        StatePill statePill = callStatePill(
                isIncoming ? "Incoming" : "Calling",
                isIncoming ? "#1BCC62" : "#7C5CFC"
        );
        panel.addView(statePill.view, new LinearLayout.LayoutParams(dp(150), dp(52)));
        if (!isIncoming) {
            outgoingCallStateText = statePill.label;
        }

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER);
        identity.addView(
                pulseAvatar(
                        isIncoming ? "#FC5C7D" : "#7C5CFC",
                        dp(260)
                ),
                new LinearLayout.LayoutParams(dp(260), dp(260))
        );

        TextView name = text(contactName, 34, "#FFFFFF", Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(0, dp(22), 0, 0);
        identity.addView(name, nameParams);

        TextView status = text(
                isIncoming ? "Incoming video call..." : "Calling...",
                21,
                "#8E8B99",
                Typeface.BOLD
        );
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(8), 0, 0);
        identity.addView(status, statusParams);
        if (!isIncoming) {
            outgoingCallStatusText = status;
        }

        panel.addView(identity, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);

        if (isIncoming) {
            actions.addView(waitingAction(
                    R.drawable.ic_call_end,
                    "Decline",
                    "#FC5C7D",
                    "#CC354B",
                    view -> declineIncomingCall()
            ), new LinearLayout.LayoutParams(0, dp(112), 1));

            actions.addView(waitingAction(
                    R.drawable.ic_clock,
                    "Remind Me",
                    "#12FFFFFF",
                    "#1AFFFFFF",
                    view -> moveTaskToBack(true)
            ), new LinearLayout.LayoutParams(0, dp(112), 1));

            actions.addView(waitingAction(
                    R.drawable.ic_call_answer,
                    "Accept",
                    "#5CFC9A",
                    "#1BCC62",
                    view -> acceptIncomingCall()
            ), new LinearLayout.LayoutParams(0, dp(112), 1));
        } else {
            actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(112), 1));
            actions.addView(waitingAction(
                    R.drawable.ic_call_end,
                    "Cancel",
                    "#FC5C7D",
                    "#CC354B",
                    view -> completeCallAndReturnHome("missed")
            ), new LinearLayout.LayoutParams(0, dp(112), 1));
            actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(112), 1));
        }

        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(112)
        ));

        stage.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void showOutgoingRingingState() {
        if (incomingCall || webRtcStarted || finalStatusWritten) {
            return;
        }
        if (outgoingCallStateText != null) {
            outgoingCallStateText.setText("Ringing");
        }
        if (outgoingCallStatusText != null) {
            outgoingCallStatusText.setText("Ringing...");
        }
        outgoingCallingBeep.stop();
        outgoingRingback.start();
    }

    private void stopOutgoingCallTones() {
        outgoingCallingBeep.stop();
        outgoingRingback.stop();
    }

    private StatePill callStatePill(String label, String dotColor) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(18), dp(10), dp(18), dp(10));
        pill.setBackground(darkGlass(dp(999)));

        View dot = new View(this);
        dot.setBackground(oval(dotColor, dotColor, 0));
        pill.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        TextView text = text(label, 17, "#D6D3DE", Typeface.BOLD);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(dp(180));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.setMargins(dp(10), 0, 0, 0);
        pill.addView(text, textParams);
        return new StatePill(pill, text);
    }

    private View pulseAvatar(String accent, int size) {
        FrameLayout pulse = new FrameLayout(this);
        pulse.setMinimumWidth(size);
        pulse.setMinimumHeight(size);
        pulse.setClipChildren(false);
        pulse.setClipToPadding(false);

        View outer = new View(this);
        outer.setBackground(oval(
                Color.TRANSPARENT,
                withAlpha(accent, 0x14),
                1
        ));
        pulse.addView(outer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        int middleSize = Math.round(size * 0.78f);
        View middle = new View(this);
        middle.setBackground(oval(
                Color.TRANSPARENT,
                withAlpha(accent, 0x32),
                1
        ));
        pulse.addView(middle, new FrameLayout.LayoutParams(
                middleSize,
                middleSize,
                Gravity.CENTER
        ));

        int imageSize = Math.round(size * 0.56f);
        ImageView image = new ImageView(this);
        setProfileImage(image);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(oval(accent, accent, 2));
        image.setClipToOutline(true);
        image.setContentDescription(contactName);
        image.setElevation(dp(4));
        pulse.addView(image, new FrameLayout.LayoutParams(
                imageSize,
                imageSize,
                Gravity.CENTER
        ));
        return pulse;
    }

    private AudioReactiveAvatar createAudioReactiveAvatar(String accent, int size) {
        FrameLayout pulse = new FrameLayout(this);
        pulse.setMinimumWidth(size);
        pulse.setMinimumHeight(size);
        pulse.setClipChildren(false);
        pulse.setClipToPadding(false);

        View glow = ambientGlow(accent, Math.round(size * 0.48f));
        glow.setAlpha(0.16f);
        glow.setScaleX(0.56f);
        glow.setScaleY(0.56f);
        pulse.addView(glow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        int imageSize = Math.round(size * 0.56f);
        int ringSize = imageSize;
        View ring = new View(this);
        ring.setBackground(oval(
                Color.TRANSPARENT,
                withAlpha(accent, 0x58),
                2
        ));
        ring.setElevation(dp(5));
        pulse.addView(ring, new FrameLayout.LayoutParams(
                ringSize,
                ringSize,
                Gravity.CENTER
        ));

        ImageView image = new ImageView(this);
        setProfileImage(image);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(oval(accent, accent, 2));
        image.setClipToOutline(true);
        image.setContentDescription(contactName);
        image.setElevation(dp(4));
        pulse.addView(image, new FrameLayout.LayoutParams(
                imageSize,
                imageSize,
                Gravity.CENTER
        ));
        return new AudioReactiveAvatar(pulse, ring, glow);
    }

    private View waitingAction(
            int iconResource,
            String label,
            String fill,
            String stroke,
            View.OnClickListener listener
    ) {
        LinearLayout action = new LinearLayout(this);
        action.setOrientation(LinearLayout.VERTICAL);
        action.setGravity(Gravity.CENTER);
        action.setClipChildren(false);
        action.setClipToPadding(false);
        action.setClickable(true);
        action.setFocusable(true);
        action.setContentDescription(label);
        action.setOnClickListener(listener);

        ImageButton button = circularControl(iconResource, fill, stroke);
        if ("Remind Me".equals(label)) {
            button.setBackground(glassCircle());
        } else {
            button.setBackground(gradientCircle(fill, stroke));
        }
        button.setContentDescription(label);
        button.setOnClickListener(listener);

        FrameLayout buttonStage = new FrameLayout(this);
        buttonStage.setClipChildren(false);
        buttonStage.setClipToPadding(false);

        View halo = null;
        boolean animatedAction = "Accept".equals(label) || "Decline".equals(label);
        if (animatedAction) {
            halo = ambientGlow(fill, dp(40));
            halo.setAlpha(0.20f);
            halo.setScaleX(0.82f);
            halo.setScaleY(0.82f);
            buttonStage.addView(halo, new FrameLayout.LayoutParams(
                    dp(84),
                    dp(84),
                    Gravity.CENTER
            ));
        }
        buttonStage.addView(button, new FrameLayout.LayoutParams(
                dp(72),
                dp(72),
                Gravity.CENTER
        ));
        action.addView(buttonStage, new LinearLayout.LayoutParams(dp(84), dp(80)));
        if (animatedAction) {
            startWaitingButtonAnimation(
                    button,
                    halo,
                    "Accept".equals(label)
            );
        }

        TextView labelView = text(label, 14, "#92909A", Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, dp(7), 0, 0);
        action.addView(labelView, labelParams);
        return action;
    }

    private void startWaitingButtonAnimation(
            View button,
            View halo,
            boolean primaryAction
    ) {
        long duration = primaryAction ? 820L : 980L;
        long startDelay = primaryAction ? 0L : 180L;
        float buttonScale = primaryAction ? 1.08f : 1.055f;

        ObjectAnimator buttonScaleX = repeatingAnimator(
                button,
                View.SCALE_X,
                1f,
                buttonScale,
                duration,
                startDelay
        );
        ObjectAnimator buttonScaleY = repeatingAnimator(
                button,
                View.SCALE_Y,
                1f,
                buttonScale,
                duration,
                startDelay
        );
        ObjectAnimator buttonAlpha = repeatingAnimator(
                button,
                View.ALPHA,
                0.88f,
                1f,
                duration,
                startDelay
        );
        ObjectAnimator haloScaleX = repeatingAnimator(
                halo,
                View.SCALE_X,
                0.82f,
                primaryAction ? 1.30f : 1.18f,
                duration,
                startDelay
        );
        ObjectAnimator haloScaleY = repeatingAnimator(
                halo,
                View.SCALE_Y,
                0.82f,
                primaryAction ? 1.30f : 1.18f,
                duration,
                startDelay
        );
        ObjectAnimator haloAlpha = repeatingAnimator(
                halo,
                View.ALPHA,
                0.20f,
                primaryAction ? 0.72f : 0.52f,
                duration,
                startDelay
        );

        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                buttonScaleX,
                buttonScaleY,
                buttonAlpha,
                haloScaleX,
                haloScaleY,
                haloAlpha
        );
        waitingButtonAnimators.add(animation);
        animation.start();
    }

    private ObjectAnimator repeatingAnimator(
            View target,
            android.util.Property<View, Float> property,
            float start,
            float end,
            long duration,
            long startDelay
    ) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, property, start, end);
        animator.setDuration(duration);
        animator.setStartDelay(startDelay);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        return animator;
    }

    private void stopWaitingButtonAnimations() {
        for (Animator animator : waitingButtonAnimators) {
            animator.cancel();
        }
        waitingButtonAnimators.clear();
    }

    private void showConnectedCall() {
        stopWaitingButtonAnimations();
        stopOutgoingCallTones();
        if (webRtcStarted) {
            return;
        }
        ensureConnectedCallTelecomRegistration();
        EzCallTelecomManager.setActive(callId);
        if (!connectedSoundPlayed) {
            connectedSoundPlayed = true;
            CallSoundPlayer.playConnected();
        }
        uiHandler.removeCallbacks(hideCallControlsRunnable);
        callControls = null;
        localPreviewContainer = null;
        localCameraOffOverlay = null;
        remoteAudioAvatar = null;
        connectedCallStage = null;
        connectedStatusPill = null;
        connectedStatusLabel = null;
        callControlsVisible = false;
        root.removeAllViews();

        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(callScreenBackground(false));

        SurfaceViewRenderer remoteRenderer = new SurfaceViewRenderer(this);
        remoteRenderer.setZOrderMediaOverlay(false);
        stage.addView(remoteRenderer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout remoteCameraOffOverlay = new LinearLayout(this);
        remoteCameraOffOverlay.setOrientation(LinearLayout.VERTICAL);
        remoteCameraOffOverlay.setGravity(Gravity.CENTER);
        remoteCameraOffOverlay.setPadding(dp(28), dp(28), dp(28), dp(28));
        remoteCameraOffOverlay.setBackground(callScreenBackground(false));
        remoteCameraOffOverlay.setVisibility(View.GONE);

        remoteAudioAvatar = createAudioReactiveAvatar("#7C5CFC", dp(220));
        remoteCameraOffOverlay.addView(remoteAudioAvatar.view);

        TextView cameraOffMessage = text(contactName, 30, "#FFFFFF", Typeface.BOLD);
        cameraOffMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cameraOffMessageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cameraOffMessageParams.setMargins(0, dp(20), 0, 0);
        remoteCameraOffOverlay.addView(cameraOffMessage, cameraOffMessageParams);

        TextView cameraOffStatus = text("Camera is off", 18, "#8D8A98", Typeface.BOLD);
        cameraOffStatus.setGravity(Gravity.CENTER);
        remoteCameraOffOverlay.addView(cameraOffStatus);

        stage.addView(remoteCameraOffOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        StatePill status = callStatePill(contactName, "#7C5CFC");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                dp(240),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        statusParams.setMargins(0, dp(42), 0, 0);
        stage.addView(status.view, statusParams);
        connectedStatusPill = status.view;
        connectedStatusLabel = status.label;

        FrameLayout localPreview = new FrameLayout(this);
        localPreview.setBackground(previewGlass(dp(10)));
        localPreview.setElevation(dp(3));
        localPreview.setClipChildren(true);
        localPreview.setClipToPadding(true);
        localPreview.setClipToOutline(true);
        localPreview.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        localPreview.setContentDescription("Your video preview. Drag to move.");

        SurfaceViewRenderer localRenderer = new SurfaceViewRenderer(this);
        localRenderer.setZOrderMediaOverlay(true);
        localRenderer.setMirror(true);
        localRenderer.setBackgroundColor(Color.TRANSPARENT);
        localRenderer.setClipToOutline(true);
        localRenderer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
            }
        });

        FrameLayout previewViewport = new FrameLayout(this);
        previewViewport.setBackground(rounded("#000000", dp(8), "#000000", 0));
        previewViewport.setClipChildren(true);
        previewViewport.setClipToPadding(true);
        previewViewport.setClipToOutline(true);
        previewViewport.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        previewViewport.addView(localRenderer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ImageView localCameraOffImage = new ImageView(this);
        localCameraOffImage.setBackgroundColor(color("#0A0B17"));
        Bitmap localProfilePhoto = ProfilePhotoUtils.decodeBase64(
                FirebaseCallRepository.currentPhotoBase64OrEmpty(this)
        );
        if (localProfilePhoto != null) {
            localCameraOffImage.setImageBitmap(localProfilePhoto);
            localCameraOffImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            localCameraOffImage.setImageResource(R.drawable.ic_default_user);
            localCameraOffImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            localCameraOffImage.setPadding(dp(16), dp(16), dp(16), dp(16));
        }
        localCameraOffImage.setContentDescription("Your profile photo. Camera is off.");
        localCameraOffImage.setVisibility(View.GONE);
        previewViewport.addView(localCameraOffImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        localCameraOffOverlay = localCameraOffImage;

        FrameLayout.LayoutParams viewportParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        // SurfaceViewRenderer needs a stable inset inside a clipped parent on
        // some devices; making it nearly flush can leave the surface black.
        viewportParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        localPreview.addView(previewViewport, viewportParams);

        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(
                dp(112),
                dp(156),
                Gravity.TOP | Gravity.RIGHT
        );
        previewParams.setMargins(0, dp(88), dp(18), 0);
        stage.addView(localPreview, previewParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(12), dp(10), dp(12), dp(10));
        controls.setBackgroundColor(Color.TRANSPARENT);

        cameraButton = circularControl(R.drawable.ic_videocam, "#12FFFFFF", "#1AFFFFFF");
        cameraButton.setContentDescription("Turn camera off");
        cameraButton.setOnClickListener(view -> toggleCamera());
        controls.addView(labeledControl(cameraButton, "Camera"), weightedControlParams());

        ImageButton endButton = circularControl(R.drawable.ic_call_end, "#FC5C7D", "#CC354B");
        endButton.setBackground(gradientCircle("#FC5C7D", "#CC354B"));
        endButton.setContentDescription("End call");
        endButton.setOnClickListener(view -> completeCallAndReturnHome("ended"));
        controls.addView(labeledControl(endButton, "End"), weightedControlParams());

        ImageButton switchButton = circularControl(R.drawable.ic_camera_switch, "#12FFFFFF", "#1AFFFFFF");
        switchButton.setContentDescription("Switch camera");
        switchButton.setOnClickListener(view -> switchCamera());
        controls.addView(labeledControl(switchButton, "Flip"), weightedControlParams());

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(104),
                Gravity.BOTTOM
        );
        controlsParams.setMargins(dp(16), 0, dp(16), dp(18));
        stage.addView(controls, controlsParams);

        callControls = controls;
        localPreviewContainer = localPreview;
        connectedCallStage = stage;
        controls.setClickable(true);
        controls.setAlpha(0f);
        controls.setTranslationY(dp(122));
        controls.setVisibility(View.INVISIBLE);

        View.OnClickListener toggleControls = view -> toggleCallControls();
        stage.setOnClickListener(toggleControls);
        remoteRenderer.setOnClickListener(toggleControls);
        remoteCameraOffOverlay.setOnClickListener(toggleControls);
        localPreview.setOnClickListener(toggleControls);
        makePreviewDraggable(localPreview, stage);

        stage.setOnApplyWindowInsetsListener((view, insets) -> {
            int safeTop = insets.getSystemWindowInsetTop();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                safeTop = Math.max(safeTop, insets.getDisplayCutout().getSafeInsetTop());
            }
            statusParams.topMargin = safeTop + dp(12);
            previewParams.topMargin = safeTop + dp(58);
            status.view.setLayoutParams(statusParams);
            localPreview.setLayoutParams(previewParams);
            return insets;
        });
        stage.requestApplyInsets();
        stage.post(this::updatePictureInPictureParams);

        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        startWebRtc(
                remoteRenderer,
                localRenderer,
                remoteCameraOffOverlay,
                remoteAudioAvatar
        );
    }

    private boolean enterPictureInPictureIfAvailable() {
        if (!webRtcStarted
                || finalStatusWritten
                || isFinishing()
                || isDestroyed()
                || isInPictureInPictureMode()
                || !getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_PICTURE_IN_PICTURE
                )) {
            return false;
        }
        try {
            applyPictureInPictureUi(true);
            boolean entered = enterPictureInPictureMode(buildPictureInPictureParams());
            if (!entered) {
                applyPictureInPictureUi(false);
            }
            return entered;
        } catch (IllegalArgumentException | IllegalStateException error) {
            Log.w(TAG, "Could not enter Picture-in-Picture", error);
            applyPictureInPictureUi(false);
            return false;
        }
    }

    private void updatePictureInPictureParams() {
        if (!webRtcStarted || isFinishing() || isDestroyed()) {
            return;
        }
        try {
            setPictureInPictureParams(buildPictureInPictureParams());
        } catch (IllegalArgumentException | IllegalStateException error) {
            Log.w(TAG, "Could not update Picture-in-Picture parameters", error);
        }
    }

    private PictureInPictureParams buildPictureInPictureParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(9, 16));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(
                    webRtcStarted
                            && !finalStatusWritten
                            && !callClosureInProgress
            );
            builder.setSeamlessResizeEnabled(true);
        }
        if (connectedCallStage != null && connectedCallStage.isLaidOut()) {
            Rect sourceRect = new Rect();
            if (connectedCallStage.getGlobalVisibleRect(sourceRect) && !sourceRect.isEmpty()) {
                builder.setSourceRectHint(sourceRect);
            }
        }
        return builder.build();
    }

    private void applyPictureInPictureUi(boolean pictureInPicture) {
        uiHandler.removeCallbacks(hideCallControlsRunnable);
        if (connectedStatusPill != null) {
            connectedStatusPill.setVisibility(pictureInPicture ? View.GONE : View.VISIBLE);
        }
        if (localPreviewContainer != null) {
            localPreviewContainer.setVisibility(pictureInPicture ? View.GONE : View.VISIBLE);
        }
        if (callControls != null) {
            callControls.animate().cancel();
            callControlsVisible = false;
            callControls.setAlpha(0f);
            callControls.setTranslationY(dp(122));
            callControls.setVisibility(View.INVISIBLE);
        }
    }

    private void makePreviewDraggable(View preview, View stage) {
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        preview.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private float startTranslationX;
            private float startTranslationY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startTranslationX = view.getTranslationX();
                        startTranslationY = view.getTranslationY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;
                        if (!dragging
                                && Math.hypot(deltaX, deltaY) >= touchSlop) {
                            dragging = true;
                        }
                        if (!dragging) {
                            return true;
                        }

                        int edgeSpacing = dp(10);
                        int leftInset = edgeSpacing;
                        int topInset = edgeSpacing;
                        int rightInset = edgeSpacing;
                        int bottomInset = edgeSpacing;
                        if (stage.getRootWindowInsets() != null) {
                            leftInset += stage.getRootWindowInsets().getSystemWindowInsetLeft();
                            topInset += stage.getRootWindowInsets().getSystemWindowInsetTop();
                            rightInset += stage.getRootWindowInsets().getSystemWindowInsetRight();
                            bottomInset += stage.getRootWindowInsets().getSystemWindowInsetBottom();
                        }

                        view.setTranslationX(FloatingPreviewBounds.clampTranslation(
                                startTranslationX + deltaX,
                                view.getLeft(),
                                view.getRight(),
                                stage.getWidth(),
                                leftInset,
                                rightInset
                        ));
                        view.setTranslationY(FloatingPreviewBounds.clampTranslation(
                                startTranslationY + deltaY,
                                view.getTop(),
                                view.getBottom(),
                                stage.getHeight(),
                                topInset,
                                bottomInset
                        ));
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            view.performClick();
                        }
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void startWebRtc(
            SurfaceViewRenderer remoteRenderer,
            SurfaceViewRenderer localRenderer,
            View remoteCameraOffOverlay,
            AudioReactiveAvatar audioAvatar
    ) {
        if (webRtcStarted || webRtcStarting) {
            return;
        }
        webRtcStarting = true;
        final boolean[] iceServerRequestResolved = {false};
        Runnable startWithStun = () -> {
            if (iceServerRequestResolved[0]) {
                return;
            }
            iceServerRequestResolved[0] = true;
            webRtcStarting = false;
            startWebRtc(
                    remoteRenderer,
                    localRenderer,
                    remoteCameraOffOverlay,
                    audioAvatar,
                    Collections.emptyList()
            );
        };
        // Existing installations may not have the new callable deployed yet.
        // Do not let its lookup delay a normally reachable direct call.
        uiHandler.postDelayed(startWithStun, 2_000L);
        FirebaseCallRepository.fetchTurnIceServers(this, iceServers -> {
            if (iceServerRequestResolved[0]) {
                return;
            }
            iceServerRequestResolved[0] = true;
            uiHandler.removeCallbacks(startWithStun);
            webRtcStarting = false;
            startWebRtc(
                    remoteRenderer,
                    localRenderer,
                    remoteCameraOffOverlay,
                    audioAvatar,
                    iceServers
            );
        });
    }

    private void startWebRtc(
            SurfaceViewRenderer remoteRenderer,
            SurfaceViewRenderer localRenderer,
            View remoteCameraOffOverlay,
            AudioReactiveAvatar audioAvatar,
            List<FirebaseCallRepository.IceServerConfiguration> iceServers
    ) {
        if (webRtcStarted || finalStatusWritten || isFinishing() || isDestroyed()) {
            return;
        }
        try {
            webRtcStarted = true;
            webRtcCallClient = new WebRtcCallClient(
                    this,
                    callId,
                    !incomingCall,
                    localRenderer,
                    remoteRenderer,
                    enabled -> remoteCameraOffOverlay.setVisibility(
                            enabled ? View.GONE : View.VISIBLE
                    ),
                    audioAvatar::setAudioLevel,
                    new WebRtcCallClient.ConnectionStateListener() {
                        @Override
                        public void onConnected() {
                            showWebRtcConnectedState();
                        }

                        @Override
                        public void onReconnecting() {
                            showWebRtcReconnectingState();
                        }

                        @Override
                        public void onReconnectFailed() {
                            handleWebRtcReconnectFailure();
                        }

                        @Override
                        public void onInitialConnectionFailed() {
                            handleWebRtcConnectionFailure(
                                    "Audio and video could not connect. Try another Wi-Fi or mobile network and call again."
                            );
                        }
                    },
                    iceServers
            );
            webRtcCallClient.start();
            EzCallTelecomManager.setActive(callId);
            CallForegroundService.start(
                    this,
                    contactName,
                    phoneNumber,
                    callId,
                    incomingCall,
                    true
            );
            ActiveCallTracker.markActive(callId);
            applyCameraEnabledState();
        } catch (RuntimeException error) {
            Log.e(TAG, "WebRTC failed to start", error);
            showCallError("Video call setup failed. The other device may be using incompatible signaling.");
        }
    }

    private void ensureConnectedCallTelecomRegistration() {
        if (!EzCallTelecomManager.isManaging(callId)) {
            if (incomingCall) {
                EzCallTelecomManager.registerIncoming(
                        this,
                        callId,
                        contactName,
                        phoneNumber
                );
            } else {
                EzCallTelecomManager.registerOutgoing(
                        this,
                        callId,
                        contactName,
                        phoneNumber
                );
            }
        }
        EzCallTelecomManager.setListener(callId, telecomListener);
    }

    private void toggleCamera() {
        cameraEnabled = !cameraEnabled;
        applyCameraEnabledState();
        scheduleCallControlsHide();
    }

    private void showWebRtcConnectedState() {
        if (finalStatusWritten || isFinishing() || isDestroyed()) {
            return;
        }
        if (connectedStatusLabel != null) {
            connectedStatusLabel.setText(contactName);
            connectedStatusLabel.setContentDescription("Connected with " + contactName);
        }
    }

    private void showWebRtcReconnectingState() {
        if (finalStatusWritten || isFinishing() || isDestroyed()) {
            return;
        }
        if (connectedStatusLabel != null) {
            connectedStatusLabel.setText("Reconnecting...");
            connectedStatusLabel.setContentDescription("Reconnecting call with " + contactName);
        }
    }

    private void handleWebRtcReconnectFailure() {
        handleWebRtcConnectionFailure("The connection could not be restored. Check your network and call again.");
    }

    private void handleWebRtcConnectionFailure(String message) {
        if (finalStatusWritten || callClosureInProgress || isFinishing() || isDestroyed()) {
            return;
        }
        finalStatusWritten = true;
        CallSessionGuard.suppressFinishedCall(this, callId);
        ActiveCallTracker.clear(callId);
        CallForegroundService.stop(this);
        EzCallTelecomManager.disconnect(callId, DisconnectCause.ERROR);
        FirebaseCallRepository.markCallInviteStatus(this, callId, "ended");
        showCallError(message);
    }

    private void applyCameraEnabledState() {
        boolean cameraTransmitting = cameraEnabled && !cameraSuspendedForScreenOff;
        if (webRtcCallClient != null) {
            webRtcCallClient.setCameraEnabled(cameraTransmitting);
        }
        if (cameraButton != null) {
            cameraButton.setEnabled(!cameraSuspendedForScreenOff);
            cameraButton.setImageResource(
                    cameraTransmitting ? R.drawable.ic_videocam : R.drawable.ic_videocam_off
            );
            cameraButton.setContentDescription(
                    cameraSuspendedForScreenOff
                            ? "Camera is off while the phone is locked"
                            : cameraEnabled ? "Turn camera off" : "Turn camera on"
            );
            cameraButton.setBackground(
                    cameraTransmitting
                            ? glassCircle()
                            : gradientCircle("#7C5CFC", "#4F35CC")
            );
        }
        if (localCameraOffOverlay != null) {
            localCameraOffOverlay.setVisibility(cameraTransmitting ? View.GONE : View.VISIBLE);
        }
    }

    private void setCameraSuspendedForScreenOff(boolean suspended) {
        if (cameraSuspendedForScreenOff == suspended) {
            return;
        }
        cameraSuspendedForScreenOff = suspended;
        applyCameraEnabledState();
    }

    private void refreshCameraSuspensionAfterWake() {
        setCameraSuspendedForScreenOff(isScreenOff());
        uiHandler.postDelayed(
                () -> setCameraSuspendedForScreenOff(isScreenOff()),
                SCREEN_WAKE_RECHECK_MILLIS
        );
        uiHandler.postDelayed(
                () -> setCameraSuspendedForScreenOff(isScreenOff()),
                SCREEN_WAKE_FINAL_RECHECK_MILLIS
        );
    }

    private boolean isScreenOff() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return powerManager != null && !powerManager.isInteractive();
    }

    private void registerScreenStateReceiver() {
        if (screenStateReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenStateReceiver, filter);
        }
        screenStateReceiverRegistered = true;
    }

    private void unregisterScreenStateReceiver() {
        if (!screenStateReceiverRegistered) {
            return;
        }
        unregisterReceiver(screenStateReceiver);
        screenStateReceiverRegistered = false;
    }

    private void switchCamera() {
        if (webRtcCallClient != null) {
            webRtcCallClient.switchCamera();
        }
        scheduleCallControlsHide();
    }

    private void toggleCallControls() {
        if (callControlsVisible) {
            hideCallControls();
        } else {
            showCallControls();
        }
    }

    private void showCallControls() {
        if (callControls == null) {
            return;
        }
        uiHandler.removeCallbacks(hideCallControlsRunnable);
        callControlsVisible = true;
        callControls.animate().cancel();
        callControls.setVisibility(View.VISIBLE);
        callControls.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220L)
                .start();

        scheduleCallControlsHide();
    }

    private void hideCallControls() {
        if (callControls == null) {
            return;
        }
        uiHandler.removeCallbacks(hideCallControlsRunnable);
        callControlsVisible = false;
        LinearLayout controlsToHide = callControls;
        controlsToHide.animate().cancel();
        controlsToHide.animate()
                .translationY(dp(122))
                .alpha(0f)
                .setDuration(200L)
                .withEndAction(() -> {
                    if (!callControlsVisible && callControls == controlsToHide) {
                        controlsToHide.setVisibility(View.INVISIBLE);
                    }
                })
                .start();

    }

    private void scheduleCallControlsHide() {
        uiHandler.removeCallbacks(hideCallControlsRunnable);
        if (callControlsVisible) {
            uiHandler.postDelayed(hideCallControlsRunnable, CALL_CONTROLS_AUTO_HIDE_MILLIS);
        }
    }

    private void removeBodyViews() {
        stopWaitingButtonAnimations();
        root.removeAllViews();
    }

    private boolean hasMediaPermissions() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void showCallError(String message) {
        CallForegroundService.stop(this);
        EzCallTelecomManager.disconnect(callId, DisconnectCause.ERROR);
        stopWaitingButtonAnimations();
        stopOutgoingCallTones();
        playEndSoundOnce();
        uiHandler.removeCallbacks(hideCallControlsRunnable);
        callControls = null;
        localPreviewContainer = null;
        localCameraOffOverlay = null;
        remoteAudioAvatar = null;
        connectedCallStage = null;
        connectedStatusPill = null;
        connectedStatusLabel = null;
        outgoingCallStateText = null;
        outgoingCallStatusText = null;
        callControlsVisible = false;
        if (webRtcCallClient != null) {
            webRtcCallClient.stop();
            webRtcCallClient = null;
        }
        root.removeAllViews();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setBackground(callScreenBackground(false));

        TextView title = text("Call could not start", 26, "#FFFFFF", Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        TextView body = text(message, 18, "#AFC2D8", Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.setMargins(0, dp(12), 0, dp(28));
        panel.addView(body, bodyParams);

        Button closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setTextSize(20);
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        closeButton.setBackground(rounded("#7C5CFC", dp(999), "#7C5CFC", 0));
        closeButton.setOnClickListener(view -> completeCallAndReturnHome(null));
        panel.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        root.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void completeCallAndReturnHome(String statusToWrite) {
        completeCallAndReturnHome(statusToWrite, true);
    }

    private void completeCallAndReturnHome(String statusToWrite, boolean notifyTelecom) {
        if (callClosureInProgress) {
            return;
        }
        callClosureInProgress = true;
        finalStatusWritten = true;
        updatePictureInPictureParams();
        stopWaitingButtonAnimations();
        stopOutgoingCallTones();
        uiHandler.removeCallbacks(outgoingCallTimeoutRunnable);
        uiHandler.removeCallbacks(hideCallControlsRunnable);
        CallSessionGuard.suppressFinishedCall(this, callId);
        ActiveCallTracker.clear(callId);
        cancelIncomingCallNotification();
        CallForegroundService.stop(this);
        if (notifyTelecom) {
            EzCallTelecomManager.disconnect(
                    callId,
                    disconnectCauseForStatus(statusToWrite)
            );
        }
        if (statusToWrite != null && !statusToWrite.trim().isEmpty()) {
            markFinalStatusWithAnsweredFallback(statusToWrite);
        }

        Intent homeIntent = new Intent(this, MainActivity.class);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(homeIntent);
        finish();
    }

    private void markFinalStatusWithAnsweredFallback(String requestedStatus) {
        FirebaseCallRepository.markCallInviteStatus(this, callId, requestedStatus)
                .addOnFailureListener(error -> {
                    if ("missed".equals(requestedStatus) || "declined".equals(requestedStatus)) {
                        FirebaseCallRepository.markCallInviteStatus(this, callId, "ended");
                    }
                });
    }

    private void setCallAudioActive(boolean active) {
        if (webRtcCallClient != null) {
            webRtcCallClient.setCallAudioEnabled(active);
        }
    }

    private String statusForTelecomDisconnect(int disconnectCode) {
        return TelecomDisconnectPolicy.inviteStatusForSystemDisconnect(
                disconnectCode,
                incomingCall,
                webRtcStarted
        );
    }

    private int disconnectCauseForStatus(String status) {
        return TelecomDisconnectPolicy.causeForInviteStatus(
                status,
                DisconnectCause.LOCAL
        );
    }

    private void playEndSoundOnce() {
        if (endSoundPlayed) {
            return;
        }
        endSoundPlayed = true;
        CallSoundPlayer.playEnded();
    }

    private final class AudioReactiveAvatar {
        private static final float SILENCE_FLOOR = 0.004f;

        final FrameLayout view;
        private final View ring;
        private final View glow;
        private float smoothedLevel;

        AudioReactiveAvatar(FrameLayout view, View ring, View glow) {
            this.view = view;
            this.ring = ring;
            this.glow = glow;
        }

        void setAudioLevel(float rawLevel) {
            float targetLevel = visualLevel(rawLevel);
            if (targetLevel > smoothedLevel) {
                smoothedLevel = smoothedLevel * 0.45f + targetLevel * 0.55f;
            } else {
                smoothedLevel = smoothedLevel * 0.82f + targetLevel * 0.18f;
            }

            animateRing(
                    ring,
                    1f + smoothedLevel * 0.72f,
                    0.58f + smoothedLevel * 0.42f
            );
            animateRing(
                    glow,
                    0.56f + smoothedLevel * 0.62f,
                    0.16f + smoothedLevel * 0.74f
            );
        }

        private float visualLevel(float rawLevel) {
            float clampedLevel = Math.max(0f, Math.min(1f, rawLevel));
            if (clampedLevel <= SILENCE_FLOOR) {
                return 0f;
            }
            float gatedLevel = (clampedLevel - SILENCE_FLOOR) / (1f - SILENCE_FLOOR);
            return Math.min(1f, (float) Math.sqrt(gatedLevel) * 2.4f);
        }

        private void animateRing(View ring, float scale, float alpha) {
            ring.animate().cancel();
            ring.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .alpha(alpha)
                    .setDuration(130L)
                    .start();
        }
    }

    private static final class StatePill {
        final View view;
        final TextView label;

        StatePill(View view, TextView label) {
            this.view = view;
            this.label = label;
        }
    }

    private ImageButton circularControl(int iconResId, String fillColor, String strokeColor) {
        ImageButton control = new ImageButton(this);
        control.setImageResource(iconResId);
        control.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        control.setPadding(dp(17), dp(17), dp(17), dp(17));
        control.setColorFilter(Color.WHITE);
        control.setBackground(rounded(fillColor, dp(999), strokeColor, 1));
        control.setElevation(dp(4));
        control.setClickable(true);
        control.setFocusable(true);
        return control;
    }

    private View labeledControl(ImageButton button, String label) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.addView(button, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView labelView = text(label, 13, "#A09DA9", Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, dp(5), 0, 0);
        wrapper.addView(labelView, labelParams);
        return wrapper;
    }

    private LinearLayout.LayoutParams weightedControlParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private TextView text(String value, int sp, String color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color(color));
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private GradientDrawable rounded(String fill, int radius, String stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setCornerRadius(radius);
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), color(stroke));
        }
        return drawable;
    }

    private GradientDrawable oval(String fill, String stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color(fill));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), color(stroke));
        }
        return drawable;
    }

    private GradientDrawable oval(int fill, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), stroke);
        }
        return drawable;
    }

    private GradientDrawable darkGlass(int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color("#66000000"));
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), color("#14FFFFFF"));
        return drawable;
    }

    private GradientDrawable glassCircle() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color("#12FFFFFF"), color("#0AFFFFFF")}
        );
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(1), color("#1AFFFFFF"));
        return drawable;
    }

    private GradientDrawable gradientCircle(String start, String end) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color(start), color(end)}
        );
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }

    private GradientDrawable previewGlass(int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.BLACK);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), color("#557C5CFC"));
        return drawable;
    }

    private View ambientGlow(String accent, int gradientRadius) {
        View glow = new View(this);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setGradientRadius(gradientRadius);
        drawable.setColors(new int[]{
                withAlpha(accent, 0x22),
                withAlpha(accent, 0x0D),
                Color.TRANSPARENT
        });
        glow.setBackground(drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            glow.setRenderEffect(RenderEffect.createBlurEffect(
                    dp(24),
                    dp(24),
                    Shader.TileMode.CLAMP
            ));
        }
        return glow;
    }

    private GradientDrawable callScreenBackground(boolean incoming) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                incoming
                        ? new int[]{color("#0D0E1F"), color("#0A0B18"), color("#080910")}
                        : new int[]{color("#0C0D1E"), color("#0A0B17"), color("#080910")}
        );
    }

    private void setProfileImage(ImageView imageView) {
        Bitmap bitmap = bitmapFromBase64(photoBase64);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(R.drawable.ic_default_user);
        }
    }

    private Bitmap bitmapFromBase64(String encodedImage) {
        return ProfilePhotoUtils.decodeBase64(encodedImage);
    }

    private int color(String hex) {
        return Color.parseColor(AppThemePalette.accentToken(
                AppSettings.accentPreference(this),
                hex
        ));
    }

    private int withAlpha(String hex, int alpha) {
        return (color(hex) & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
