package com.ezcall.onetoone;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

final class CallSoundPlayer {
    private static final String TAG = "CallSoundPlayer";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    static final class CallingBeep {
        private static final int BEEP_DURATION_MILLIS = 160;
        private static final long BEEP_CADENCE_MILLIS = 1_800L;

        private ToneGenerator generator;
        private boolean playing;
        private final Runnable beepPulse = new Runnable() {
            @Override
            public void run() {
                if (!playing || generator == null) {
                    return;
                }
                try {
                    generator.startTone(
                            ToneGenerator.TONE_PROP_BEEP,
                            BEEP_DURATION_MILLIS
                    );
                    MAIN_HANDLER.postDelayed(this, BEEP_CADENCE_MILLIS);
                } catch (RuntimeException error) {
                    Log.e(TAG, "Could not continue outgoing calling beep", error);
                    stopOnMainThread();
                }
            }
        };

        void start() {
            runOnMainThread(this::startOnMainThread);
        }

        void stop() {
            runOnMainThread(this::stopOnMainThread);
        }

        private void startOnMainThread() {
            if (playing) {
                return;
            }
            try {
                generator = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
                playing = true;
                beepPulse.run();
            } catch (RuntimeException error) {
                Log.e(TAG, "Could not start outgoing calling beep", error);
                stopOnMainThread();
            }
        }

        private void stopOnMainThread() {
            playing = false;
            MAIN_HANDLER.removeCallbacks(beepPulse);
            if (generator == null) {
                return;
            }
            try {
                generator.stopTone();
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not stop outgoing calling beep", error);
            }
            generator.release();
            generator = null;
        }
    }

    static final class OutgoingRingback {
        private static final int RING_DURATION_MILLIS = 1_200;
        private static final long RING_CADENCE_MILLIS = 3_000L;

        private ToneGenerator generator;
        private boolean playing;
        private final Runnable ringPulse = new Runnable() {
            @Override
            public void run() {
                if (!playing || generator == null) {
                    return;
                }
                try {
                    generator.startTone(
                            ToneGenerator.TONE_SUP_RINGTONE,
                            RING_DURATION_MILLIS
                    );
                    MAIN_HANDLER.postDelayed(this, RING_CADENCE_MILLIS);
                } catch (RuntimeException error) {
                    Log.e(TAG, "Could not continue outgoing ringback", error);
                    stopOnMainThread();
                }
            }
        };

        void start() {
            runOnMainThread(this::startOnMainThread);
        }

        void stop() {
            runOnMainThread(this::stopOnMainThread);
        }

        private void startOnMainThread() {
            if (playing) {
                return;
            }
            try {
                generator = new ToneGenerator(AudioManager.STREAM_MUSIC, 72);
                playing = true;
                ringPulse.run();
            } catch (RuntimeException error) {
                Log.e(TAG, "Could not start outgoing ringback", error);
                stopOnMainThread();
            }
        }

        private void stopOnMainThread() {
            playing = false;
            MAIN_HANDLER.removeCallbacks(ringPulse);
            if (generator == null) {
                return;
            }
            try {
                generator.stopTone();
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not stop outgoing ringback tone", error);
            }
            generator.release();
            generator = null;
        }
    }

    static void playConnected() {
        playTone(ToneGenerator.TONE_PROP_ACK, 180);
    }

    static void playEnded() {
        playTone(ToneGenerator.TONE_PROP_NACK, 260);
    }

    private static void playTone(int tone, int durationMillis) {
        final ToneGenerator generator;
        try {
            generator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            generator.startTone(tone, durationMillis);
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not play call sound effect", error);
            return;
        }
        MAIN_HANDLER.postDelayed(generator::release, durationMillis + 80L);
    }

    private static void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN_HANDLER.post(runnable);
        }
    }
}
