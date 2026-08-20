# EZ Call Production Roadmap

Recorded on 2026-07-30. Keep the numbering stable so Android and iOS work can
refer to the same improvement.

## Reliability

1. **TURN support** - Pending
   - Add an authenticated coturn service with temporary credentials.
   - Support UDP plus TCP/TLS fallback for restrictive networks.

2. **Automatic WebRTC reconnection** - Implemented on Android
   - Show a Reconnecting state after network interruption.
   - Attempt ICE restart during Wi-Fi/mobile handoff.
   - End the call only after a defined recovery timeout.
   - Two-device network-handoff validation remains required, and TURN is still
     needed for reliable recovery on restrictive networks.

3. **Android Core-Telecom integration** - Implemented on Android
   - Calls use Android's native lifecycle and ongoing call-style notification.
   - Lock-screen and remote-device answer/end, Bluetooth/audio-route ownership,
     system mute, inactive/hold, and competing-call primitives are integrated.
   - Two-device Bluetooth and call-waiting validation remains required.

4. **Server-controlled call state** - Implemented
   - Authenticated callable functions enforce participant roles and valid,
     idempotent transitions inside Firestore transactions.
   - Server timestamps, monotonic revisions, actor fields, and automatic
     unanswered-call expiry are stored with every invite.
   - Firestore rules reject direct invite writes from stale clients.
   - Two-device simultaneous Answer, Decline, Timeout, and End validation
     remains required.

5. **Call quality and delivery diagnostics** - Pending
   - Record FCM delay, answer time, ICE setup time, selected candidate type,
     packet loss, bitrate, latency, and disconnect reason.
   - Provide a per-call diagnostic timeline keyed by `callId`.

## Operations And Security

6. **Crash and ANR reporting** - Pending
   - Add Firebase Crashlytics.
   - Attach call state, network type, and ICE state without storing private
     media or sensitive user data.

7. **Security hardening** - Pending
   - Add Firebase App Check with Play Integrity.
   - Tighten server-side call authorization and Firestore transition rules.
   - Use expiring TURN credentials and add account deletion/privacy controls.

## User Experience

8. **Additional call controls** - Pending
   - Add microphone mute and explicit speaker, earpiece, Bluetooth, and hearing
     aid selection.
   - Add connection-quality feedback and retry for recoverable failures.

9. **Accessibility mode** - Pending
   - Support larger controls, TalkBack, high contrast, reduced motion, and
     caregiver-assisted setup.
   - Optionally confirm destructive call actions.

## Release

10. **Production release pipeline** - Pending
    - Add protected release signing, Play internal testing, CI builds, Firebase
      emulator tests, staged rollouts, a privacy policy, and Data Safety
      declarations.

## Recommended Order

Start with **1. TURN support**, then **2. automatic WebRTC reconnection**.
Complete **4. server-controlled call state** and **5. diagnostics** before a
public production rollout.
