# Change Log

## 1.93 - 2026-08-22

- Fixed Continue with Google for Play-distributed builds by using Credential
  Manager's explicit Google button flow.
- The account picker can now include accounts that require reauthentication or
  have not previously authorized EZ Call.

## 1.91 - 2026-08-19

- Removed the visible Back control from the Email sign-in and registration
  header.
- Android system back navigation still returns to the authentication provider
  selection screen.

## 1.90 - 2026-08-19

- Added a dedicated unauthenticated entry screen with the EZ Call name, current
  app logo, and three provider choices on a fixed dark navy background.
- Continue with Google starts the existing Google/Firebase authentication flow;
  Continue with email opens the existing sign-in and registration forms.
- Continue with Apple is visible but disabled until Apple authentication is
  implemented.
- Preserved SMS phone verification, first-time profile completion, password
  recovery, and existing Firebase profile storage.

## 1.89 - 2026-08-19

- Replaced the legacy violet UI accent with the EZ Call icon's green palette:
  vivid `#33FF64`, mid `#62FE8A`, and highlight `#ADFB9C`.
- Applied the brand palette consistently to existing and new installations,
  including authentication, navigation, profile, settings, and call surfaces.
- Kept semantic Accept, Decline, End, and status colors unchanged and bumped
  Android to 1.89 (90).

## 1.88 - 2026-08-16

- Removed the password-reset action from the Profile screen.
- Password recovery remains available from Forgot Password on the sign-in
  screen.
- Removed the unused Profile-only authentication-provider policy helper and
  bumped Android to 1.88 (89).

## 1.87 - 2026-08-14

- Restored the pre-Telecom outgoing Calling and Ringing audio path so local
  tones remain on the normal media route and retain their previous volume.
- Register outgoing calls with Android Telecom only after the recipient
  answers, while preserving Telecom audio routing, Bluetooth, background, and
  external controls for the connected call.
- Incoming lock-screen call registration and Firebase/WebRTC signaling remain
  unchanged.
- Bumped Android to 1.87 (88).

## 1.86 - 2026-08-14

- Updated outgoing, incoming, connected, camera-off, reconnecting, and call
  error surfaces to resolve legacy violet tokens through the app's current
  accent palette.
- Preserved semantic green Accept, red Decline/End, and the dark video-call
  surface.
- Bumped Android to 1.86 (87).

## 1.85 - 2026-08-13

- Removed the ongoing-call foreground-service notification while an outgoing
  call is still calling or ringing.
- Start the foreground service only after the other person accepts and WebRTC
  begins, preserving active-call background camera and microphone behavior.
- Bumped Android to 1.85 (86).

## 1.84 - 2026-08-13

- Removed the Android 12+ Nearby Devices permission and startup prompt.
- Continue routing calls automatically to already-connected Bluetooth, BLE,
  hearing-aid, wired, and USB communication devices through `AudioManager`.
- Retained legacy Bluetooth manifest compatibility through Android 11 without
  adding Bluetooth discovery, pairing, or direct-device access.
- Bumped Android to 1.84 (85).

## 1.83 - 2026-08-13

- Moved call invite creation and all participant status transitions behind
  authenticated callable Cloud Functions.
- Added a transactional, role-aware state machine with immutable terminal
  states and idempotent retries so stale clients cannot overwrite newer call
  results.
- Added server timestamps, monotonic state revisions, transition actor fields,
  and automatic expiry of unanswered calls after two minutes.
- Denied direct client writes to `callInvites` in Firestore rules while
  preserving participant reads and existing WebRTC signaling access.
- Wait for authoritative Answer and Timeout results before changing local call
  state, including correct pre-answer notification cancellation behavior.
- Added backend state-machine tests and bumped Android to 1.83 (84).

## 1.82 - 2026-08-13

- Integrated one-to-one video calls with Android Core-Telecom while retaining
  the existing Firebase signaling and peer-to-peer WebRTC media.
- Register incoming and outgoing calls with the Android system so lock-screen,
  Bluetooth headset, Android Auto, and wearable answer/end controls reach the
  active EZ Call session.
- Added Telecom-aware answer, active, inactive/hold, mute, reject, missed,
  remote-end, error, and local-end state synchronization.
- Hand managed-call audio routing to Telecom; the existing `AudioManager`
  router remains only as a fallback when Telecom cannot register a call.
- Pause both incoming and outgoing WebRTC audio while Telecom makes a call
  inactive, and apply system mute only to the local microphone track.
- Use an ongoing `Notification.CallStyle` notification with a working End call
  action, and request Bluetooth call access during normal app startup.
- Added disconnect-policy regression tests.
- Bumped Android to 1.82 (83).

## 1.81 - 2026-08-12

- Added automatic WebRTC recovery for temporary connection loss and active
  Wi-Fi/mobile-network changes.
- Versioned offers, answers, and ICE candidates so Firebase signaling can
  safely carry repeated ICE-restart negotiations within the same call.
- The caller owns restart offers; a callee affected by a network change sends
  an authenticated restart request through the existing call document to
  avoid simultaneous-offer collisions.
- Connected calls show `Reconnecting...`, return to the contact name after
  recovery, and end for both participants after a 25-second recovery timeout.
- Added regression tests for ICE-restart generation handling.
- Bumped Android to 1.81 (82).

## 1.80 - 2026-08-12

- Hide Profile's password-reset action for Google-only accounts because those
  users authenticate with Google and do not have an EZ Call password.
- Continue showing password reset for email/password accounts and accounts
  that have both Google and password providers linked.
- Added regression coverage for provider-specific password-reset eligibility.
- Bumped Android to 1.80 (81).

## 1.79 - 2026-08-12

- Removed the Accent color controls from Settings while retaining the current
  application styling and fixed launcher icon.
- Replaced the permanently expanded priority-contact checklist with one
  compact Settings row that shows the selected count.
- Tapping the row opens a multi-select popup of registered EZ Call users found
  in the device contacts; Save commits the choices and Cancel leaves them
  unchanged.
- Bumped Android to 1.79 (80).

## 1.78 - 2026-08-12

- Gave the six-digit SMS verification field an explicit themed surface,
  border, text color, and hint color so OTP digits remain readable in both
  light and dark appearance modes.
- Made Profile sign-out return to the sign-in screen immediately instead of
  waiting for asynchronous Google credential-state cleanup.
- After a password-reset email is sent from Profile, sign out the active
  Firebase session, open the sign-in form, prefill the account email, and show
  instructions to set the new password before signing in again.
- Bumped Android to 1.78 (79).

## 1.77 - 2026-08-09

- Updated the fixed EZ Call launcher artwork to the selected brighter
  neon-lime treatment.
- Kept three logo blades green and one blade white while preserving the glass
  highlights, pink status dot, geometry, and adaptive-icon padding.
- Bumped Android to 1.77 (78).

## 1.76 - 2026-08-09

- Fixed the password eye control incorrectly treating Android's hidden-password
  input variation as already visible.
- Use the input-type variation mask to switch reliably between concealed and
  visible password text on both Password and Confirm password fields.
- Added regression coverage for hidden and visible password input states.
- Bumped Android to 1.76 (77).

## 1.75 - 2026-08-09

- Reverted the compact People-card detail spacing introduced in 1.74.
- Restored the previous name height, recent-call spacing, flexible spacer, Call
  button placement, and overall card height from 1.73.
- Kept the large edge-to-edge top profile photo unchanged.
- Bumped Android to 1.75 (76).

## 1.74 - 2026-08-09

- Tightened the lower section of People cards by removing the expanding spacer
  between recent-call information and the Call button.
- Reduced name-block height, status spacing, and vertical content padding while
  retaining support for two-line contact names.
- Reduced overall card height without changing the large edge-to-edge profile
  photo area.
- Bumped Android to 1.74 (75).

## 1.73 - 2026-08-09

- Fixed Android Studio and ADB launch failures caused by a previously disabled
  `LauncherViolet` activity alias remaining on installed devices.
- Removed all obsolete accent-specific launcher aliases and made
  `MainActivity` the app's single permanent MAIN/LAUNCHER entry point.
- Kept the fixed bright-green launcher icon and preserved all in-app accent
  choices without modifying package-manager component state.
- Bumped Android to 1.73 (74).

## 1.72 - 2026-08-09

- Replaced the circular avatar on every People card with a large edge-to-edge
  profile photo filling the card's top section.
- Use center-crop framing and the card's outer rounded corners so faces remain
  prominent without rendering the image as a circle.
- Moved names, recent-call status, and the compact Call action into a dedicated
  padded lower section to prevent photo and text overlap.
- Kept the default user artwork as the full top-section fallback when no photo
  was provided.
- Bumped Android to 1.72 (73).

## 1.71 - 2026-08-08

- Recolored the EZ Call launcher logo to a fixed brighter green while
  preserving its glass finish, white blade, pink status dot, and adaptive-icon
  padding.
- Decoupled the launcher icon from the selectable in-app accent color. Accent
  changes now update the interface only and never replace or restart the
  launcher entry.
- Kept legacy launcher aliases for installed-app upgrade compatibility, but
  made every alias use the same fixed bright-green icon.
- Bumped Android to 1.71 (72).

## 1.70 - 2026-08-08

- Require Firebase SMS verification for the phone number entered during both
  email/password registration and first-time Google profile completion.
- Added a six-digit verification dialog with resend, cancellation, automatic
  Android verification support, expired-code feedback, rate-limit feedback,
  and an explicit already-registered-number message.
- Link the verified phone credential to Firebase Auth before creating the
  Firestore calling profile, and clean up incomplete email registrations when
  phone linking fails.
- Hardened new Firestore profile creation so the stored phone number must match
  the Firebase Auth token's verified phone number.
- Bumped Android to 1.70 (71).

## 1.69 - 2026-08-08

- Fixed the app task closing when an accent selection changed the active
  launcher alias on Samsung devices.
- Apply the in-app accent immediately, but defer the launcher-icon alias switch
  until all EZ Call activities have remained in the background for two seconds.
- Reconcile a pending icon safely after future app launches without disabling
  the launcher component that opened the active task.
- Bumped Android to 1.69 (70).

## 1.68 - 2026-08-08

- Added five adaptive launcher-icon variants matching the selectable Violet,
  Ocean, Sky, Teal, and Emerald accent themes.
- Change the launcher and system splash icon when the accent selection changes,
  while preserving the logo's glass shading and pink status dot.
- Reconcile the selected launcher alias when the application starts and added
  launcher-alias mapping coverage.
- Bumped Android to 1.68 (69).

## 1.67 - 2026-08-08

- Added an Accent color selector to Settings with five video-call-friendly
  choices: Violet, Ocean, Sky, Teal, and Emerald.
- Persist the selected accent on the device and apply it across navigation,
  buttons, form highlights, profile accents, and call-screen highlights.
- Preserve semantic red, green, and status colors for decline, accept, end-call,
  and availability states.
- Added accent-palette unit coverage and bumped Android to 1.67 (68).

## 1.66 - 2026-08-07

- Made the password eye icon respond across its full trailing touch target and
  preserve the cursor while switching between hidden and visible text.
- Added a dedicated password-reset dialog that displays and validates the
  account email before requesting the Firebase reset link.
- Detect phone numbers already owned by another Firebase profile and display
  `Already registered phone number.` during registration and profile editing.
- Remove a newly created email-auth account when registration cannot finish
  because its phone number is already registered, allowing a clean retry.
- Added password-toggle hit-target and phone-ownership unit coverage and bumped
  Android to 1.66 (67).

## 1.65 - 2026-07-31

- Prefer the name saved in this device's contacts when displaying a registered
  EZ Call user.
- Apply local contact names to People cards, search and sorting, call logs,
  priority-contact settings, outgoing call screens, and incoming or missed-call
  notifications.
- Keep the user's EZ Call profile name as the fallback when no matching local
  contact name is available.
- Added exact-number, local-number, and fallback unit coverage and bumped
  Android to 1.65 (66).

## 1.64 - 2026-07-31

- Added a persisted Appearance setting with Light and Dark options.
- Applied the light palette to People, Calls, Settings, Profile, authentication,
  phone setup, and country-code selection while preserving EZ Call accents.
- Main screens refresh automatically after the appearance changes.
- Kept the connected video-call surface dark for clear video and control
  contrast.
- Added palette unit coverage and bumped Android to 1.64 (65).

## 1.63 - 2026-07-30

- Add a soft 160 ms connecting beep every 1.8 seconds while an outgoing call
  remains in the `Calling` state.
- Stop the connecting beep and hand off to the existing ringback sound only
  when the receiver acknowledges notification delivery and the state changes
  to `Ringing`.
- Stop both outgoing tones on answer, cancellation, failure, activity
  destruction, and call replacement.
- Bumped Android to `1.63 (64)`.

## 1.62 - 2026-07-30

- Mark an outgoing call `missed` when its caller cancels before the receiver
  answers instead of recording the unanswered call as normally ended.
- Add the `sendCallStatusUpdate` Firestore function to send the receiver a
  high-priority terminal-state message when an unanswered call is cancelled.
- Stop and dismiss the receiver's insistent incoming-call notification, then
  replace it with a silent missed-call notification containing the caller's
  name and available profile photo.
- Recheck the Firestore invite before presenting an incoming FCM push so a
  delayed push cannot restart ringing after the call became terminal.
- Preserve compatibility with older callers by translating a pre-answer
  `ended` transition into a receiver-side missed-call update.
- Added focused Android and Firebase Functions transition tests and bumped
  Android to `1.62 (63)`.

## 1.61 - 2026-07-30

- Moved incoming calls to a fresh `incoming_video_calls_v3` notification
  channel so devices that retained a silent setting for the previous channel
  receive ringtone audio again.
- Stopped replacing an actively ringing notification during the asynchronous
  caller-photo lookup, which could interrupt the insistent ringtone. An
  immediately available Firestore photo is included in the first notification
  instead.
- Preserved FCM signaling, accept/decline actions, call waiting, and WebRTC
  behavior.
- Bumped Android to `1.61 (62)`.

## 1.60 - 2026-07-30

- Removed the competing foreground Firestore listener that could open an
  incoming screen before FCM arrived and then cause the notification push to be
  ignored after the call connected.
- Make FCM the single incoming-call presentation and ringtone path in both
  foreground and background app states.
- Assign a stable notification ID derived from each `callId` instead of
  repeatedly cancelling and reposting global notification `1001`.
- Carry the call-specific ID through Accept, Decline, End & accept, caller-photo
  refresh, and call-screen cancellation.
- Added notification-ID tests and bumped Android to `1.60 (61)`.

## 1.59 - 2026-07-29

- Track the WebRTC call currently connected in the Android process.
- Show waiting incoming calls as notifications with `Decline` and
  `End & accept` instead of opening a second full-screen call UI.
- `End & accept` marks the existing Firestore call ended, clears its foreground
  call session, replaces the current activity task, and automatically accepts
  the waiting call.
- Ignore duplicate incoming pushes for the call that is already connected.
- Added focused active-call tracker tests and bumped Android to `1.59 (60)`.

## 1.58 - 2026-07-29

- Track the `callId` associated with the current incoming-call notification.
- Clear a stale notification before posting a different incoming call so
  Android treats it as a new alert and does not suppress the ringtone because
  the notification ID and `onlyAlertOnce` flag were reused.
- Keep duplicate FCM delivery and caller-photo refreshes quiet for the same
  call.
- Bumped Android to `1.58 (59)`.

## 1.57 - 2026-07-29

- Center-crop and mask incoming caller photos into anti-aliased circles before
  passing them to Android's call-style or legacy notification layouts.
- Preserve transparent corners so manufacturer notification styles cannot show
  the original square profile image.
- Bumped Android to `1.57 (58)`.

## 1.56 - 2026-07-29

- Outgoing calls now begin in `Calling`, without playing ringback audio.
- The receiving Android app transactionally acknowledges `delivered` only
  after it receives the FCM message and posts the incoming-call notification.
- Caller UI and ringback audio change to `Ringing` only after that device
  delivery acknowledgment reaches Firestore.
- Prevent a delayed delivery acknowledgment from overwriting answered,
  declined, missed, ended, or notification-failure states.
- Added focused call-status transition tests and bumped Android to `1.56 (57)`.

## 1.55 - 2026-07-28

- Enable Android's native automatic Picture-in-Picture transition for active
  connected calls on Android 12 and newer.
- Keep the existing manual Home/Back PiP fallback for older Android versions.
- Disable automatic PiP immediately when a call is ending so the completed
  call screen cannot reappear as a floating window.
- Bumped Android to `1.55 (56)`.

## 1.54 - 2026-07-28

- Added a first-frame camera startup watchdog. If the initial camera session
  opens but produces no frame, Android restarts the same camera once instead of
  requiring the user to press Flip.
- Recheck the display state while an incoming call wakes the lock screen so the
  intended camera state resumes after Android finishes the wake transition.
- Parallelized the incoming-call Function's user/device lookups and added
  server and Android timing logs that separate trigger, FCM, and total
  notification latency.
- Kept FCM delivery at high priority and did not enable a billable always-warm
  Function instance.
- Bumped Android to `1.54 (55)`.

## 1.53 - 2026-07-27

- Centered the connected-call status dot and contact name as one visual group
  inside the top pill.
- Retained single-line ellipsis for long contact names without forcing the text
  into a left-aligned weighted layout.
- Bumped Android to `1.53 (54)`.

## 1.52 - 2026-07-27

- Added native Android Picture-in-Picture for connected one-to-one video calls.
- Home or Back now moves an active connected call into PiP instead of ending
  it, with ordinary backgrounding retained as a fallback when PiP is
  unavailable.
- PiP keeps remote video, remote-camera-off identity, audio, signaling, and the
  foreground call service active.
- Hide the local preview, contact pill, and call controls inside the compact
  PiP window, then restore the full call layout when expanded.
- Bumped Android to `1.52 (53)`.

## 1.51 - 2026-07-27

- Constrained the waiting-call profile animation to a true square so it no
  longer consumes the name and call-status area beneath it.
- Keep the other user's name visible on both incoming and outgoing waiting
  screens.
- Replaced the connected-call `Live` label with the other user's name and
  safely truncate unusually long names inside the wider top pill.
- Bumped Android to `1.51 (52)`.

## 1.50 - 2026-07-27

- Added the Android 14+ Full screen alerts access flow required for an incoming
  EZ Call to appear above the lock screen.
- Show a one-time explanation after notification permission is available and
  open the exact system settings page where the user can allow this access.
- Added a Lock-screen calls row in EZ Call Settings with a live Allowed/Allow
  status, so access can be checked or restored later.
- Kept the normal device keyguard intact outside the incoming-call screen.
- Bumped Android to `1.50 (51)`.

## 1.49 - 2026-07-27

- Show the incoming and connected video-call activity above the Android lock
  screen and turn on the display for incoming calls.
- Allow notification Accept to open the connected call without requiring the
  user to unlock the phone first.
- Continue suspending the camera while the display is off, then restore the
  intended camera state when the visible call screen turns the display on.
- Preserve the device lock outside the call activity; the app does not dismiss
  or disable the system keyguard.
- Bumped Android to `1.49 (50)`.

## 1.48 - 2026-07-27

- Increased the home bottom-navigation glass opacity from 40% black to 75%
  dark navy.
- Retained the subtle transparency, hairline border, and existing selected-tab
  colors while improving separation from scrolling content.
- Bumped Android to `1.48 (49)`.

## 1.47 - 2026-07-27

- Split the adaptive icon into a solid dark navy background and a transparent
  camera-aperture foreground.
- Reduced the foreground mark to the adaptive safe area and optically centered
  it so the camera extension no longer makes the logo appear oversized or
  offset in the launcher and Android splash screen.
- Bumped Android to `1.47 (48)`.

## 1.46 - 2026-07-27

- Converted the EZ Call launcher artwork from a raw square drawable into an
  Android adaptive icon.
- Let each supported launcher apply its native rounded icon mask while retaining
  the dark navy background and supplied glowing logo.
- Bumped Android to `1.46 (47)`.

## 1.45 - 2026-07-27

- Replaced the orange video-camera launcher icon with the supplied EZ Call
  branding: a glowing purple/white camera-aperture mark on dark navy with a
  coral recording indicator.
- Use the text-free square artwork for both standard and round launcher icons
  so the brand remains legible under Android launcher masks.
- Bumped Android to `1.45 (46)`.

## 1.44 - 2026-07-27

- Changed the initial home destination from Calls to People.
- Reorganized People into Priority contacts, Recent calls, and Available
  people sections, keeping each contact in only its highest applicable section.
- Keep priority contacts alphabetical, order non-priority recent contacts by
  their latest call, and keep remaining available contacts alphabetical.
- Removed the pinned priority-contact section from Calls so that tab contains
  only the complete reverse-chronological call-event log.
- Bumped Android to `1.44 (45)`.

## 1.43 - 2026-07-27

- Added an always-pinned Priority contacts section at the top of the Calls tab.
- Show every selected priority contact alphabetically with their profile image
  and a direct video-call action, including contacts with no prior calls.
- Keep the complete reverse-chronological Recent calls list below the pinned
  section, including repeated events for priority contacts.
- Bumped Android to `1.43 (44)`.

## 1.42 - 2026-07-27

- Corrected Security Mode to use the receiver's Android phone contacts rather
  than the separate priority-contact list.
- With Security Mode on, a caller is accepted only when their normalized phone
  number matches a contact saved on the receiving device; an unknown caller is
  declined before call UI or ringtone is shown.
- With Security Mode off, incoming calls remain allowed even when the receiver
  has not saved the caller's number.
- Kept priority contacts as an independent account setting and added focused
  security-policy tests.
- Bumped Android to `1.42 (43)`.

## 1.41 - 2026-07-27

- Replaced the Calls and People bottom-navigation scroll shortcuts with real,
  independently selected views.
- People now shows all registered EZ Call users found in the phone contacts in
  alphabetical order while preserving each person's latest-call indicator.
- Calls now shows every incoming and outgoing call-history event newest first,
  including repeated calls, direction, result, timestamp, photo, and callback
  action.
- Retain caller/callee display details from existing Firestore call invitations
  so older call-log rows remain readable when contact lists change.
- Bumped Android to `1.41 (42)`.

## 1.40 - 2026-07-27

- Changed the bottom Settings destination to open a dedicated Settings screen
  instead of the user's profile.
- Added account-scoped Security Mode. When enabled, only selected priority
  contacts can ring; other incoming calls are declined and the caller receives
  the existing declined status.
- Added priority-contact selection from registered EZ Call users that are also
  present in the device's phone contacts.
- Added a device-wide language preference with System default and English
  choices, including Android per-app locale integration.
- Added security-policy unit coverage and bumped Android to `1.40 (41)`.

## 1.39 - 2026-07-27

- Added a searchable country/region calling-code selector to every phone-number
  field used during signup, Google profile completion, legacy device setup, and
  profile editing.
- Default the selection from the SIM, network, or device locale and accept
  pasted international numbers without duplicating their country code.
- Parse and validate possible numbers with Android-optimized libphonenumber,
  then store a canonical E.164 value such as `+919620500072` in the existing
  Firebase profile fields.
- Keep phone metadata initialization off the UI thread and preserve the dark
  glass authentication/profile styling.
- Bumped Android to `1.39 (40)`.

## 1.38 - 2026-07-27

- Show the signed-in user's profile photo inside the floating local preview
  whenever their camera is manually disabled or suspended by screen lock.
- Use the shared neutral user silhouette when the signed-in user has not saved
  a profile photo.
- Drive the overlay from the same effective camera-transmission state as the
  WebRTC video track, so it disappears immediately when video resumes.
- Bumped Android to `1.38 (39)`.

## 1.37 - 2026-07-27

- Removed the opaque black drawable from the local `SurfaceViewRenderer`; on
  affected devices it could be composited above and hide live camera frames.
- Use a transparent renderer layer with an explicit rounded outline for direct
  clipping, while the parent viewport supplies the seamless black backing.
- Retained the stable 4dp renderer inset, thin purple frame, dragging, and
  tap-to-show controls.
- Bumped Android to `1.37 (38)`.

## 1.36 - 2026-07-27

- Restored the local `SurfaceViewRenderer` viewport's proven 4dp inset after
  the nearly flush 1dp layout caused a black self-preview on some devices.
- Kept the outer preview backing black, so the restored safety inset does not
  reintroduce the previous grey bands.
- Preserved the thin purple outline, rounded clipping, dragging, and tap
  behavior.
- Bumped Android to `1.36 (37)`.

## 1.35 - 2026-07-27

- Removed the exposed grey glass bands around the floating local-camera video.
- Reduced the clipped viewport inset from 4dp to 1dp and changed the underlying
  frame to black, retaining only a thin purple outline.
- Preserved direct rounded clipping, camera rendering, dragging, and tap-to-show
  call controls.
- Bumped Android to `1.35 (36)`.

## 1.34 - 2026-07-27

- Switched incoming calls on Android 12 and newer to the native call-style
  notification with green Accept and red Decline color hints.
- Show the caller's profile photo through the call-style `Person` icon; Android
  8 through 11 receive the same photo as the notification's large icon.
- Keep the push payload small and the ringtone immediate, then securely load
  the existing caller image from `callInvites/{callId}` and refresh only the
  still-active notification without alerting a second time.
- Center-crop and downsample notification photos to avoid oversized Binder
  payloads while leaving the stored profile image unchanged.
- Bumped Android to `1.34 (35)`.

## 1.33 - 2026-07-27

- Changed the connected camera-off audio ring to begin at exactly the profile
  image diameter instead of starting substantially outside it.
- Increased the ring expansion range so incoming speech visibly changes its
  radius from the avatar edge outward.
- Start the diffuse glow near the avatar diameter as well, then expand and
  brighten it more strongly with louder incoming audio.
- Bumped Android to `1.33 (34)`.

## 1.32 - 2026-07-27

- Added repeating breathing animations to the incoming-call Accept and Decline
  buttons so they read clearly as interactive controls.
- Added expanding colored halo pulses; Accept has the stronger emphasis while
  Decline remains visibly active but more restrained.
- Kept Remind Me static and cancel all waiting-screen animators immediately
  when the call state or activity changes.
- Bumped Android to `1.32 (33)`.

## 1.31 - 2026-07-27

- Simplified the connected camera-off visualization from two outlined rings to
  one outlined audio-reactive ring.
- Added a separate diffuse glow layer whose scale and brightness rise and fall
  with incoming WebRTC voice level.
- Kept the existing noise gate and attack/release smoothing; waiting-call
  artwork remains unchanged.
- Bumped Android to `1.31 (32)`.

## 1.30 - 2026-07-27

- Reduced the floating local-preview glass radius from 20dp to 10dp and its
  clipped video viewport radius to 8dp for a squarer, more restrained shape.
- Kept clipping, border containment, dragging, safe-area bounds, and tap
  behavior unchanged.
- Bumped Android to `1.30 (31)`.

## 1.29 - 2026-07-27

- Fixed the floating local-camera surface drawing over its glass frame by
  placing it inside an inset, directly clipped rounded viewport.
- Made the local self-preview draggable during a connected call.
- Distinguish taps from drags: tapping the preview still shows or hides call
  controls, while dragging repositions it.
- Clamp movement to the visible call stage and Android system insets so the
  preview cannot be lost beyond the screen edges.
- Added unit coverage for preview-bound calculations and bumped Android to
  `1.29 (30)`.

## 1.28 - 2026-07-27

- Made the connected-call avatar rings respond to the remote participant's
  incoming WebRTC audio instead of remaining static when their camera is off.
- Poll standard inbound audio-level statistics every 120ms and fall back to
  RMS level calculated from WebRTC audio-energy deltas when needed.
- Added a silence floor plus fast-attack/slow-release smoothing so speech
  expands and brightens the rings without constant background-noise jitter.
- Added unit coverage for direct audio levels, energy fallback, and filtering
  of video/outbound statistics.
- Bumped Android to `1.28 (29)`.

## 1.27 - 2026-07-27

- Added an audible outgoing ringback tone while waiting for the callee to
  answer.
- Use a familiar repeating ring-and-pause cadence on the caller's device only;
  the callee continues to hear the incoming-call notification ringtone.
- Stop and release ringback immediately on answer, decline, timeout, send
  failure, cancellation, call termination, error display, or activity
  destruction.
- Bumped Android to `1.27 (28)`.

## 1.26 - 2026-07-26

- Fixed End, Cancel, Decline, remote hang-up, and call-error dismissal so a
  notification-launched call returns to the EZ Calls home screen instead of
  closing the whole app task.
- Centralized call completion so the final Firestore status is written once,
  call notifications and foreground service are cleared, and stale call
  activity state is not restored from the launcher.
- Added a five-minute local guard for the completed call ID so delayed
  Firestore snapshots or FCM messages cannot reopen a call while its final
  status is propagating.
- Added unit coverage for completed-call suppression and bumped Android to
  `1.26 (27)`.

## 1.25 - 2026-07-26

- Fixed contact names being vertically clipped after the last-call row was
  added.
- Increased the responsive contact-card minimum height, reserved a complete
  two-line name area, and moved remaining flexible space below the last-call
  label.
- Kept the compact Call button, glowing avatar, last-call metadata, and
  two-column grid.
- Bumped Android to `1.25 (26)`.

## 1.24 - 2026-07-26

- Added real last-call information to each eligible contact card using the
  signed-in user's existing Firestore `callInvites` history.
- Show relative values such as `Just now`, `2 min ago`, `Yesterday`, or a
  calendar date; incoming missed calls use the reference design's pink
  `Missed` badge.
- Sort eligible contacts by most recent call, with contacts that have never
  been called shown alphabetically afterward.
- Reduced contact avatar dimensions to the supplied design's proportions while
  preserving the colored glow treatment and making space for call history.
- Added two participant-scoped Firestore listeners for outgoing and incoming
  history. No schema migration, new collection, index, or rules deployment is
  required.
- Bumped Android to `1.24 (25)`.

## 1.23 - 2026-07-26

- Reduced the home search and Call action corner radii to 8dp.
- Restyled login, signup, Google profile completion, and Profile screens with
  the home screen's dark navy background, purple accents, translucent glass
  fields, compact controls, and restrained corner radii.
- Removed the old white/orange header bands and legacy `MEMORY CALLS` branding
  from authentication and profile surfaces.
- Preserved Firebase authentication, Google sign-in, password reset, profile
  photo selection, profile persistence, sign-out, and calling behavior.
- Bumped Android to `1.23 (24)`.

## 1.22 - 2026-07-26

- Added a soft radial accent glow behind every contact profile image, matching
  the supplied design's glowing avatars.
- Apply the glow to both real profile photos and the neutral missing-photo
  fallback while preserving each card's rotating accent color.
- Kept contact data, card actions, search, Firebase, and calling unchanged.
- Bumped Android to `1.22 (23)`.

## 1.21 - 2026-07-26

- Reduced the home search field height, corner radius, icon size, typography,
  and horizontal padding for a slimmer glass treatment.
- Reduced each contact card's Call action height, corner radius, icon, label,
  and spacing so the repeated controls no longer dominate the cards.
- Kept the title, profile control, contact images, filtering, and call behavior
  unchanged.
- Bumped Android to `1.21 (22)`.

## 1.20 - 2026-07-26

- Replaced the home header's two-level `EZ CALL` / `Video Calls` treatment
  with one larger `EZ Calls` title.
- Removed the `Tap a person to call` subtitle.
- Replaced the framed profile photo with a transparent purple profile icon
  that continues to open the Profile screen.
- Added a visible glass search field beneath the title that filters the loaded
  registered contacts by name as the user types.
- Kept contact eligibility, Firebase loading, and call behavior unchanged.
- Bumped Android to `1.20 (21)`.

## 1.19 - 2026-07-26

- Replaced the home header's settings glyph with the signed-in user's actual
  profile photo.
- Use the neutral user silhouette when no profile photo is stored, matching
  profile and contact fallback behavior.
- Added a layered purple glass frame, clearer touch target, and restrained
  `EZ CALL` brand eyebrow to make the home header feel less plain.
- Refresh the header avatar after returning from profile editing.
- Kept profile navigation, contacts, calling, Firebase, and WebRTC behavior
  unchanged.
- Bumped Android to `1.19 (20)`.

## 1.18 - 2026-07-26

- Matched the supplied `Video Call App` design source with translucent
  glass-style contact panels, controls, status pills, and bottom navigation.
- Applied the reference design's low-opacity white/black layers, thin tinted
  borders, blurred purple/cyan ambient light, and exact purple, pink, and green
  action gradients.
- Kept the real registered contacts, real profile photos, full-screen remote
  WebRTC video, floating local preview, and existing call controls instead of
  copying mock contact or call-state data from the reference.
- Kept Firebase, FCM, Firestore call states, native WebRTC signaling, and call
  behavior unchanged.
- Bumped Android to `1.18 (19)`.

## 1.17 - 2026-07-26

- Redesigned the registered-contacts screen with a dark navy visual system,
  prominent `Video Calls` heading, two-column contact panels, circular profile
  photos, colored call actions, and a fixed Calls/People/Settings navigation
  bar.
- Redesigned outgoing and incoming call states with compact status pills,
  concentric profile-photo treatment, clearer caller identity, and large
  circular call actions.
- Added a functional incoming `Remind Me` action that backgrounds the call UI
  while leaving the system call notification available.
- Restyled the connected call while preserving full-screen remote video,
  floating local camera preview, camera-off feedback, and tap-to-reveal Camera,
  End, and Flip controls.
- Kept Firebase, FCM, Firestore call states, native WebRTC signaling, contact
  filtering, and call actions unchanged.
- Bumped Android to `1.17 (18)`.

## 1.16 - 2026-07-26

- Automatically disable the local WebRTC video track when the phone screen
  turns off or the device is locked.
- Publish the existing camera-off state so the other participant sees that
  video is unavailable instead of a stale frame.
- Restore video after unlock only when the user had left the camera enabled;
  manual camera-off preference is preserved.
- Keep call audio, signaling, and the foreground call service active while the
  screen is off.
- Bumped Android to `1.16 (17)`.

## 1.15 - 2026-07-25

- Removed the activity-level incoming ringtone so an open app no longer plays
  audio on top of the FCM notification ringtone.
- Kept the incoming notification active until Accept, Decline, remote End,
  missed-call timeout, or notification timeout.
- Changed system Back during a connected call to move EZ Call into the
  background instead of destroying the call activity.
- Bumped Android to `1.15 (16)`.

## 1.14 - 2026-07-25

- Fixed the caller's Firestore status listener being attached before its
  secured `callInvites/{callId}` document existed.
- Outgoing invite creation now returns an observable task; the caller subscribes
  to `answered`, `declined`, `missed`, and `ended` states only after that
  create succeeds.
- Added explicit caller feedback when an invite cannot be created.
- Bumped Android to `1.14 (15)`.

## 1.13 - 2026-07-25

- Changed registered contacts from one full-width card per row to a responsive
  two-column portrait grid.
- Kept an odd final contact at half width and preserved full-width loading,
  permission, search-empty, and no-contact messages.
- Made each complete photo tile the call target and adjusted the name overlay
  for the narrower card size.
- Corrected the Contacts header brand label to `EZ CALL`.
- Bumped Android to `1.13 (14)`.

## 1.12 - 2026-07-25

- Prevented the FCM delivery function from overwriting fast receiver
  `answered` or `declined` states with `sent`.
- Kept the notification Decline receiver alive until its asynchronous Firestore
  status write finishes, with an eight-second safety timeout.
- Added logging for failed invite-status writes and caller feedback when push
  delivery fails.
- Added a two-minute unanswered-call timeout that marks the invite `missed`,
  tells the caller that the contact did not answer, and closes stale receiver
  call UI.
- Bumped Android to `1.12 (13)`.

## 1.11 - 2026-07-25

- Changed the incoming-call notification's Answer action to accept the invite
  and enter the video call immediately.
- Kept notification-body and full-screen-intent taps on the incoming
  Accept/Decline screen so calls are never answered without an explicit Answer
  action.
- Added duplicate-safe handling when the existing single-top call activity
  receives an Answer action while already visible.
- Bumped Android to `1.11 (12)`.

## 1.10 - 2026-07-24

- Restored Firebase project `ez-call-5467b` after access to its owning Google
  account was recovered.
- Restored Android Firebase app
  `1:532440329624:android:f220892be06aabf0c1cfed` and its Google OAuth
  configuration.
- Restored the original debug signing identity expected by that OAuth client.
- Existing Firebase Authentication users and Firestore data remain in place;
  this change did not migrate, delete, or overwrite database records.
- Deployed the Blaze `sendIncomingCallInvite` 2nd Gen function for background
  call delivery with high-priority FCM, a two-minute expiry, and delivery
  status.
- Removed Base64 profile photos from FCM payloads to prevent oversized messages;
  profile images remain stored in Firestore.
- Bumped Android to `1.10 (11)`.

## 1.9 - 2026-07-24

- Replaced the inaccessible Firebase project `ez-call-5467b` with the new
  dedicated project `ez-call-67296`.
- Registered Android app `com.ezcall.onetoone`, enabled Email/Password and
  Google authentication, created Firestore, and deployed the secured rules.
- Added a project-specific debug signing certificate so the new Firebase
  project has its own Android OAuth identity.
- Existing users and Firestore records were not migrated; users register again
  in the replacement Firebase project.
- Bumped Android to `1.9 (10)`.

## 1.8 - 2026-07-18

- Replaced all bundled stock-person profile fallbacks with a neutral user
  avatar on Authentication, Profile, Contacts, incoming-call, and call screens.
- Removed the four stock profile-photo assets from the Android package.
- Profiles with no chosen photo keep an empty `photoBase64` and no longer write
  a generated `photoKey` to Firestore.
- Bumped Android to `1.8 (9)`.

## 1.7 - 2026-07-18

- Renamed the user-facing application from Memory Calls to EZ Call.
- Changed the Android namespace and application ID from
  `com.memorycalls.onetoone` to `com.ezcall.onetoone`.
- Started a clean Firebase-project migration for the new application identity;
  existing Memory Calls installations and Firebase data remain separate.
- Registered `com.ezcall.onetoone` in the dedicated Firebase project
  `ez-call-5467b` and added the debug SHA-1 and SHA-256 fingerprints.
- Replaced prototype public Firestore access with authenticated profile,
  participant-only call, and owner-only device-token rules.
- Moved FCM tokens out of contact profiles into private `userDevices/{uid}`
  documents and attached caller/callee UIDs to every call invite.
- Bumped Android to `1.7 (8)`.

## 1.6 - 2026-07-12

- Added a looping incoming-call ringtone using the device's configured ringtone.
- Added short connected and call-ended sound effects.
- Upgraded incoming FCM notifications with a ringtone/vibration channel,
  insistent call alert, full-screen intent, Answer/Decline actions, public
  lock-screen visibility, and a two-minute timeout.
- Added notification decline handling that writes `declined` to Firestore.
- Accept incoming invites in either `ringing` or FCM-delivered `sent` state and
  prevent duplicate call activities with `singleTop` launch mode.
- Added a proper monochrome notification icon and vibration permission.
- Bumped Android to `1.6 (7)`.

## 1.5 - 2026-07-11

- Redesigned Contacts with the same off-white header, charcoal content area,
  and orange primary accents used by Authentication and Profile.
- Added a clearer `Your contacts` hierarchy with signed-in identity and Profile
  action in the header.
- Restyled search, helper text, empty/permission states, and contact-card borders
  without changing contact filtering or tap-to-call behavior.
- Bumped Android to `1.5 (6)`.

## 1.4 - 2026-07-11

- Replaced the previous green brand system with the supplied palette:
  off-white `#F4FFF0`, orange `#F95830`, and charcoal `#353535`.
- Authentication and Profile now use off-white headers, charcoal form
  backgrounds, black/off-white typography, and orange primary actions.
- Updated main-screen accents, positive call actions, legacy phone setup, and
  the launcher mark to use the orange primary color.
- Kept red reserved for destructive end-call and sign-out actions.
- Bumped Android to `1.4 (5)`.

## 1.3 - 2026-07-11

- Redesigned sign-up and sign-in with a deep-green branded header, dark
  unframed form, outlined inputs, primary action, Google divider, and clearer
  mode switching.
- Added confirm-password validation, password visibility controls, and a
  working forgot-password action to authentication.
- Redesigned Profile with the same visual system, larger profile photo,
  prominent Save action, read-only login email, account section, and separated
  password-reset and sign-out controls.
- Preserved existing Firebase Auth, profile storage, contacts, and call flows.
- Bumped Android to `1.3 (4)`.

## 1.2 - 2026-07-11

- Added Android contacts permission and address-book phone number loading.
- The main screen now shows only users who are both registered in Firestore and
  present in the signed-in user's phone contacts.
- Added country-code-tolerant matching using exact normalized numbers or the
  same final ten digits.
- Added permission-required and no-matching-contact empty states.
- Refresh device contacts when the app resumes.
- Added unit tests for phone-number matching and bumped Android to `1.2 (3)`.

## 1.1 - 2026-07-11

- Added Google sign-in using Android Credential Manager and Firebase Auth.
- Added first-time profile completion for Google users to provide the phone
  number used by one-to-one calls and optionally choose a profile photo.
- Added a different-account action when profile setup is incomplete.
- Clear Credential Manager state on sign-out so users can select another
  Google account.
- Updated Firebase dependencies and bumped Android version to `1.1 (2)`.

## 1.0 - 2026-07-11

- Created an independent one-to-one Android application.
- Assigned package ID `com.memorycalls.onetoone`.
- Preserved Firebase login, profiles, registered contacts, foreground incoming
  calls, and native one-to-one WebRTC.
- Removed doctor roles, group-call state, callback approvals, Pi API access,
  Janus signaling, group-call screens, and group-only dependencies.
- Reset versioning for the independent application.
# 1.64 - 2026-07-31

- Added a persisted Appearance setting with Light and Dark options.
- Applied the light palette to People, Calls, Settings, Profile, authentication,
  phone setup, and country-code selection while preserving EZ Call accents.
- Main screens refresh automatically after the appearance changes.
- Kept the connected video-call surface dark for clear video and control
  contrast.
- Added palette unit coverage and bumped Android to 1.64 (65).
