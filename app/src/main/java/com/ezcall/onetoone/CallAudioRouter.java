package com.ezcall.onetoone;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

final class CallAudioRouter {
    private static final String TAG = "CallAudioRouter";

    private final AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            routeCallAudio();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            routeCallAudio();
        }
    };

    private AudioFocusRequest audioFocusRequest;
    private int originalMode;
    private boolean originalSpeakerphoneOn;
    private boolean started;

    CallAudioRouter(Context context) {
        audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    void start() {
        if (started || audioManager == null) {
            return;
        }
        started = true;
        originalMode = audioManager.getMode();
        originalSpeakerphoneOn = audioManager.isSpeakerphoneOn();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        requestAudioFocus();
        audioManager.registerAudioDeviceCallback(deviceCallback, handler);
        routeCallAudio();
        handler.postDelayed(this::routeCallAudio, 500L);
    }

    void stop() {
        if (!started || audioManager == null) {
            return;
        }
        started = false;
        handler.removeCallbacksAndMessages(null);
        audioManager.unregisterAudioDeviceCallback(deviceCallback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice();
        } else {
            audioManager.setSpeakerphoneOn(originalSpeakerphoneOn);
        }
        audioManager.setMode(originalMode);
        abandonAudioFocus();
    }

    private void routeCallAudio() {
        if (!started || audioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo selected = preferredCommunicationDevice(
                    audioManager.getAvailableCommunicationDevices()
            );
            if (selected != null && !audioManager.setCommunicationDevice(selected)) {
                Log.w(TAG, "Android rejected communication audio route type " + selected.getType());
            }
            return;
        }

        boolean externalAudio = audioManager.isWiredHeadsetOn() || audioManager.isBluetoothScoOn();
        audioManager.setSpeakerphoneOn(!externalAudio);
    }

    private AudioDeviceInfo preferredCommunicationDevice(List<AudioDeviceInfo> devices) {
        AudioDeviceInfo best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (AudioDeviceInfo device : devices) {
            int priority = devicePriority(device.getType());
            if (priority < bestPriority) {
                best = device;
                bestPriority = priority;
            }
        }
        return best;
    }

    private int devicePriority(int type) {
        if (type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            return 0;
        }
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return 1;
        }
        if (type == AudioDeviceInfo.TYPE_HEARING_AID) {
            return 2;
        }
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            return 3;
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            return 10;
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            return 20;
        }
        return 15;
    }

    private void requestAudioFocus() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusChange -> {
                }, handler)
                .build();
        audioManager.requestAudioFocus(audioFocusRequest);
    }

    private void abandonAudioFocus() {
        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        }
    }
}
