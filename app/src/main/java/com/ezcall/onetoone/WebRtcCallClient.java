package com.ezcall.onetoone;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WebRtcCallClient {
    interface RemoteCameraStateListener {
        void onRemoteCameraEnabled(boolean enabled);
    }

    interface RemoteAudioLevelListener {
        void onRemoteAudioLevel(float level);
    }

    interface ConnectionStateListener {
        void onConnected();

        void onReconnecting();

        void onReconnectFailed();
    }

    private static final String TAG = "WebRtcCallClient";
    private static final String VIDEO_TRACK_ID = "memory-calls-video";
    private static final String AUDIO_TRACK_ID = "memory-calls-audio";
    private static final String STREAM_ID = "memory-calls-stream";
    private static final long CAMERA_SWITCH_STABILIZATION_MILLIS = 700L;
    private static final long INITIAL_CAMERA_FRAME_TIMEOUT_MILLIS = 2500L;
    private static final long AUDIO_LEVEL_POLL_MILLIS = 120L;
    private static final long DISCONNECT_RESTART_DELAY_MILLIS = 1200L;
    private static final long RESTART_RETRY_MILLIS = 7000L;
    private static final long RECONNECT_TIMEOUT_MILLIS = 25_000L;

    private final Context context;
    private final Object cameraLifecycleLock = new Object();
    private final String callId;
    private final boolean caller;
    private final String participantId = UUID.randomUUID().toString();
    private final SurfaceViewRenderer localRenderer;
    private final SurfaceViewRenderer remoteRenderer;
    private final RemoteCameraStateListener remoteCameraStateListener;
    private final RemoteAudioLevelListener remoteAudioLevelListener;
    private final ConnectionStateListener connectionStateListener;
    private final DocumentReference callRef;
    private final CallAudioRouter callAudioRouter;
    private final Handler connectionHandler = new Handler(Looper.getMainLooper());
    private final Handler audioLevelHandler = new Handler(Looper.getMainLooper());
    private final Handler cameraStateHandler = new Handler(Looper.getMainLooper());
    private final RemoteAudioLevelMeter remoteAudioLevelMeter = new RemoteAudioLevelMeter();
    private final Set<String> seenCandidateIds = new HashSet<>();
    private final List<GenerationCandidate> pendingRemoteCandidates = new ArrayList<>();
    private final List<ListenerRegistration> listeners = new ArrayList<>();

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper cameraTextureHelper;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private AudioTrack localAudioTrack;
    private AudioTrack remoteAudioTrack;
    private int localIceGeneration = IceRestartSignaling.INITIAL_GENERATION;
    private int remoteDescriptionGeneration;
    private int handledOfferGeneration;
    private int handlingOfferGeneration;
    private int handledAnswerGeneration;
    private int handlingAnswerGeneration;
    private boolean remoteDescriptionSet;
    private boolean everConnected;
    private boolean reconnecting;
    private boolean restartOfferInProgress;
    private boolean restartRequestListenerInitialized;
    private long lastObservedRestartRequest;
    private long lastPublishedRestartRequest;
    private String activeNetworkId = "";
    private boolean networkWasLost;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean usingFrontCamera = true;
    private volatile boolean cameraEnabled = true;
    private volatile boolean callAudioEnabled = true;
    private volatile boolean microphoneEnabled = true;
    private boolean fallbackAudioRouting;
    private volatile boolean cameraSwitchInProgress;
    private volatile boolean firstLocalCameraFrameReceived;
    private volatile boolean initialCameraRecoveryAttempted;
    private volatile boolean cameraRecoveryInProgress;
    private boolean remoteCameraSwitching;
    private volatile boolean stopped;
    private final Runnable remoteAudioLevelPoll = this::requestRemoteAudioLevel;
    private final Runnable initialCameraFrameWatchdog = this::recoverInitialCameraIfNeeded;
    private final Runnable delayedRestartRequest = () -> requestIceRestart("ICE disconnected");
    private final Runnable restartRetry = this::retryIceRestart;
    private final Runnable reconnectTimeout = this::handleReconnectTimeout;
    private final CameraVideoCapturer.CameraEventsHandler cameraEventsHandler =
            new CameraVideoCapturer.CameraEventsHandler() {
                @Override
                public void onCameraError(String errorDescription) {
                    Log.e(TAG, "Camera error: " + errorDescription);
                }

                @Override
                public void onCameraDisconnected() {
                    Log.w(TAG, "Camera disconnected.");
                }

                @Override
                public void onCameraFreezed(String errorDescription) {
                    Log.w(TAG, "Camera froze: " + errorDescription);
                    scheduleInitialCameraFrameWatchdog();
                }

                @Override
                public void onCameraOpening(String cameraName) {
                    Log.d(TAG, "Opening camera " + cameraName);
                }

                @Override
                public void onFirstFrameAvailable() {
                    firstLocalCameraFrameReceived = true;
                    cameraStateHandler.removeCallbacks(initialCameraFrameWatchdog);
                    Log.i(TAG, "First local camera frame is available.");
                }

                @Override
                public void onCameraClosed() {
                    Log.d(TAG, "Camera closed.");
                }
            };

    WebRtcCallClient(
            Context context,
            String callId,
            boolean caller,
            SurfaceViewRenderer localRenderer,
            SurfaceViewRenderer remoteRenderer,
            RemoteCameraStateListener remoteCameraStateListener,
            RemoteAudioLevelListener remoteAudioLevelListener,
            ConnectionStateListener connectionStateListener
    ) {
        this.context = context.getApplicationContext();
        this.callId = callId;
        this.caller = caller;
        this.localRenderer = localRenderer;
        this.remoteRenderer = remoteRenderer;
        this.remoteCameraStateListener = remoteCameraStateListener;
        this.remoteAudioLevelListener = remoteAudioLevelListener;
        this.connectionStateListener = connectionStateListener;
        this.callRef = FirebaseFirestore.getInstance().collection("calls").document(callId);
        this.callAudioRouter = new CallAudioRouter(this.context);
    }

    void start() {
        try {
            stopped = false;
            fallbackAudioRouting = !EzCallTelecomManager.isManaging(callId);
            if (fallbackAudioRouting) {
                callAudioRouter.start();
            }
            initializeFactory();
            initializeRenderers();
            createPeerConnection();
            createLocalMedia();
            listenForRemoteCandidates();
            listenForRemoteCameraState();
            monitorNetworkChanges();
            publishCameraState(false);
            audioLevelHandler.post(remoteAudioLevelPoll);

            if (caller) {
                listenForAnswer();
                listenForCalleeRestartRequests();
                createOffer(IceRestartSignaling.INITIAL_GENERATION, false);
            } else {
                listenForOffer();
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Failed to start WebRTC client", error);
            stop();
            throw error;
        }
    }

    void stop() {
        stopped = true;
        audioLevelHandler.removeCallbacks(remoteAudioLevelPoll);
        cameraStateHandler.removeCallbacks(initialCameraFrameWatchdog);
        connectionHandler.removeCallbacksAndMessages(null);
        stopNetworkMonitoring();
        for (ListenerRegistration listener : listeners) {
            listener.remove();
        }
        listeners.clear();

        if (remoteVideoTrack != null) {
            remoteVideoTrack.removeSink(remoteRenderer);
            remoteVideoTrack = null;
        }
        remoteAudioTrack = null;

        synchronized (cameraLifecycleLock) {
            if (videoCapturer != null) {
                try {
                    videoCapturer.stopCapture();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
                videoCapturer.dispose();
                videoCapturer = null;
            }
        }
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }
        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }
        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (cameraTextureHelper != null) {
            cameraTextureHelper.dispose();
            cameraTextureHelper = null;
        }
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        localRenderer.release();
        remoteRenderer.release();
        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
        if (fallbackAudioRouting) {
            callAudioRouter.stop();
            fallbackAudioRouting = false;
        }
    }

    private void requestRemoteAudioLevel() {
        PeerConnection connection = peerConnection;
        if (stopped || connection == null) {
            return;
        }
        connection.getStats(report -> {
            if (stopped) {
                return;
            }
            float level = remoteAudioLevelMeter.read(report);
            localRenderer.post(() -> {
                if (!stopped && remoteAudioLevelListener != null) {
                    remoteAudioLevelListener.onRemoteAudioLevel(level);
                }
            });
            audioLevelHandler.postDelayed(remoteAudioLevelPoll, AUDIO_LEVEL_POLL_MILLIS);
        });
    }

    void setCameraEnabled(boolean enabled) {
        cameraEnabled = enabled;
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enabled && !cameraSwitchInProgress);
        }
        if (enabled) {
            scheduleInitialCameraFrameWatchdog();
        }
        publishCameraState(cameraSwitchInProgress);
    }

    void switchCamera() {
        if (cameraSwitchInProgress) {
            return;
        }
        if (videoCapturer instanceof CameraVideoCapturer) {
            cameraSwitchInProgress = true;
            if (localVideoTrack != null) {
                localVideoTrack.setEnabled(false);
            }
            publishCameraState(true);
            ((CameraVideoCapturer) videoCapturer).switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFrontCamera) {
                    usingFrontCamera = isFrontCamera;
                    localRenderer.post(() -> localRenderer.setMirror(isFrontCamera));
                    publishCameraState(true);
                    localRenderer.postDelayed(() -> {
                        cameraSwitchInProgress = false;
                        if (cameraEnabled && localVideoTrack != null) {
                            localVideoTrack.setEnabled(true);
                        }
                        publishCameraState(false);
                    }, CAMERA_SWITCH_STABILIZATION_MILLIS);
                }

                @Override
                public void onCameraSwitchError(String errorDescription) {
                    Log.e(TAG, "Could not switch camera: " + errorDescription);
                    localRenderer.post(() -> {
                        cameraSwitchInProgress = false;
                        if (cameraEnabled && localVideoTrack != null) {
                            localVideoTrack.setEnabled(true);
                        }
                        publishCameraState(false);
                    });
                }
            });
        } else {
            Log.w(TAG, "Camera switch requested, but current capturer does not support switching.");
        }
    }

    private void initializeFactory() {
        eglBase = EglBase.create();
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
        );

        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(),
                true,
                true
        );
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    private void initializeRenderers() {
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setMirror(true);
        localRenderer.setEnableHardwareScaler(true);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        localRenderer.setZOrderMediaOverlay(true);

        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.setMirror(false);
        remoteRenderer.setEnableHardwareScaler(true);
        remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        remoteRenderer.setZOrderMediaOverlay(false);
    }

    private void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(iceServers);
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState state) {
                Log.d(TAG, "Signaling state: " + state);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                Log.d(TAG, "ICE connection state: " + state);
                connectionHandler.post(() -> handleIceConnectionState(state));
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                Log.d(TAG, "Peer connection state: " + state);
                connectionHandler.post(() -> handlePeerConnectionState(state));
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
                Log.d(TAG, "ICE receiving: " + receiving);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                Log.d(TAG, "ICE gathering state: " + state);
            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                Log.d(TAG, "Local ICE candidate gathered: " + candidateSummary(candidate));
                writeLocalCandidate(candidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            }

            @Override
            public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
                Log.d(TAG, "Selected candidate pair changed.");
            }

            @Override
            public void onAddStream(MediaStream stream) {
                if (stream.videoTracks.size() > 0) {
                    Log.d(TAG, "Remote video stream received via onAddStream.");
                    attachRemoteVideoTrack(stream.videoTracks.get(0));
                }
                if (stream.audioTracks.size() > 0) {
                    attachRemoteAudioTrack(stream.audioTracks.get(0));
                }
            }

            @Override
            public void onRemoveStream(MediaStream stream) {
            }

            @Override
            public void onDataChannel(DataChannel channel) {
            }

            @Override
            public void onRenegotiationNeeded() {
            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                if (receiver.track() instanceof VideoTrack) {
                    Log.d(TAG, "Remote video track received via onAddTrack.");
                    attachRemoteVideoTrack((VideoTrack) receiver.track());
                } else if (receiver.track() instanceof AudioTrack) {
                    attachRemoteAudioTrack((AudioTrack) receiver.track());
                }
            }
        });
    }

    private void createLocalMedia() {
        videoCapturer = createCameraCapturer();
        if (videoCapturer != null) {
            localRenderer.setMirror(usingFrontCamera);
            cameraTextureHelper = SurfaceTextureHelper.create(
                    "MemoryCallsCameraThread",
                    eglBase.getEglBaseContext()
            );
            videoSource = factory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(cameraTextureHelper, context, videoSource.getCapturerObserver());
            videoCapturer.startCapture(1280, 720, 30);
            localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
            localVideoTrack.addSink(localRenderer);
            peerConnection.addTrack(localVideoTrack, List.of(STREAM_ID));
            scheduleInitialCameraFrameWatchdog();
        } else {
            Log.w(TAG, "No camera capturer was available for local video.");
            cameraEnabled = false;
        }

        audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(callAudioEnabled && microphoneEnabled);
        peerConnection.addTrack(localAudioTrack, List.of(STREAM_ID));
    }

    void setCallAudioEnabled(boolean enabled) {
        callAudioEnabled = enabled;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled && microphoneEnabled);
        }
        if (remoteAudioTrack != null) {
            remoteAudioTrack.setEnabled(enabled);
        }
    }

    void setMicrophoneEnabled(boolean enabled) {
        microphoneEnabled = enabled;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled && callAudioEnabled);
        }
    }

    private void attachRemoteAudioTrack(AudioTrack track) {
        remoteAudioTrack = track;
        remoteAudioTrack.setEnabled(callAudioEnabled);
    }

    private VideoCapturer createCameraCapturer() {
        CameraEnumerator enumerator = new Camera2Enumerator(context);
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, cameraEventsHandler);
                if (capturer != null) {
                    usingFrontCamera = true;
                    return capturer;
                }
            }
        }
        for (String deviceName : enumerator.getDeviceNames()) {
            VideoCapturer capturer = enumerator.createCapturer(deviceName, cameraEventsHandler);
            if (capturer != null) {
                usingFrontCamera = enumerator.isFrontFacing(deviceName);
                return capturer;
            }
        }
        return null;
    }

    private void scheduleInitialCameraFrameWatchdog() {
        if (stopped
                || !cameraEnabled
                || firstLocalCameraFrameReceived
                || initialCameraRecoveryAttempted
                || videoCapturer == null) {
            return;
        }
        cameraStateHandler.removeCallbacks(initialCameraFrameWatchdog);
        cameraStateHandler.postDelayed(
                initialCameraFrameWatchdog,
                INITIAL_CAMERA_FRAME_TIMEOUT_MILLIS
        );
    }

    private void recoverInitialCameraIfNeeded() {
        if (stopped
                || !cameraEnabled
                || firstLocalCameraFrameReceived
                || initialCameraRecoveryAttempted
                || cameraRecoveryInProgress
                || videoCapturer == null) {
            return;
        }
        initialCameraRecoveryAttempted = true;
        cameraRecoveryInProgress = true;
        Log.w(TAG, "No initial camera frame arrived; restarting the current camera once.");
        Thread recoveryThread = new Thread(() -> {
            synchronized (cameraLifecycleLock) {
                VideoCapturer capturer = videoCapturer;
                if (stopped || capturer == null) {
                    cameraRecoveryInProgress = false;
                    return;
                }
                try {
                    capturer.stopCapture();
                    if (!stopped) {
                        capturer.startCapture(1280, 720, 30);
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Camera startup recovery was interrupted.", error);
                } catch (RuntimeException error) {
                    Log.e(TAG, "Camera startup recovery failed.", error);
                } finally {
                    cameraRecoveryInProgress = false;
                }
            }
        }, "EzCallCameraRecovery");
        recoveryThread.start();
    }

    private void createOffer(int generation, boolean iceRestart) {
        localIceGeneration = generation;
        if (!iceRestart) {
            callRef.set(baseCallData(), com.google.firebase.firestore.SetOptions.merge());
        }
        peerConnection.createOffer(new SimpleSdpObserver("create offer") {
            @Override
            public void onCreateSuccess(SessionDescription description) {
                Log.d(TAG, "Created local offer with SDP length " + sdpLength(description));
                peerConnection.setLocalDescription(new SimpleSdpObserver("set local offer") {
                    @Override
                    public void onSetSuccess() {
                        Map<String, Object> offerData = new HashMap<>();
                        offerData.put("offer", sdpMap(description));
                        offerData.put("offerGeneration", generation);
                        offerData.put("negotiationUpdatedAt", FieldValue.serverTimestamp());
                        callRef.set(offerData, com.google.firebase.firestore.SetOptions.merge());
                        Log.i(TAG, (iceRestart ? "Published ICE-restart" : "Published initial")
                                + " offer generation " + generation);
                    }

                    @Override
                    public void onSetFailure(String error) {
                        super.onSetFailure(error);
                        restartOfferInProgress = false;
                        scheduleRestartRetry();
                    }
                }, description);
            }

            @Override
            public void onCreateFailure(String error) {
                super.onCreateFailure(error);
                restartOfferInProgress = false;
                scheduleRestartRetry();
            }
        }, mediaConstraints());
    }

    private void listenForOffer() {
        listeners.add(callRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Offer listener failed", error);
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }

            Map<String, Object> offer = mapFromValue(snapshot.get("offer"));
            if (offer == null) {
                return;
            }
            int generation = IceRestartSignaling.generation(snapshot.get("offerGeneration"));
            if (!IceRestartSignaling.shouldHandleOffer(
                    generation,
                    handledOfferGeneration,
                    handlingOfferGeneration
            )) {
                return;
            }

            SessionDescription remoteOffer = sessionDescriptionFromMap(offer);
            if (remoteOffer == null) {
                Log.w(TAG, "Ignoring malformed offer");
                return;
            }

            handlingOfferGeneration = generation;
            localIceGeneration = generation;
            remoteDescriptionSet = false;
            Log.d(TAG, "Applying remote offer generation " + generation
                    + " with SDP length " + sdpLength(remoteOffer));
            peerConnection.setRemoteDescription(new SimpleSdpObserver("set remote offer") {
                @Override
                public void onSetSuccess() {
                    handlingOfferGeneration = 0;
                    handledOfferGeneration = generation;
                    remoteDescriptionGeneration = generation;
                    remoteDescriptionSet = true;
                    flushPendingRemoteCandidates();
                    if (generation > IceRestartSignaling.INITIAL_GENERATION) {
                        markReconnecting("Remote ICE restart");
                    }
                    createAnswer(generation);
                }

                @Override
                public void onSetFailure(String error) {
                    super.onSetFailure(error);
                    handlingOfferGeneration = 0;
                }
            }, remoteOffer);
        }));
    }

    private void createAnswer(int generation) {
        peerConnection.createAnswer(new SimpleSdpObserver("create answer") {
            @Override
            public void onCreateSuccess(SessionDescription description) {
                Log.d(TAG, "Created local answer with SDP length " + sdpLength(description));
                peerConnection.setLocalDescription(new SimpleSdpObserver("set local answer") {
                    @Override
                    public void onSetSuccess() {
                        Map<String, Object> answerData = new HashMap<>();
                        answerData.put("answer", sdpMap(description));
                        answerData.put("answerGeneration", generation);
                        answerData.put("negotiationUpdatedAt", FieldValue.serverTimestamp());
                        callRef.set(answerData, com.google.firebase.firestore.SetOptions.merge());
                        Log.i(TAG, "Published answer generation " + generation);
                    }
                }, description);
            }
        }, mediaConstraints());
    }

    private void listenForAnswer() {
        listeners.add(callRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Answer listener failed", error);
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }

            Map<String, Object> answer = mapFromValue(snapshot.get("answer"));
            if (answer == null) {
                return;
            }
            int generation = IceRestartSignaling.generation(snapshot.get("answerGeneration"));
            if (!IceRestartSignaling.shouldHandleAnswer(
                    generation,
                    localIceGeneration,
                    handledAnswerGeneration,
                    handlingAnswerGeneration
            )) {
                return;
            }

            SessionDescription remoteAnswer = sessionDescriptionFromMap(answer);
            if (remoteAnswer == null) {
                Log.w(TAG, "Ignoring malformed answer");
                return;
            }

            handlingAnswerGeneration = generation;
            remoteDescriptionSet = false;
            Log.d(TAG, "Applying remote answer generation " + generation
                    + " with SDP length " + sdpLength(remoteAnswer));
            peerConnection.setRemoteDescription(new SimpleSdpObserver("set remote answer") {
                @Override
                public void onSetSuccess() {
                    handlingAnswerGeneration = 0;
                    handledAnswerGeneration = generation;
                    remoteDescriptionGeneration = generation;
                    remoteDescriptionSet = true;
                    restartOfferInProgress = false;
                    flushPendingRemoteCandidates();
                    maybeConfirmConnected();
                }

                @Override
                public void onSetFailure(String error) {
                    super.onSetFailure(error);
                    handlingAnswerGeneration = 0;
                    restartOfferInProgress = false;
                    scheduleRestartRetry();
                }
            }, remoteAnswer);
        }));
    }

    private void writeLocalCandidate(IceCandidate candidate) {
        Map<String, Object> data = new HashMap<>();
        data.put("sender", participantId);
        data.put("sdpMid", candidate.sdpMid);
        data.put("sdpMLineIndex", candidate.sdpMLineIndex);
        data.put("candidate", candidate.sdp);
        data.put("generation", localIceGeneration);
        data.put("createdAt", FieldValue.serverTimestamp());

        callRef.collection("candidates")
                .add(data)
                .addOnSuccessListener(document -> Log.d(TAG, "Wrote local ICE candidate " + document.getId()))
                .addOnFailureListener(error -> Log.e(TAG, "Failed to write local ICE candidate", error));
    }

    private void listenForRemoteCandidates() {
        listeners.add(callRef.collection("candidates").addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Candidate listener failed", error);
                return;
            }
            if (snapshot == null || snapshot.isEmpty()) {
                return;
            }

            for (int i = 0; i < snapshot.getDocuments().size(); i++) {
                String documentId = snapshot.getDocuments().get(i).getId();
                if (seenCandidateIds.contains(documentId)) {
                    continue;
                }
                seenCandidateIds.add(documentId);

                Map<String, Object> data = snapshot.getDocuments().get(i).getData();
                if (data == null || participantId.equals(data.get("sender"))) {
                    continue;
                }

                IceCandidate remoteCandidate = iceCandidateFromMap(data);
                if (remoteCandidate == null) {
                    Log.w(TAG, "Skipping malformed ICE candidate " + documentId);
                    continue;
                }
                int generation = IceRestartSignaling.generation(data.get("generation"));
                if (remoteDescriptionSet && IceRestartSignaling.candidateMatches(
                        generation,
                        remoteDescriptionGeneration
                )) {
                    boolean added = peerConnection.addIceCandidate(remoteCandidate);
                    Log.d(TAG, "Remote ICE candidate " + documentId + " generation "
                            + generation + " added=" + added + ": " + candidateSummary(remoteCandidate));
                } else if (generation >= Math.max(
                        IceRestartSignaling.INITIAL_GENERATION,
                        remoteDescriptionGeneration
                )) {
                    Log.d(TAG, "Queued remote ICE candidate " + documentId
                            + " generation " + generation + ": " + candidateSummary(remoteCandidate));
                    pendingRemoteCandidates.add(new GenerationCandidate(generation, remoteCandidate));
                } else {
                    Log.d(TAG, "Ignored stale ICE candidate " + documentId
                            + " generation " + generation);
                }
            }
        }));
    }

    private void flushPendingRemoteCandidates() {
        List<GenerationCandidate> futureCandidates = new ArrayList<>();
        for (GenerationCandidate pending : pendingRemoteCandidates) {
            if (pending.generation > remoteDescriptionGeneration) {
                futureCandidates.add(pending);
                continue;
            }
            if (!IceRestartSignaling.candidateMatches(
                    pending.generation,
                    remoteDescriptionGeneration
            )) {
                continue;
            }
            boolean added = peerConnection.addIceCandidate(pending.candidate);
            Log.d(TAG, "Flushed queued remote ICE candidate generation "
                    + pending.generation + " added=" + added + ": "
                    + candidateSummary(pending.candidate));
        }
        pendingRemoteCandidates.clear();
        pendingRemoteCandidates.addAll(futureCandidates);
    }

    private void listenForCalleeRestartRequests() {
        listeners.add(callRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "ICE-restart request listener failed", error);
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            long request = longValue(snapshot.get("calleeIceRestartRequest"), 0L);
            if (!restartRequestListenerInitialized) {
                restartRequestListenerInitialized = true;
                lastObservedRestartRequest = request;
                return;
            }
            if (request <= lastObservedRestartRequest) {
                return;
            }
            lastObservedRestartRequest = request;
            if (everConnected) {
                markReconnecting("Callee requested ICE restart");
                beginCallerIceRestart("callee network change");
            }
        }));
    }

    private void handleIceConnectionState(PeerConnection.IceConnectionState state) {
        if (stopped) {
            return;
        }
        if (state == PeerConnection.IceConnectionState.CONNECTED
                || state == PeerConnection.IceConnectionState.COMPLETED) {
            handleConnected();
        } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
            handleConnectionInterrupted(false);
        } else if (state == PeerConnection.IceConnectionState.FAILED) {
            handleConnectionInterrupted(true);
        }
    }

    private void handlePeerConnectionState(PeerConnection.PeerConnectionState state) {
        if (stopped) {
            return;
        }
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            handleConnected();
        } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
            handleConnectionInterrupted(false);
        } else if (state == PeerConnection.PeerConnectionState.FAILED) {
            handleConnectionInterrupted(true);
        }
    }

    private void handleConnected() {
        if (stopped) {
            return;
        }
        everConnected = true;
        reconnecting = false;
        restartOfferInProgress = false;
        connectionHandler.removeCallbacks(delayedRestartRequest);
        connectionHandler.removeCallbacks(restartRetry);
        connectionHandler.removeCallbacks(reconnectTimeout);
        if (connectionStateListener != null) {
            connectionStateListener.onConnected();
        }
    }

    private void handleConnectionInterrupted(boolean failed) {
        if (!everConnected || stopped) {
            if (failed) {
                Log.w(TAG, "ICE failed before media could flow. Check signaling, network reachability, or TURN.");
            }
            return;
        }
        markReconnecting(failed ? "ICE failed" : "ICE disconnected");
        connectionHandler.removeCallbacks(delayedRestartRequest);
        connectionHandler.postDelayed(
                delayedRestartRequest,
                failed ? 0L : DISCONNECT_RESTART_DELAY_MILLIS
        );
    }

    private void requestIceRestart(String reason) {
        if (stopped || !everConnected) {
            return;
        }
        markReconnecting(reason);
        if (caller) {
            beginCallerIceRestart(reason);
        } else {
            publishCalleeRestartRequest(reason);
        }
    }

    private void markReconnecting(String reason) {
        if (!everConnected || stopped) {
            return;
        }
        Log.w(TAG, "Call connection interrupted: " + reason);
        if (!reconnecting) {
            reconnecting = true;
            if (connectionStateListener != null) {
                connectionStateListener.onReconnecting();
            }
            connectionHandler.removeCallbacks(reconnectTimeout);
            connectionHandler.postDelayed(reconnectTimeout, RECONNECT_TIMEOUT_MILLIS);
        }
    }

    private void beginCallerIceRestart(String reason) {
        if (stopped || !caller || !everConnected || !reconnecting) {
            return;
        }
        PeerConnection connection = peerConnection;
        if (connection == null) {
            return;
        }
        if (restartOfferInProgress
                || connection.signalingState() != PeerConnection.SignalingState.STABLE) {
            scheduleRestartRetry();
            return;
        }
        restartOfferInProgress = true;
        int nextGeneration = localIceGeneration + 1;
        Log.i(TAG, "Starting ICE restart generation " + nextGeneration + ": " + reason);
        connection.restartIce();
        createOffer(nextGeneration, true);
        scheduleRestartRetry();
    }

    private void publishCalleeRestartRequest(String reason) {
        long request = Math.max(System.currentTimeMillis(), lastPublishedRestartRequest + 1L);
        lastPublishedRestartRequest = request;
        Map<String, Object> data = new HashMap<>();
        data.put("calleeIceRestartRequest", request);
        data.put("calleeIceRestartReason", reason);
        data.put("negotiationUpdatedAt", FieldValue.serverTimestamp());
        callRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(error -> Log.e(TAG, "Failed to request ICE restart", error));
    }

    private void scheduleRestartRetry() {
        connectionHandler.removeCallbacks(restartRetry);
        if (reconnecting) {
            connectionHandler.postDelayed(restartRetry, RESTART_RETRY_MILLIS);
        }
    }

    private void retryIceRestart() {
        if (reconnecting && caller) {
            beginCallerIceRestart("recovery retry");
        }
    }

    private void handleReconnectTimeout() {
        if (!stopped && reconnecting && connectionStateListener != null) {
            connectionStateListener.onReconnectFailed();
        }
    }

    private void maybeConfirmConnected() {
        connectionHandler.postDelayed(() -> {
            PeerConnection connection = peerConnection;
            if (!stopped
                    && reconnecting
                    && connection != null
                    && connection.connectionState() == PeerConnection.PeerConnectionState.CONNECTED) {
                handleConnected();
            }
        }, 300L);
    }

    private void monitorNetworkChanges() {
        connectivityManager = context.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return;
        }
        Network initialNetwork = connectivityManager.getActiveNetwork();
        activeNetworkId = initialNetwork == null ? "" : initialNetwork.toString();
        networkWasLost = initialNetwork == null;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                connectionHandler.post(() -> {
                    String newNetworkId = network == null ? "" : network.toString();
                    boolean changed = networkWasLost
                            || (!activeNetworkId.isEmpty() && !activeNetworkId.equals(newNetworkId));
                    activeNetworkId = newNetworkId;
                    networkWasLost = false;
                    if (changed && everConnected) {
                        requestIceRestart("Active network changed");
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                connectionHandler.post(() -> {
                    String lostNetworkId = network == null ? "" : network.toString();
                    if (activeNetworkId.equals(lostNetworkId)) {
                        activeNetworkId = "";
                        networkWasLost = true;
                        markReconnecting("Active network lost");
                    }
                });
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not monitor network changes", error);
            networkCallback = null;
        }
    }

    private void stopNetworkMonitoring() {
        if (connectivityManager == null || networkCallback == null) {
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not unregister network callback", error);
        }
        networkCallback = null;
        connectivityManager = null;
    }

    private void publishCameraState(boolean switching) {
        Map<String, Object> data = new HashMap<>();
        if (videoCapturer != null) {
            data.put(localCameraFacingField(), usingFrontCamera ? "front" : "back");
        }
        data.put(localCameraSwitchingField(), switching);
        data.put(localCameraEnabledField(), cameraEnabled && videoCapturer != null);
        callRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(error -> Log.e(TAG, "Failed to publish camera state", error));
    }

    private void listenForRemoteCameraState() {
        listeners.add(callRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Camera-state listener failed", error);
                return;
            }
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            String facing = value(snapshot.get(remoteCameraFacingField()));
            boolean switching = Boolean.TRUE.equals(snapshot.get(remoteCameraSwitchingField()));
            Object enabledValue = snapshot.get(remoteCameraEnabledField());
            boolean enabled = !(enabledValue instanceof Boolean) || Boolean.TRUE.equals(enabledValue);
            remoteRenderer.post(() -> {
                if ("front".equals(facing) || "back".equals(facing)) {
                    remoteRenderer.setMirror("front".equals(facing));
                }
                setRemoteCameraSwitching(switching);
                if (remoteCameraStateListener != null) {
                    remoteCameraStateListener.onRemoteCameraEnabled(enabled);
                }
            });
        }));
    }

    private void attachRemoteVideoTrack(VideoTrack track) {
        if (track == remoteVideoTrack) {
            return;
        }
        if (remoteVideoTrack != null) {
            remoteVideoTrack.removeSink(remoteRenderer);
        }
        remoteVideoTrack = track;
        if (!remoteCameraSwitching) {
            remoteVideoTrack.addSink(remoteRenderer);
        }
    }

    private void setRemoteCameraSwitching(boolean switching) {
        if (remoteCameraSwitching == switching) {
            return;
        }
        remoteCameraSwitching = switching;
        if (remoteVideoTrack == null) {
            return;
        }
        if (switching) {
            remoteVideoTrack.removeSink(remoteRenderer);
        } else {
            remoteVideoTrack.addSink(remoteRenderer);
        }
    }

    private String localCameraFacingField() {
        return caller ? "callerCameraFacing" : "calleeCameraFacing";
    }

    private String remoteCameraFacingField() {
        return caller ? "calleeCameraFacing" : "callerCameraFacing";
    }

    private String localCameraSwitchingField() {
        return caller ? "callerCameraSwitching" : "calleeCameraSwitching";
    }

    private String remoteCameraSwitchingField() {
        return caller ? "calleeCameraSwitching" : "callerCameraSwitching";
    }

    private String localCameraEnabledField() {
        return caller ? "callerCameraEnabled" : "calleeCameraEnabled";
    }

    private String remoteCameraEnabledField() {
        return caller ? "calleeCameraEnabled" : "callerCameraEnabled";
    }

    private String candidateSummary(IceCandidate candidate) {
        if (candidate == null) {
            return "null";
        }
        return "mid=" + candidate.sdpMid
                + ", line=" + candidate.sdpMLineIndex
                + ", type=" + candidateType(candidate.sdp);
    }

    private String candidateType(String sdp) {
        if (sdp == null) {
            return "unknown";
        }
        String[] parts = sdp.split(" ");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("typ".equals(parts[i])) {
                return parts[i + 1];
            }
        }
        return "unknown";
    }

    private Map<String, Object> baseCallData() {
        Map<String, Object> data = new HashMap<>();
        data.put("callId", callId);
        data.put("updatedAt", FieldValue.serverTimestamp());
        return data;
    }

    private Map<String, Object> sdpMap(SessionDescription description) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", description.type.canonicalForm());
        data.put("sdp", description.description);
        return data;
    }

    private int sdpLength(SessionDescription description) {
        return description == null || description.description == null ? 0 : description.description.length();
    }

    private SessionDescription sessionDescriptionFromMap(Map<String, Object> data) {
        String type = value(data.get("type"));
        String sdp = sdpValue(data.get("sdp"));
        if (type.isEmpty() || sdp.isEmpty()) {
            return null;
        }
        SessionDescription.Type descriptionType = SessionDescription.Type.fromCanonicalForm(type);
        if (descriptionType == null) {
            return null;
        }
        return new SessionDescription(descriptionType, sdp);
    }

    private String sdpValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.trim().isEmpty() || "null".equals(text.trim())) {
            return "";
        }
        return text.endsWith("\n") ? text : text + "\r\n";
    }

    private IceCandidate iceCandidateFromMap(Map<String, Object> data) {
        Object candidateObject = data.get("candidate");
        if (candidateObject instanceof Map) {
            Map<String, Object> candidateMap = mapFromValue(candidateObject);
            if (candidateMap != null) {
                return iceCandidateFromFields(candidateMap);
            }
        }
        return iceCandidateFromFields(data);
    }

    private IceCandidate iceCandidateFromFields(Map<String, Object> data) {
        String candidate = firstValue(data, "candidate", "sdp", "candidateString");
        String sdpMid = firstValue(data, "sdpMid", "id", "mid");
        int sdpMLineIndex = intValue(firstObject(data, "sdpMLineIndex", "label", "mLineIndex"), -1);
        if (candidate.isEmpty() || sdpMLineIndex < 0) {
            return null;
        }
        return new IceCandidate(sdpMid.isEmpty() ? "0" : sdpMid, sdpMLineIndex, candidate);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapFromValue(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private Object firstObject(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            String value = value(data.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException error) {
                return fallback;
            }
        }
        return fallback;
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException error) {
                return fallback;
            }
        }
        return fallback;
    }

    private String value(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return text.trim().isEmpty() || "null".equals(text) ? "" : text.trim();
    }

    private MediaConstraints mediaConstraints() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        return constraints;
    }

    private static final class GenerationCandidate {
        final int generation;
        final IceCandidate candidate;

        GenerationCandidate(int generation, IceCandidate candidate) {
            this.generation = generation;
            this.candidate = candidate;
        }
    }

    private static class SimpleSdpObserver implements SdpObserver {
        private final String operation;

        SimpleSdpObserver() {
            this("SDP operation");
        }

        SimpleSdpObserver(String operation) {
            this.operation = operation;
        }

        @Override
        public void onCreateSuccess(SessionDescription description) {
        }

        @Override
        public void onSetSuccess() {
            Log.d(TAG, operation + " succeeded");
        }

        @Override
        public void onCreateFailure(String error) {
            Log.e(TAG, operation + " create failed: " + error);
        }

        @Override
        public void onSetFailure(String error) {
            Log.e(TAG, operation + " set failed: " + error);
        }
    }
}
