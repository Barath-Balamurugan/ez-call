# EZ Call One-to-One

Independent Android application for one-to-one video calls. This project does
not use the doctor group-call workflow, Pi signaling backend, or Janus.

## Application identity

```text
App name: EZ Call
Package ID: com.ezcall.onetoone
Version: 1.84 (85)
```

This application must use its own Firebase project and Firestore database. Do
not copy `google-services.json`, `.firebaserc`, service-account files, or users
from the group-call application.

Firebase project ID: `ez-call-5467b`

Firebase Android app ID:
`1:532440329624:android:f220892be06aabf0c1cfed`

The ten planned production improvements are tracked in
[`ROADMAP.md`](ROADMAP.md).

## Features

- Firebase Email/Password sign-up and login.
- Server-authoritative call invite creation and state transitions with
  authenticated callers, role checks, immutable terminal states, idempotent
  retries, state revisions, and automatic unanswered-call expiry.
- Android Core-Telecom integration for native call lifecycle, lock-screen and
  remote-device controls, Bluetooth routing, system mute, and inactive/hold.
- Native ongoing call-style notification with a system End call action.
- Automatically retries the same camera once when Android opens the capture
  session but no initial video frame arrives.
- Active calls automatically enter native Picture-in-Picture when the user
  leaves EZ Call, including gesture-navigation exits on Android 12 and newer.
- Outgoing calls distinguish Calling from Ringing: Ringing starts only after
  the receiving Android device posts the incoming-call notification and
  acknowledges delivery in Firestore.
- The initial Calling state plays a short, soft connecting beep every 1.8
  seconds. It stops and hands off to the normal ringback cadence when the
  receiver acknowledges notification delivery.
- Incoming-call push timing is logged separately for the Cloud Function and
  Android FCM delivery so delayed alerts can be diagnosed precisely.
- Incoming calls use FCM as the single presentation path and a fresh
  ringtone-enabled notification channel. The actively ringing notification is
  not replaced by a delayed profile-photo lookup; an immediately available
  photo is included in the first notification instead.
- Incoming call and connected-call screens can remain visible above the Android
  lock screen, allowing calls to be answered without unlocking the phone.
- Incoming/outgoing waiting screens keep the other user's name visible below
  the profile image, and connected calls show that name in the top status pill.
- The connected-call status dot and contact name are centered together inside
  the pill while long names remain safely truncated.
- On Android 14 and newer, EZ Call explains and links directly to the required
  Full screen alerts access; its current state is also visible in Settings.
- Adaptive dark navy EZ Call launcher logo with rounded system-managed corners,
  the glowing purple camera-aperture mark, and coral recording indicator.
- Google sign-in through Android Credential Manager.
- First-time Google profile completion for phone number and optional photo.
- Consistent orange, off-white, and charcoal authentication and editable profile screens with
  outlined fields, password visibility controls, and account actions.
- User profiles with names, phone numbers, and profile photos.
- Dedicated Settings screen for Security Mode, priority contacts, and language
  preference; Profile remains a separate destination.
- Account-scoped Security Mode accepts incoming calls only when the caller's
  number is saved in the receiver's phone contacts. With the mode off,
  registered callers are allowed even when the receiver has not saved them.
- Registered users shown only when their phone number is also in the signed-in
  user's Android contacts.
- Registered contacts are presented in a two-column portrait photo grid.
- The app opens on People. That tab groups registered phone contacts into
  alphabetized Priority contacts, recently called contacts ordered newest
  first, and the remaining Available people alphabetically without duplicates.
- The Calls tab contains only incoming and outgoing call events newest first;
  priority contacts are no longer pinned in the event log.
- Contacts and call states use the dark navy visual system with circular
  profile imagery and purple, pink, cyan, and green action accents.
- Tapping a contact photo starts a one-to-one video call.
- Foreground incoming-call Accept/Decline flow.
- Looping device ringtone for incoming calls plus connected/end sound effects.
- System Back backgrounds an active call instead of ending it.
- Connected calls enter native Android Picture-in-Picture when Home or Back is
  pressed; PiP keeps the remote video and audio active while hiding the
  full-screen controls and local preview until the call is expanded.
- High-priority call notification with Answer/Decline actions when FCM delivery
  is configured; Answer enters the call immediately, and caller photos are
  center-cropped into circular notification icons.
- Every incoming `callId` receives a stable, call-specific notification ID.
  This lets new calls ring normally while duplicate delivery and profile-photo
  refreshes for the same call remain quiet.
- FCM is the single incoming-call presentation path in foreground and
  background states; the removed foreground Firestore launcher can no longer
  open a silent call screen before the notification arrives.
- While another WebRTC call is connected, an incoming call stays in a
  call-waiting notification with `Decline` and `End & accept`; accepting ends
  the current call for both participants before starting the waiting call.
- Synchronized answered, declined, delivery-failed, and two-minute no-answer
  call results for both devices.
- Cancelling an outgoing call before it is answered marks the invite missed,
  stops the receiver's insistent ringtone through a high-priority status push,
  and replaces the incoming-call notification with a silent missed-call
  notification. A late incoming push cannot restart a call that is already
  terminal.
- Native WebRTC camera, microphone, camera switching, and synchronized hang-up.
- Incoming, outgoing, and connected screens use distinct state pills and
  accessible circular controls without changing the signaling protocol.
- Local video automatically pauses while the phone is locked or its screen is
  off, without ending call audio.
- Firestore offer/answer and ICE signaling.
- Ongoing-call foreground service for background audio continuity.

## Firebase setup

1. Use the dedicated Firebase project `ez-call-5467b`.
2. Use the registered Android application with package ID:

   ```text
   com.ezcall.onetoone
   ```

3. Download that application's `google-services.json` to:

   ```text
   app/google-services.json
   ```

4. Enable Authentication -> Email/Password, Google, and Phone.
5. In Authentication settings, configure the SMS region policy to allow every
   country where EZ Call registrations are supported. Test phone numbers and
   fixed verification codes can be configured under the Phone provider for
   development without sending real SMS messages.
6. Add the debug and production SHA-1 fingerprints to the Android app in
   Firebase Project settings.
7. Download `google-services.json` again after enabling Google so it includes
   the Web OAuth client, then replace `app/google-services.json`.
8. Create a Firestore database in Standard edition.
9. This directory is connected to `ez-call-5467b` through `.firebaserc`.
   To reconnect it manually, run:

   ```bash
   firebase use --add
   ```

10. Deploy this project's rules:

   ```bash
   firebase deploy --only firestore:rules
   ```

11. On Blaze, deploy the incoming-call notification function:

   ```bash
   firebase deploy --only functions
   ```

The deployed rules require Firebase Authentication. Signed-in users can read
calling profiles for local address-book matching but can write only their own
profile. New profile creation also requires the profile phone number to match
the SMS-verified phone number linked to the Firebase account. FCM tokens are
private to `userDevices/{uid}`, and call invites plus
WebRTC signaling are available only to the caller and callee UIDs recorded on
the invite.

## Build

```bash
./gradlew lintDebug assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Without `app/google-services.json`, the project still builds, but registration,
contacts, and real call signaling remain unavailable.

Google sign-in also requires a Web OAuth client in `google-services.json` and a
SHA-1 matching the certificate used to sign the APK. Run `./gradlew signingReport`
to inspect the debug certificate. Add the Play App Signing SHA-1 before a Play
Store release.

Local debug builds use the standard `~/.android/debug.keystore`. The expected
debug SHA-1 is `CB:6E:28:ED:90:8F:99:F2:F2:8A:1A:14:48:C9:32:8F:8E:6F:2C:7D`.
Production releases must use a separate protected release key and register its
SHA-1 with Firebase.

Background and closed-app call alerts require the deployed
`sendIncomingCallInvite` and `sendCallStatusUpdate` functions. The first sends
a high-priority, data-only FCM message with a two-minute lifetime. The second
stops remote ringing and reports a missed call when an unanswered caller
cancels. Profile photos stay in Firestore and are not embedded in the push
payload.

## Contacts access

The app requests Android `READ_CONTACTS` permission. The main screen intersects
phone numbers from the device address book with registered profiles in the
Firestore `users` collection. It does not upload the device address book. If
permission is denied, no registered users are displayed or callable from the
main screen.

For production distribution, disclose contacts access in the privacy policy
and complete the applicable Google Play Data safety declarations.

## Optional background ringing

The `functions/` directory contains the one-to-one FCM invite function. Firebase
Cloud Functions deployment requires the Blaze plan. Spark testing continues to
support incoming calls and ringtone playback while the receiving app is open.
True background/closed-app notifications require deploying that function or
using another trusted server to send the high-priority FCM data message. Users
must also allow Notifications on Android 13 and newer.
# EZ Call

> Current Android build: `1.63 (64)`.
