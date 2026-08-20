# Android Changes To Mirror On iOS

This file records Android behavior that should be considered when maintaining
the separate iPhone application.

## 2026-08-19 - Use the icon greens as the app accent

- Replaced the legacy violet accent with the EZ Call icon palette: vivid
  `#33FF64`, mid `#62FE8A`, and highlight `#ADFB9C`.
- Applied the palette across authentication, navigation, profile, settings,
  and call surfaces while preserving semantic call-state colors.
- Android app version is 1.89 (90).

### Suggested iOS equivalent

- Use the same three green brand tokens throughout the iOS UI and keep
  Accept, Decline, End, warning, and status colors semantic.

## 2026-08-16 - Remove password reset from Profile

- Removed the password-reset action from the Profile screen for all account
  types.
- Password recovery remains available from Forgot Password on the sign-in
  screen.
- Android app version is 1.88 (89).

### Suggested iOS equivalent

- Keep password recovery on the unauthenticated sign-in screen and remove it
  from the signed-in Profile screen.

## 2026-08-14 - Restore outgoing pre-answer ringback routing

- Outgoing Android calls are no longer registered with Core-Telecom during
  Calling or Ringing, restoring the previous local ringback audio route and
  volume.
- Core-Telecom registration now occurs when the recipient answers, before
  WebRTC starts, so connected calls retain system audio routing, Bluetooth,
  background, and external controls.
- Incoming Telecom registration and lock-screen call handling are unchanged.
- Android app version is 1.87 (88).

### Suggested iOS equivalent

- Keep app-owned outgoing ringback independent from CallKit audio activation,
  then activate the system call/audio session when the call is answered.

## 2026-08-14 - Call screens use the current app accent

- Replaced the call screen's fixed violet decoration with the app's current
  accent palette across outgoing, incoming, connected, camera-off,
  reconnecting, and error states.
- Kept Accept green, Decline/End red, and video surfaces dark because those
  colors communicate call state rather than branding.
- Android app version is 1.86 (87).

### Suggested iOS equivalent

- Resolve decorative call-screen accents from the same app tint used by other
  screens while preserving semantic call-action colors.

## 2026-08-13 - No outgoing pre-answer call notification

- Removed Android's ongoing call notification during the outgoing Calling and
  Ringing stages.
- The foreground call service starts only after the recipient accepts and the
  WebRTC session begins, so active calls retain background media support.
- Android app version is 1.85 (86).

### Suggested iOS equivalent

- Do not show an app-owned persistent notification while an outgoing call is
  waiting for an answer; continue using CallKit once the call is active.

## 2026-08-13 - Bluetooth routing without Nearby Devices access

- Removed Android's `BLUETOOTH_CONNECT` permission and Nearby Devices runtime
  prompt because EZ Call does not scan, pair, or directly connect Bluetooth
  devices.
- Already-connected Bluetooth, BLE, hearing-aid, wired, and USB devices remain
  available through Android's communication-audio routing APIs.
- Android app version is 1.84 (85).

### Suggested iOS equivalent

- Continue letting AVAudioSession and CallKit route calls to connected audio
  accessories; do not request unrelated Bluetooth discovery access.

## 2026-08-13 - Server-controlled one-to-one call state

- Android no longer creates or updates `callInvites` directly. It calls the
  authenticated `createCallInvite` and `transitionCallState` Cloud Functions.
- The server atomically checks Firebase UID, participant role, current status,
  requested status, and two-minute expiration before committing.
- Caller pending calls can become `missed`; callee pending calls can become
  `delivered`, `answered`, `declined`, or `missed`; either participant can
  transition `answered` to `ended`.
- Terminal states are immutable and repeated identical requests are
  idempotent. Accepted changes increment `stateRevision` and record server
  timestamps plus the transition actor.
- Firestore rules permit participants to read invites but reject direct client
  create, update, and delete operations.
- Android app version is 1.83 (84).

### Required iOS behavior

- Use the same callable function names and statuses; do not write
  `callInvites` directly from iOS.
- Treat rejected conflicting transitions as normal race outcomes and render
  the authoritative Firestore status.
- Keep `callId` limited to letters, digits, underscores, and hyphens.

## 2026-08-13 - Android Core-Telecom call integration

- Firebase call invites and peer-to-peer WebRTC media remain unchanged.
- Every incoming and outgoing Android call is also registered with
  Core-Telecom as a video call with inactive/hold support.
- Lock-screen, Bluetooth headset, Android Auto, and wearable Answer/End actions
  are forwarded to the same Firebase/WebRTC call lifecycle.
- Telecom owns managed-call audio routing. Android's manual `AudioManager`
  route selector is used only when Telecom registration fails.
- System mute disables the local microphone track. System inactive/hold pauses
  both local transmission and remote playback until the call becomes active.
- Ongoing Android calls use a native call-style notification with an End call
  action and are synchronized with rejected, missed, ended, and error states.
- Android app version is 1.82 (83).

### Suggested iOS equivalent

- Use CallKit (`CXProvider` and `CXCallController`) for incoming/outgoing call
  reporting and system answer, end, mute, hold, Bluetooth, lock-screen, and
  competing-call behavior.
- Keep the shared Firebase status strings and WebRTC signaling schema exactly
  aligned with Android; CallKit is the platform lifecycle layer, not a new
  signaling or media backend.

## 2026-08-12 - Automatic WebRTC reconnection

- Monitor active-network changes and WebRTC disconnection/failure states while
  a call is connected.
- Keep the same call and media tracks alive while attempting an ICE restart.
- Offers, answers, and ICE candidates include a negotiation generation so
  repeated negotiations are processed exactly once.
- The caller creates restart offers. The callee requests a restart through the
  authenticated signaling document to prevent offer collisions.
- Show `Reconnecting...`, restore the contact name after recovery, and end the
  call for both participants if recovery does not complete within 25 seconds.
- Both participants must run the updated signaling implementation for network
  handoff recovery.
- Android app version is 1.81 (82).

### Suggested iOS equivalent

- Observe `NWPathMonitor` changes and WebRTC ICE/connection state, preserve the
  current peer connection, and mirror the generation-based Firebase restart
  protocol used by Android.

## 2026-08-12 - Provider-aware password reset

- The Profile screen shows password reset only when the Firebase account has
  the email/password provider linked.
- Google-only accounts do not show password reset because they authenticate
  through Google and have no separate EZ Call password.
- Accounts with both Google and email/password linked retain password reset.
- Android app version is 1.80 (81).

### Suggested iOS equivalent

- Inspect the current Firebase user's linked provider data and show password
  reset only when `password` is present.

## 2026-08-12 - Simplified Settings choices

- Removed the Accent color selection section from Settings.
- Priority contacts are represented by one compact row with the selected
  count instead of an always-visible checklist.
- Tapping that row opens a multi-select popup containing registered EZ Call
  users from the device contacts. Save applies changes; Cancel discards them.
- Android app version is 1.79 (80).

### Suggested iOS equivalent

- Remove the accent picker and present priority-contact selection from a
  compact Settings row into a modal multi-selection list.

## 2026-08-12 - Readable OTP and immediate account exit

- The SMS verification code field uses an explicit contrasting surface, text,
  hint, and border in both light and dark appearance modes.
- Profile sign-out returns to the sign-in screen immediately; clearing the
  platform account chooser state no longer blocks navigation.
- A successful password-reset request from Profile signs out the current
  session, opens the sign-in form, prefills the account email, and tells the
  user to set the new password before signing in again.
- Android app version is 1.78 (79).

### Suggested iOS equivalent

- Give the verification-code field explicit dynamic foreground/background
  colors rather than inheriting them from the presenting alert.
- End the authenticated session after successfully requesting a password-reset
  email and return directly to the sign-in view with the email prefilled.

## 2026-08-09 - Brighter three-green-blade app icon

- Updated the fixed launcher logo to the selected brighter neon-lime palette.
- Three logo blades are green and the remaining blade is white; the original
  glass highlights, pink status dot, geometry, and safe padding are retained.
- The launcher icon remains independent of the selectable in-app accent.
- Android app version is 1.77 (78).

### Suggested iOS equivalent

- Use the matching fixed three-green-blade artwork for the primary AppIcon and
  keep interface accent selection independent from application-icon assets.

## 2026-08-09 - Reliable password visibility toggle

- Fixed the eye control so tapping it now alternates between concealed and
  visible text in both registration password fields and the sign-in password.
- The cursor remains at the end of the current value and the icon reflects the
  resulting visibility state.
- Android app version is 1.76 (77).

### Suggested iOS equivalent

- Toggle `isSecureTextEntry` using explicit state rather than inferring it from
  overlapping keyboard/input flags, and preserve the current selection.

## 2026-08-09 - Restored contact-card detail spacing

- Reverted Android's compact lower card spacing from version 1.74.
- Restored the earlier name, last-call, flexible spacer, and Call-button layout
  while retaining the large rectangular profile photo.
- Android app version is 1.75 (76).

### Suggested iOS equivalent

- Retain the spacing documented for the large contact-card photo design rather
  than mirroring Android 1.74's compact detail experiment.

## 2026-08-09 - Compact contact-card details

- Reduced spacing between the contact name, last-call information, and Call
  button.
- Removed the flexible spacer that previously pushed the Call button away from
  the status label, while preserving two-line name support.
- The large top profile-photo area remains unchanged.
- Android app version is 1.74 (75).

### Suggested iOS equivalent

- Use fixed compact vertical spacing in the lower card content instead of a
  flexible spacer between call status and the Call action.

## 2026-08-09 - Single permanent launcher entry

- Removed Android's former accent-specific launcher aliases.
- `MainActivity` is now the only MAIN/LAUNCHER component, preventing stale
  disabled-alias state from blocking Android Studio, ADB, or launcher starts.
- The fixed bright-green app icon and selectable in-app accent colors remain
  unchanged.
- Android app version is 1.73 (74).

### Suggested iOS equivalent

- Keep one primary app entry and one fixed AppIcon; do not alter launch routing
  when the user changes an in-app accent.

## 2026-08-09 - Large contact-card profile photos

- People cards display the contact's photo edge-to-edge across the entire top
  section instead of inside a circular avatar.
- Photos use center-crop framing and inherit only the card's rounded top
  corners, making faces larger and easier to identify before calling.
- The contact name, last-call state, and Call action remain in a separate lower
  section, and the default user artwork fills the photo area when needed.
- Android app version is 1.72 (73).

### Suggested iOS equivalent

- Use a full-width, aspect-fill image at the top of each two-column contact
  card, clip it with the card's top corners, and keep text/actions in a fixed
  lower content region.

## 2026-08-08 - Fixed bright-green app icon

- The launcher logo is now a brighter green than the Emerald interface accent.
- Changing the selected interface accent no longer changes the application
  icon.
- Android keeps its former launcher aliases only so existing installations
  retain a valid launcher entry after upgrading; all aliases use the same icon.
- Android app version is 1.71 (72).

### Suggested iOS equivalent

- Use the same fixed bright-green asset as the primary AppIcon and remove
  accent-selection calls to `setAlternateIconName`.

## 2026-08-08 - SMS-verified signup phone numbers

- Email/password registration sends a Firebase SMS verification code before
  creating the calling profile.
- First-time Google users must also verify the phone number entered during
  profile completion.
- The verified Firebase phone credential is linked to the user's existing Auth
  identity; a phone number already linked to another account is rejected.
- New Firestore profiles are allowed only when their phone number matches the
  verified `phone_number` in the Firebase Auth token.
- Android app version is 1.70 (71).

### Suggested iOS equivalent

- Use Firebase Phone Auth to verify and link a phone credential before writing
  a new profile. Provide the same code entry, resend, cancel, expiry, throttling,
  and duplicate-phone states, and deploy the shared Firestore rule change.

## 2026-08-08 - Safe deferred application-icon updates

- In-app accent changes remain immediate.
- Launcher-icon changes are deferred until every EZ Call activity has remained
  in the background for two seconds. This prevents Android launchers from
  removing the active task when its launcher alias is disabled.
- Android app version is 1.69 (70).

### Suggested iOS equivalent

- If alternate-icon changes interrupt the current Settings experience, defer
  `setAlternateIconName` until the app backgrounds while keeping the selected
  in-app accent immediate.

## 2026-08-08 - Accent-matched application icons

- Each of the five selectable accent colors has a matching application icon.
- Android switches between launcher aliases when the user selects an accent,
  preserving the logo's glass shading and pink status dot.
- The selected icon is reconciled during app startup and is also used by the
  system launch experience.
- Android app version is 1.68 (69).

### Suggested iOS equivalent

- Provide matching alternate app icons and call `setAlternateIconName` after
  the user selects an accent. Keep the Violet icon as the primary icon.

## 2026-08-08 - Selectable app accent colors

- Settings includes an Accent color card with Violet, Ocean, Sky, Teal, and
  Emerald swatches.
- The selected accent persists locally and updates primary navigation, buttons,
  fields, profile highlights, and call-screen highlights.
- Decline, accept, end-call, and availability colors remain semantic and do not
  change with the selected accent.
- Android app version is 1.67 (68).

### Suggested iOS equivalent

- Store an accent identifier in app preferences and resolve semantic accent
  roles through one shared palette. Present the same five swatches in Settings
  and refresh active screens after selection.

## 2026-08-07 - Registration and password recovery validation

- Password and confirmation fields have tappable eye icons that switch between
  concealed and visible text without moving the cursor.
- Forgot Password opens an email-entry dialog, validates the address, and then
  requests a Firebase password-reset link.
- Profile creation and phone-number edits check whether the normalized number
  already belongs to another UID. Conflicts display
  `Already registered phone number.`
- A newly created email-auth user is removed if its registration fails only
  because the phone number is already owned, so registration can be retried.
- Android app version is 1.66 (67).

### Suggested iOS equivalent

- Add secure-field visibility toggles and an explicit reset-email prompt. Before
  saving a phone-keyed user profile, compare its existing owner UID and present
  the same duplicate-number message without overwriting that profile.

## 2026-07-31 - Device contact display names

- Registered users are still identified and called by normalized phone number.
- When rendering a user, Android prefers the name saved for that phone number
  in the current device's address book.
- The local name is used on People cards, call logs, priority contacts, call
  screens, and incoming or missed-call notifications.
- The EZ Call profile name is used when the number has no local contact name.
- Android app version is 1.65 (66).

### Suggested iOS equivalent

- Resolve each registered E.164 phone number through `CNContactStore` after
  contacts permission is granted. Use the contact's formatted name only for
  display and keep the registered phone number as the calling identity.

## 2026-07-31 - Light and dark appearance

- Settings now includes an Appearance row with Light and Dark choices.
- The selection is stored on the device and is applied to People, Calls,
  Settings, Profile, authentication, phone setup, and country selection.
- Brand purple, status colors, and call-state colors remain consistent in both
  appearances.
- The connected video-call surface remains dark for video/control contrast.
- Android app version is 1.64 (65).

### Suggested iOS equivalent

- Store a user-selected appearance override and apply a matching semantic color
  palette across navigation, forms, cards, and dialogs. Keep video-call chrome
  dark unless a separate call-surface appearance is introduced.

## 2026-07-30 - Calling-state connecting beep

- Android plays a soft, short beep every 1.8 seconds while the outgoing state
  is `Calling`.
- After the receiver acknowledges notification delivery, Android stops that
  beep and starts the normal `Ringing` ringback cadence.
- Both sounds stop on answer, cancellation, failure, or call replacement.
- Android app version is `1.63 (64)`.

### Suggested iOS equivalent

- Use a lightweight local connecting tone before remote delivery is confirmed,
  then replace it with the existing ringback audio after the shared call state
  reports delivery. Keep the two players mutually exclusive.

## 2026-07-30 - Remote cancellation and missed calls

- Cancelling an outgoing Android call before answer writes `missed`, not
  `ended`.
- A backend status push stops the receiver's active ringtone and removes the
  incoming-call presentation even when EZ Call is backgrounded or closed.
- Android then presents a silent missed-call notification with the caller's
  name and available profile photo.
- Before presenting a delayed incoming push, Android checks the current invite
  status so terminal calls cannot begin ringing again.
- Android app version is `1.62 (63)`.

### Suggested iOS equivalent

- When the caller cancels before answer, report the CallKit call ended with the
  remote-ended reason, persist a missed event for the callee, and show a
  non-ringing missed-call notification. Ignore delayed VoIP pushes whose shared
  call record is already terminal.

## 2026-07-30 - Incoming ringtone channel reset

- Android now posts incoming calls on `incoming_video_calls_v3`, a fresh
  high-importance channel configured with the system ringtone.
- Android no longer replaces the active incoming-call notification after a
  delayed profile-photo lookup because that replacement could interrupt the
  ringtone. An immediately available photo is included in the first
  notification instead.
- Android app version is `1.61 (62)`.

### Suggested iOS equivalent

- Keep the incoming CallKit call active while enriching caller metadata; do not
  replace or end the ringing call solely to refresh an image.

## 2026-07-30 - One push path and call-specific notification identity

- FCM is the only Android incoming-call presentation path, whether the app is
  foregrounded or backgrounded.
- The foreground Firestore listener no longer opens a separate silent
  incoming-call screen before push delivery.
- Each `callId` maps to its own notification ID so new calls alert while
  duplicate delivery and photo refreshes remain quiet.
- Android app version is `1.60 (61)`.

### Suggested iOS equivalent

- Use one APNs/CallKit presentation path in all application states and preserve
  the call UUID across notification updates and call actions.

## 2026-07-29 - End current call and accept waiting call

- A second incoming call does not replace the active connected-call screen.
- Its notification offers `Decline` and `End & accept`.
- `End & accept` ends the shared current call before automatically accepting
  and starting the waiting call.
- Duplicate pushes for the already-connected call are ignored.
- Android app version is `1.59 (60)`.

### Suggested iOS equivalent

- Report the second call through CallKit and implement the end-and-answer
  transaction so the active WebRTC session is closed before the waiting
  session acquires camera and microphone resources.

## 2026-07-29 - Reliable ringtone for consecutive incoming calls

- Android tracks which `callId` owns the active incoming-call notification.
- Before a different call is posted, Android removes the previous notification
  so the system does not classify the new call as a silent update.
- Duplicate delivery and profile-photo updates for the same call still update
  without restarting its ringtone.
- Android app version is `1.58 (59)`.

### Suggested iOS equivalent

- Associate each reported incoming call with its UUID and replace only stale
  call reports; do not replay alerts for duplicate push delivery.

## 2026-07-29 - Circular caller photo in notifications

- Incoming caller photos are center-cropped and rendered into circular
  bitmaps with transparent corners before being supplied to Android's system
  call notification.
- Android app version is `1.57 (58)`.

### Suggested iOS equivalent

- Supply the caller's profile image through the platform contact/call-provider
  integration using the same centered circular crop.

## 2026-07-29 - Calling and device-delivered ringing states

- Outgoing calls show Calling until the receiving device obtains the push and
  posts its incoming-call notification.
- The receiving client then acknowledges a shared `delivered` status, causing
  the caller to show Ringing and begin ringback audio.
- Delivery acknowledgment is transactional and cannot replace a later
  answered, declined, missed, ended, or failed state.
- Android app version is `1.56 (57)`.

### Suggested iOS equivalent

- Acknowledge APNs receipt after reporting the incoming call to CallKit, then
  transition the caller from Calling to Ringing from that shared status.

## 2026-07-28 - Reliable background Picture-in-Picture

- Connected calls automatically enter Android Picture-in-Picture when the user
  goes Home, including gesture-navigation and vendor-launcher paths that do not
  reliably send the legacy leave callback.
- Automatic PiP is disabled as soon as call closure begins.
- Android app version is `1.55 (56)`.

### Suggested iOS equivalent

- Start the active `AVPictureInPictureController` from the platform background
  transition path and stop it when call termination begins.

## 2026-07-28 - Camera startup recovery and call-push timing

- Android detects when an opened camera produces no initial frame and restarts
  that same camera once, without changing front/back selection.
- Camera suspension is re-evaluated during the delayed display wake transition
  used by lock-screen call acceptance.
- Incoming-call push logs now distinguish invite-to-trigger, Function lookup,
  FCM send, FCM delivery, and end-to-end latency.
- Android app version is `1.54 (55)`.

### Suggested iOS equivalent

- Observe the first local capture frame and restart the active capture session
  once if startup stalls.
- Record APNs receipt time separately from backend send time so server delay and
  device delivery delay can be diagnosed independently.

## 2026-07-27 - Centered connected-call identity

- The connected-call status dot and remote contact name are centered together
  inside the top pill.
- Long names remain single-line and truncate without shifting the visual group
  to the left.
- Android app version is `1.53 (54)`.

### Suggested iOS equivalent

- Center the complete status-and-name group rather than centering the text
  inside a separately weighted region.

## 2026-07-27 - Connected-call Picture-in-Picture

- Connected Android calls enter native Picture-in-Picture when the user presses
  Home or Back.
- The compact window keeps the remote video or remote camera-off identity and
  call audio active, while hiding the local preview and full call controls.
- Expanding PiP restores the full connected-call interface without creating a
  new call session.
- Android app version is `1.52 (53)`.

### Suggested iOS equivalent

- Use AVPictureInPictureController for the connected remote video and preserve
  the same call session while moving between compact and full-screen states.

## 2026-07-27 - Call-screen contact identity

- Incoming and outgoing waiting screens keep the other user's name visible
  beneath the profile image.
- Connected calls show the other user's name in the top status pill instead of
  the generic `Live` label.
- Long names are truncated within the pill rather than overlapping the camera
  cutout or screen edges.
- Android app version is `1.51 (52)`.

### Suggested iOS equivalent

- Keep the remote participant name visible in every call state and use a
  bounded single-line name in the connected-call status area.

## 2026-07-27 - Full-screen incoming-call access

- Android 14 and newer require separate user-controlled Full screen alerts
  access before EZ Call can present its incoming-call activity over the lock
  screen.
- Android now shows a one-time explanation and provides a persistent Settings
  row with the current access state and a link to the exact system setting.
- The access applies only to incoming-call presentation and does not remove the
  device keyguard.
- Android app version is `1.50 (51)`.

### Suggested iOS equivalent

- Keep CallKit incoming-call capability/status handling visible in app
  settings and direct users to the relevant iOS settings when call
  presentation is unavailable.

## 2026-07-27 - Answer calls above the lock screen

- The incoming and connected call UI can remain visible above Android's lock
  screen, so answering an incoming call does not require unlocking the device.
- The display wakes for the call, but Android's system keyguard is not disabled
  or dismissed for the rest of the device.
- The camera remains suspended while the display is actually off and can resume
  once the call screen is visible.
- Android app version is `1.49 (50)`.

### Suggested iOS equivalent

- Use the platform calling UI to let the user answer from the Lock Screen and
  transition directly into the in-app call without opening unrelated app data.

## 2026-07-27 - Less-transparent bottom navigation

- The home bottom navigation now uses a 75% dark navy glass fill instead of a
  40% black fill.
- The border and active/inactive item colors remain unchanged.
- Android app version is `1.48 (49)`.

### Suggested iOS equivalent

- Increase the tab-bar material/background opacity to approximately 75% while
  retaining the existing border and selection styling.

## 2026-07-27 - Centered adaptive-icon foreground

- The EZ Call camera-aperture mark is now a separate transparent foreground
  over the dark navy icon background.
- Android keeps the mark near half the full adaptive canvas, with optical
  centering and sufficient safe padding for launcher and splash masks.
- Android app version is `1.47 (48)`.

### Suggested iOS equivalent

- Center the symbol optically rather than by the camera extension's outer
  bounds, and preserve comparable padding in every AppIcon export.

## 2026-07-27 - Rounded adaptive launcher icon

- Android now registers the EZ Call artwork as an adaptive launcher icon rather
  than exposing the full square bitmap.
- The launcher applies its native rounded mask around the dark navy artwork.
- Android app version is `1.46 (47)`.

### Suggested iOS equivalent

- Keep the AppIcon source square and let iOS apply the platform corner mask;
  do not bake an additional opaque square outside the intended icon boundary.

## 2026-07-27 - EZ Call branded application icon

- Replaced the previous orange video-camera app icon with the text-free EZ Call
  camera-aperture logo on dark navy.
- The Android asset keeps the glowing purple/white symbol and coral recording
  dot inside safe padding for launcher masking.
- Android app version is `1.45 (46)`.

### Suggested iOS equivalent

- Export the same text-free square artwork into the iOS AppIcon set at all
  required sizes; do not include the product name or tagline inside the icon.

## 2026-07-27 - People-first categorized contacts

- Android now opens with People selected instead of Calls.
- People renders three non-duplicating sections: alphabetized Priority
  contacts, non-priority Recent calls ordered by latest activity, and the
  remaining Available people alphabetically.
- Calls no longer pins priority contacts and contains only the complete
  reverse-chronological call-event list.
- Android app version is `1.44 (45)`.

### Suggested iOS equivalent

- Make People the initial tab and derive the same three contact groups from
  priority preferences and each contact's latest call, while retaining the
  separate ungrouped event history in Calls.

## 2026-07-27 - Priority contacts pinned above call history

- Calls now begins with an alphabetized Priority contacts section containing
  every selected priority contact, including people with no call history.
- Each pinned row has the current profile image and direct video-call action.
- The complete reverse-chronological Recent calls list remains below and still
  includes each event involving a priority contact.
- Android app version is `1.43 (44)`.

### Suggested iOS equivalent

- Render priority contacts as a pinned section before the call-event list,
  sourcing it independently from call history so new priority contacts appear
  immediately.

## 2026-07-27 - Security Mode uses receiver contacts

- Security Mode no longer uses the priority-contact list.
- When enabled, Android normalizes the incoming caller's phone number and
  allows the call only if it matches a number in the receiving device's phone
  contacts. Unknown callers are declined before ringtone or call UI.
- When disabled, an incoming registered caller is allowed even if the receiver
  has not saved that caller.
- Priority contacts remain a separate account-scoped preference.
- Android app version is `1.42 (43)`.

### Suggested iOS equivalent

- With Contacts permission, normalize the CallKit caller handle and compare it
  against the receiver's CNContact phone numbers before presenting an incoming
  call. Fail closed when Security Mode is enabled and contact access is absent.

## 2026-07-27 - Separate call history and alphabetical people

- Calls and People are now independent selected tabs rather than scroll
  shortcuts to one shared list.
- People displays all registered users found in the phone contacts sorted by
  display name.
- Calls displays every call event newest first with direction, result, time,
  contact identity, and a callback action.
- Existing Firestore call-invite fields supply historical caller/callee data;
  no database migration is required.
- Android app version is `1.41 (42)`.

### Suggested iOS equivalent

- Keep a separate alphabetized contacts collection and reverse-chronological
  call-event collection, resolving current profile data when available and
  falling back to the identity stored with each call invitation.

## 2026-07-27 - Dedicated settings and original priority-only calls

This original Android behavior was superseded by version 1.42, where Security
Mode checks the receiver's phone contacts instead.

- The Settings navigation item now opens a dedicated screen; the profile icon
  remains the route to profile editing.
- Security Mode and priority contacts are scoped to the signed-in account on
  the device. With Security Mode enabled, non-priority incoming calls are
  declined before any call UI or ringtone is shown.
- Priority choices include only registered EZ Call users found in the device's
  contacts.
- Language preference supports System default or English and is stored for the
  whole installation.
- Android app version is `1.40 (41)`.

### Suggested iOS equivalent

- Store security and priority choices per Firebase UID, apply the same filter
  before presenting CallKit UI, and expose language through the app's settings
  bundle or an in-app picker.

## 2026-07-27 - Country-aware phone entry

- Signup, Google profile completion, device setup, and profile editing now use
  a searchable country/region calling-code selector.
- Android defaults from SIM/network/locale, parses pasted international values,
  validates possible lengths, and stores the canonical E.164 number.
- Android app version is `1.39 (40)`.

### Suggested iOS equivalent

- Use PhoneNumberKit or equivalent current metadata for a searchable country
  picker and persist the same E.164 value used by Android and Firestore.

## 2026-07-27 - Local camera-off profile photo

- The floating self-preview shows the signed-in user's profile photo whenever
  their camera is manually off or automatically suspended while locked.
- Users without a stored photo see the shared neutral silhouette.
- The overlay follows the effective local WebRTC camera state and clears as
  soon as camera transmission resumes.
- Android app version is `1.38 (39)`.

### Suggested iOS equivalent

- Overlay the current account's avatar inside the local preview whenever its
  outgoing video track is disabled, including lock-driven suspension.

## 2026-07-27 - Transparent local renderer layer

- The Android local video renderer no longer owns an opaque black background,
  because that layer could cover the hardware-composited camera surface.
- A custom rounded outline clips the transparent renderer, and the containing
  viewport provides the black empty-video background instead.
- Android app version is `1.37 (38)`.

### Suggested iOS equivalent

- Keep the video-rendering view transparent and place any empty-state color on
  the containing masked view so it cannot cover decoded frames.

## 2026-07-27 - Stable black local-preview inset

- Android restored a 4dp inset around the local video surface because a 1dp
  inset could prevent `SurfaceViewRenderer` from displaying on some devices.
- The backing layer remains black, so the safety inset is visually seamless
  rather than grey.
- Android app version is `1.36 (37)`.

### Suggested iOS equivalent

- Keep the black backing layer, but retain enough internal spacing for the
  platform video-rendering layer to composite reliably inside its mask.

## 2026-07-27 - Black local-preview frame

- The floating local-camera viewport now sits 1dp inside a black frame instead
  of 4dp inside a dark-grey glass layer.
- A thin purple outline remains, while clipping, dragging, and tap behavior are
  unchanged.
- Android app version is `1.35 (36)`.

### Suggested iOS equivalent

- Make the local-preview mask nearly flush with a black backing layer and keep
  only a one-pixel accent border visible.

## 2026-07-27 - Native call notification actions and caller photo

- Android 12 and newer use the native incoming-call notification style with
  green Accept and red Decline color hints.
- The notification shows the caller's profile photo when one is stored. The
  existing Firestore call invite supplies the image after the small FCM payload
  triggers the ringtone, and the active notification is updated silently.
- Android 8 through 11 retain standard notification actions and show the caller
  image as a large notification icon.
- Android app version is `1.34 (35)`.

### Suggested iOS equivalent

- Supply the caller image through the notification service/CallKit contact
  presentation while keeping the initial VoIP or remote notification payload
  small and time-sensitive.

## 2026-07-27 - Avatar-sized reactive ring origin

- The connected camera-off ring begins at the same diameter as the profile
  image and expands outward with incoming voice volume.
- The diffuse glow also begins near the avatar size and grows/brights with the
  same smoothed remote level.
- Android app version is `1.33 (34)`.

### Suggested iOS equivalent

- Set the resting stroked-ring frame equal to the avatar frame, then drive a
  larger scale range from the normalized remote audio level.

## 2026-07-27 - Animated incoming-call actions

- Accept and Decline use continuous breathing scale/opacity animation with a
  matching colored halo to signal that they are actionable.
- Accept receives stronger visual emphasis; Remind Me stays static.
- Animations stop as soon as the waiting screen is dismissed.
- Android app version is `1.32 (33)`.

### Suggested iOS equivalent

- Add subtle repeating scale/opacity and halo animations to Accept and Decline,
  honor Reduce Motion, and stop them when the call leaves its incoming state.

## 2026-07-27 - Single reactive ring and glow

- The connected camera-off avatar now uses one outlined ring and one diffuse
  glow rather than two outlined rings.
- Both respond to incoming voice level; the glow has the larger scale and
  brightness range.
- Waiting-call artwork is unchanged.
- Android app version is `1.31 (32)`.

### Suggested iOS equivalent

- Drive one stroked circle and one blurred radial glow from the smoothed remote
  audio level, with a stronger response on the glow.

## 2026-07-27 - Reduced local-preview corner radius

- The draggable local preview now uses a 10dp outer glass radius and an 8dp
  clipped video radius.
- Clipping and movement behavior are unchanged.
- Android app version is `1.30 (31)`.

### Suggested iOS equivalent

- Reduce the floating preview container and video-mask radii to match Android's
  more squared shape without changing pan-gesture behavior.

## 2026-07-27 - Clipped and draggable local preview

- The floating local-camera renderer is inset inside a directly clipped
  rounded viewport so video cannot cover or overflow its glass frame.
- The preview can be dragged during a connected call and is clamped to the
  visible stage plus system safe-area insets.
- A tap without movement continues to show or hide call controls.
- Android app version is `1.29 (30)`.

### Suggested iOS equivalent

- Mask the local video layer to the preview bounds and add a pan gesture that
  clamps the preview frame to the call view's safe area while preserving tap
  behavior for controls.

## 2026-07-27 - Remote-audio-reactive call rings

- When the remote camera is off during a connected call, the rings behind that
  participant's avatar expand and brighten in response to their incoming
  WebRTC audio level.
- Android uses inbound audio-level statistics with an audio-energy fallback,
  a silence floor, and smoothed attack/release behavior.
- Waiting-call rings remain decorative because remote media is not connected
  yet.
- Android app version is `1.28 (29)`.

### Suggested iOS equivalent

- Meter the remote WebRTC audio track, normalize and smooth the level, and use
  it to drive scale/opacity of the camera-off avatar rings on the main thread.

## 2026-07-27 - Outgoing ringback

- The caller hears a repeating ring-and-pause tone while an outgoing call is
  waiting for the callee to answer.
- Ringback stops immediately for every answered or terminal call state and is
  released when the call screen is destroyed.
- This is separate from the callee's incoming notification ringtone.
- Android app version is `1.27 (28)`.

### Suggested iOS equivalent

- Use an app-owned audio session for local outgoing ringback and stop it from
  the same centralized call-state transitions that handle answer, decline,
  timeout, failure, cancellation, and hang-up.

## 2026-07-26 - Stable call termination and home return

- Ending, cancelling, declining, dismissing an error, or receiving a remote
  hang-up now returns to the EZ Calls home screen even when the call was opened
  directly from a notification.
- Call completion is idempotent and locally suppresses the completed call ID
  for five minutes so delayed signaling cannot reopen an ended call.
- Android app version is `1.26 (27)`.

### Suggested iOS equivalent

- Route every terminal call state through one coordinator that writes the
  final status once, clears CallKit/notification state, dismisses the call UI,
  and ignores delayed signaling for the completed call identifier.

## 2026-07-26 - Contact name layout correction

- Contact cards reserve enough height for two complete name lines plus the
  last-call label and Call action.
- Flexible vertical space is placed below metadata rather than compressing the
  contact name.
- Android app version is `1.25 (26)`.

### Suggested iOS equivalent

- Give names a two-line content region and allow the card to grow before
  truncating text; metadata and call controls must not overlap it.

## 2026-07-26 - Real last-call metadata

- Eligible contact cards show the latest real call time from the current
  user's existing `callInvites` documents.
- Incoming missed calls show a pink `Missed` badge; other latest calls show a
  relative time or date.
- Contacts are ordered by most recent call, then alphabetically when no call
  history exists.
- No Firestore schema, index, or security-rule change is required.
- Android app version is `1.24 (25)`.

### Suggested iOS equivalent

- Listen to participant-scoped outgoing and incoming call invites, merge by
  contact, and retain only the newest entry.
- Match Android's relative-time labels and incoming-missed badge while
  filtering the displayed results through the same eligible-contact list.

## 2026-07-26 - Glass authentication and profile screens

- Login, signup, Google profile completion, and Profile now use the same dark
  navy, translucent glass, purple-accent design as the home screen.
- Inputs and buttons are shorter with 12dp corners; the home search and Call
  controls use 8dp corners.
- Authentication and profile behavior did not change.
- Android app version is `1.23 (24)`.

### Suggested iOS equivalent

- Apply the same dark glass tokens to authentication and profile views while
  keeping native secure-field, Google sign-in, photo-picker, and account
  behavior intact.

## 2026-07-26 - Glowing contact avatars

- Every contact avatar now has a soft radial halo derived from its card accent.
- The same treatment applies to real photos and the neutral missing-photo
  fallback.
- Android app version is `1.22 (23)`.

### Suggested iOS equivalent

- Place a blurred radial accent layer behind each circular contact image,
  keeping the photo itself sharp and unobscured.

## 2026-07-26 - Slim home controls

- The search field and per-contact Call actions now use shorter heights,
  smaller icons/type, tighter spacing, and lighter corner radii.
- Contact eligibility, search behavior, and call actions are unchanged.
- Android app version is `1.21 (22)`.

### Suggested iOS equivalent

- Match the compact control dimensions while retaining accessible touch
  targets around the complete contact card and search field.

## 2026-07-26 - Simplified home header and contact search

- Android now uses one large `EZ Calls` home title with no instructional
  subtitle.
- The top-right profile control is a transparent purple person icon linked to
  the existing Profile screen.
- A glass search field filters the currently eligible registered contacts by
  name while typing.
- Android app version is `1.20 (21)`.

### Suggested iOS equivalent

- Match the simplified title and transparent profile control.
- Filter only the contacts already authorized for display; searching must not
  expose users outside the device-contact and database intersection.

## 2026-07-26 - Home profile avatar

- The home header now shows the signed-in user's real profile photo instead of
  a settings icon.
- When no photo exists, Android shows the shared neutral user silhouette.
- The avatar uses a translucent purple frame and opens the existing Profile
  screen when tapped.
- Returning from profile editing refreshes the header avatar from the local
  profile cache.
- Android app version is `1.19 (20)`.

### Suggested iOS equivalent

- Use the current user's stored profile photo as the home profile control and
  the same neutral silhouette when absent.
- Refresh the avatar when the profile view closes; keep the control linked to
  profile editing.

## 2026-07-26 - Canonical glassmorphism styling

- Android now follows the supplied `Video Call App` source tokens: nearly
  transparent white/black glass layers, fine tinted borders, purple/cyan
  ambient glows, and exact purple, pink, and green action gradients.
- Contact cards and call controls allow the page lighting to remain visible
  through their surfaces instead of appearing as opaque colored panels.
- The redesign changes presentation only. Real contact data, call state,
  notification behavior, Firebase signaling, and WebRTC media are unchanged.
- Android app version is `1.18 (19)`.

### Suggested iOS equivalent

- Use native material/blur views with the same alpha, border, and accent tokens
  from the supplied design source.
- Preserve the actual contact and video-call data model; do not reproduce the
  reference app's mock presence or call-history values.

## 2026-07-26 - Navy contacts and call-state redesign

- The Android contacts view uses a dark navy background, two-column contact
  panels, circular real/neutral profile images, colored video-call actions, and
  a persistent Calls/People/Settings navigation bar.
- Outgoing and incoming screens use a top state pill, concentric profile-image
  treatment, centered identity/status content, and large circular actions.
- Incoming calls provide Decline, Remind Me, and Accept. Remind Me backgrounds
  the activity without changing the shared invite state.
- Connected calls retain full-screen remote video, a top-right local preview,
  a camera-off identity treatment, and auto-hiding Camera/End/Flip controls.
- No Firebase schema, call-state, notification, or WebRTC protocol changed.
- Android app version is `1.17 (18)`.

### Suggested iOS equivalent

- Mirror the navy/purple/pink/green visual tokens and responsive two-column
  contact grid without fabricating presence or call-history data.
- Keep the real remote camera feed full-screen and use the profile treatment
  only while ringing or when video is disabled.
- Implement Remind Me as dismissing/backgrounding the incoming UI while
  retaining the CallKit or notification call affordance.
- Preserve the existing shared Firestore and WebRTC behavior.

## 2026-07-26 - Disable video while the device is locked

- Android disables its local WebRTC video track when the screen turns off or
  the device locks.
- The participant's existing camera-off signaling field is updated so the
  remote user sees the camera-off state.
- Unlock restores video only if the user had not manually disabled the camera.
- Audio, signaling, and the active call remain connected while video is
  suspended.
- Android app version is `1.16 (17)`.

### Suggested iOS equivalent

- Observe protected-data and application lifecycle changes and disable the
  local WebRTC video track when the device locks.
- Publish the same camera-off state used by the manual camera control.
- Restore the video track after unlock/activation only when the saved user
  camera preference is enabled; keep audio and signaling connected.

## 2026-07-25 - Single ringtone and non-destructive Back

- Android no longer starts a second ringtone inside the incoming-call activity;
  only the high-priority call notification owns incoming ringing.
- The notification is dismissed on Accept, Decline, remote End, or missed-call
  completion.
- Pressing system Back during a connected call backgrounds the app while the
  call and foreground media service continue.
- Android app version is `1.15 (16)`.

### Suggested iOS equivalent

- Keep one owner for incoming ringtone playback, preferably CallKit/APNs, and
  stop it from shared call-state transitions.
- Treat normal navigation away from a connected call as backgrounding, not as
  hang-up; only the explicit end-call action should terminate media.

## 2026-07-25 - Subscribe after secured invite creation

- The outgoing caller creates and awaits the shared invite document before
  attaching its status listener.
- This ordering is required because participant-only Firestore rules cannot
  authorize a read of a call document that does not exist yet.
- Invite-creation failure is shown to the caller instead of leaving the ringing
  UI open.
- Android app version is `1.14 (15)`.

### Suggested iOS equivalent

- Await successful creation of `callInvites/{callId}` before registering the
  caller's snapshot listener for call-state changes.
- Surface create and listener errors as terminal call UI rather than continuing
  to ring.

## 2026-07-25 - Two-column contacts

- Registered callable contacts are displayed as two equal-width portrait photo
  tiles per row.
- The entire tile remains the tap target for starting a call, with the contact
  name overlaid at the bottom of the photo.
- An odd final contact remains half width instead of stretching across the
  screen; non-contact states remain full width.
- Android app version is `1.13 (14)`.

### Suggested iOS equivalent

- Use a two-column adaptive SwiftUI grid with matching gutters and portrait
  tile proportions.
- Keep filtering and call behavior unchanged, and make the complete tile the
  accessible call action.

## 2026-07-25 - Call response and no-answer synchronization

- Receiver `answered` and `declined` states are preserved even when the push
  delivery function finishes immediately afterward.
- The caller exits the ringing state as soon as the receiver answers or
  declines and sees an explicit decline message.
- An unanswered outgoing call times out after two minutes, writes `missed` to
  the shared invite, and shows the caller that the contact did not answer.
- Push-delivery failures produce explicit caller feedback instead of leaving
  the ringing screen open.
- Android app version is `1.12 (13)`.

### Suggested iOS equivalent

- Treat the invite document as the shared call-state source and preserve
  terminal receiver states against late push-delivery bookkeeping.
- Mirror the two-minute `missed` transition and close incoming UI when that
  state arrives.
- Keep notification action work alive until the shared status update is queued
  or acknowledged.

## 2026-07-25 - Notification Answer starts the call

- The notification body and automatic full-screen presentation still open the
  incoming Accept/Decline screen.
- Pressing the notification's explicit Answer action now marks the invite as
  answered and starts the video-call connection immediately.
- Answer handling is idempotent when the call screen is already open, so a
  repeated notification intent cannot start WebRTC twice.
- Android app version is `1.11 (12)`.

### Suggested iOS equivalent

- Make the CallKit or notification Answer action invoke the same acceptance and
  media-join path as the in-app Accept control.
- Keep a normal notification tap as navigation to incoming-call UI, and guard
  the answer handler so the same call cannot be joined twice.

## 2026-07-24 - Original Firebase project restored

- Android again uses Firebase project `ez-call-5467b` and Android Firebase app
  `1:532440329624:android:f220892be06aabf0c1cfed`.
- Access to the original owning Google account was recovered, so existing
  Authentication users and Firestore data remain available.
- The temporary `ez-call-67296` configuration is no longer used by Android.
- Android restored the original debug signing certificate required by the
  original project's Google OAuth client.
- No Firestore records were migrated, deleted, or overwritten.
- Background incoming-call delivery uses the Blaze
  `sendIncomingCallInvite` function with a two-minute push expiration.
- The function is deployed as a Node.js 22 2nd Gen Firestore create trigger in
  `us-central1`.
- Push payloads do not contain Base64 profile photos; the shared profile image
  remains in Firestore.
- Android app version is `1.10 (11)`.

### Suggested iOS equivalent

- Register or retain the final iOS bundle identifier inside Firebase project
  `ez-call-5467b` so both platforms use the same Authentication users and
  Firestore data.
- Download `GoogleService-Info.plist` from `ez-call-5467b`; do not use a plist
  from the temporary replacement project.
- Preserve the same Firestore profile and call-signaling schemas.
- Use the same call ID and caller metadata fields for APNs/FCM delivery, and
  load profile imagery separately instead of embedding it in the push payload.

## 2026-07-24 - Firebase project replacement

- Android now uses Firebase project `ez-call-67296` and Android Firebase app
  `1:262355750047:android:7e9c55457b57b4d94966aa`.
- The inaccessible project `ez-call-5467b` is no longer used by current Android
  builds.
- Existing authentication users and Firestore documents were not migrated, so
  users register again in the replacement project.
- Email/Password and Google providers are enabled, and the existing secured
  Firestore rules were deployed to the new database.
- Android debug builds use a new Android-only signing certificate; this does
  not change the iOS signing identity.
- Android app version is `1.9 (10)`.

### Suggested iOS equivalent

- Register the final iOS bundle identifier as an iOS app inside Firebase
  project `ez-call-67296`.
- Download a new `GoogleService-Info.plist` from that project and do not reuse
  configuration from `ez-call-5467b`.
- Preserve the same Firestore profile and call-signaling schemas so Android and
  iOS users can communicate.

## 2026-07-12 - Incoming ringtone and call sound effects

- Incoming calls loop the device's configured ringtone until the call is
  accepted, declined, ended, or the activity is destroyed.
- Connecting a call plays a short acknowledgement sound once.
- Ending, declining, or failing a call plays a distinct end sound once.
- Background Android FCM notifications use a high-importance call channel with
  ringtone, vibration, Answer/Decline actions, lock-screen visibility, and a
  two-minute timeout.
- The notification channel ID changed to `incoming_video_calls_v2` because
  Android notification-channel sound settings are immutable after creation.
- Background/closed-app ringing still requires a trusted server or deployed
  Firebase Function to send the high-priority FCM data message.
- Android app version is `1.6 (7)`.

### Suggested iOS equivalent

- Use the app's incoming-call presentation and APNs/CallKit path for background
  ringing; do not try to maintain a Firestore listener while suspended.
- Stop ringtone playback on accept, decline, remote end, timeout, and teardown.
- Play short connected/end sounds once at the same state transitions.
- Ensure APNs call-status handling dismisses stale incoming-call UI.

## 2026-07-11 - Contacts visual system

- Contacts now uses an off-white branded header and charcoal content area,
  matching Authentication and Profile.
- The header contains the orange Profile action, `Your contacts` title, and the
  current user's identity.
- Search uses a dark outlined field; contact cards retain full-photo tap targets
  with an orange border.
- Permission and empty-state messaging uses the shared orange accent.
- Contact matching, Firestore listeners, and tap-to-call behavior are unchanged.
- Android app version is `1.5 (6)`.

### Suggested iOS equivalent

- Apply the same header/body composition and color tokens to the SwiftUI
  contacts screen.
- Preserve full-photo contact cards as the call action and keep filtering logic
  unchanged.

## 2026-07-11 - Off-white, charcoal, and orange palette

- Brand surfaces use off-white `#F4FFF0`, charcoal `#353535`, black, and white.
- Primary actions and interactive accents use orange `#F95830`.
- Authentication and Profile use off-white headers over charcoal form areas.
- Positive call actions such as Allow, Accept, and Close use orange with dark
  text; destructive actions remain red.
- The Android launcher mark now uses orange.
- Android app version is `1.4 (5)`.

### Suggested iOS equivalent

- Define shared SwiftUI color tokens using the exact hex values above.
- Apply orange only to primary/positive actions and retain red for destructive
  actions.
- Use the same off-white-header and charcoal-form composition on authentication
  and Profile screens.

## 2026-07-11 - Authentication and Profile visual system

- Sign-up, sign-in, Google profile completion, and Profile use the same dark
  visual system with a deep primary-green header and green primary actions.
- Sign-up now validates a repeated password before creating the Firebase user.
- Password inputs provide show/hide controls.
- Sign-in includes a Firebase password-reset action.
- Switching between sign-up and sign-in returns the form to the top.
- Profile retains editable name, phone number, and photo, with login email shown
  read-only and password reset/sign-out separated into an Account section.
- No Firebase schema, authentication provider, contact filtering, or call
  signaling behavior changed.
- Android app version is `1.3 (4)`.

### Suggested iOS equivalent

- Reuse the same primary-green header, dark form surfaces, outlined controls,
  spacing, and action hierarchy for SwiftUI authentication and Profile views.
- Add repeated-password validation and secure-field visibility toggles.
- Keep the same Firebase password-reset, profile editing, and sign-out behavior.

## 2026-07-11 - Show only registered phone contacts

- Android requests read-only access to the user's device contacts.
- The main screen displays a person only when that person's registered
  Firestore phone number also exists in the signed-in user's address book.
- Contacts remain hidden when permission is denied.
- Device contacts are read locally and are not uploaded to Firestore.
- Phone matching accepts exact normalized digits and local/country-code forms
  that share the same final ten digits.
- The address book is refreshed whenever the main screen resumes.
- Android app version is `1.2 (3)`.

### Suggested iOS equivalent

- Add `NSContactsUsageDescription` and request access through `CNContactStore`.
- Read contact phone numbers locally and intersect them with registered
  Firestore profiles before rendering callable users.
- Show no callable users when Contacts access is denied and provide a route to
  the app's Settings page.
- Do not upload the user's address book.

## 2026-07-11 - Google sign-in and profile completion

- Android now supports Google authentication in addition to Email/Password.
- Google authentication uses Firebase Auth and Android Credential Manager.
- A returning Google user with an existing Firestore calling profile enters the
  contacts screen immediately.
- A first-time Google user must complete a calling profile before entering the
  app. Required fields are display name and phone number; profile photo is
  optional and can be selected from the device.
- The Firestore profile schema and lookup remain unchanged: profiles are stored
  in `users`, keyed by normalized phone number, and include the Firebase `uid`.
- Users can cancel incomplete profile setup and choose a different account.
- Sign-out clears both Firebase Auth state and the platform credential-selection
  state so another Google account can be selected.
- Android app version is `1.1 (2)`.

### Suggested iOS equivalent

- Use Firebase Auth's Google provider and the supported Google Sign-In iOS SDK.
- After Firebase authentication, query `users` by the authenticated `uid`.
- If no complete profile exists, require name and phone number before opening
  contacts; allow an optional user-selected photo.
- Preserve the same normalized-phone Firestore document and field schema so the
  two mobile clients remain interoperable.
## 2026-07-18 - Neutral profile-photo fallback

- Authentication, Profile, Contacts, incoming-call, and call screens show the
  same neutral local user avatar when no profile image was selected.
- Android does not bundle or assign stock-person profile photos.
- Profiles without an image keep `photoBase64` empty and do not write a
  generated `photoKey`.
- Android app version is `1.8 (9)`.

### Suggested iOS equivalent

- Use one neutral SF Symbol or local avatar asset whenever profile image data
  is absent or invalid.
- Keep the shared Firestore `photoBase64` value empty instead of assigning a
  stock image or platform-specific photo key.

## 2026-07-18 - EZ Call application identity

- The Android display name is now `EZ Call`.
- The Android package and namespace changed from `com.memorycalls.onetoone` to
  `com.ezcall.onetoone`.
- Android now uses a separate Firebase application registered for the new
  package; the previous Firebase project is not shared with EZ Call.
- The shared EZ Call Firebase project ID is `ez-call-5467b`; register the iOS
  bundle identifier as a separate iOS app inside this same project when the
  iOS client should interoperate with Android.
- Contact profiles no longer contain email addresses or FCM tokens. Device push
  tokens are stored privately at `userDevices/{uid}`.
- Call invites include `callerUid` and `calleeUid`; Firestore permits signaling
  access only to those two authenticated users.
- Android app version is `1.7 (8)`.

### Suggested iOS equivalent

- Rename the visible iOS application to `EZ Call`.
- Decide on the final iOS bundle identifier before registering the iOS app in
  the new EZ Call Firebase project.
- Replace `GoogleService-Info.plist` with the configuration downloaded for that
  exact bundle identifier.
