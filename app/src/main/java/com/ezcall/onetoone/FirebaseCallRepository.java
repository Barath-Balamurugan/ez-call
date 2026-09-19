package com.ezcall.onetoone;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FirebaseCallRepository {
    private static final String TAG = "FirebaseCalls";
    private static final String PREFS_NAME = "memory_calls";
    private static final String KEY_LOCAL_PHONE_NUMBER = "local_phone_number";
    private static final String KEY_LOCAL_DISPLAY_NAME = "local_display_name";
    private static final String KEY_LOCAL_EMAIL = "local_email";
    private static final String KEY_LOCAL_PHOTO_BASE64 = "local_photo_base64";
    private static final long INCOMING_CALL_WINDOW_MILLIS = 120_000L;
    private static final String FIREBASE_TEST_PHONE_PREFIX = "1555010";

    private FirebaseCallRepository() {
    }

    static void registerDeviceForPhoneNumber(Context context, String phoneNumber) {
        if (!isConfigured(context)) {
            Log.w(TAG, "Firebase is not configured yet. Add app/google-services.json to enable device registration.");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot register an FCM token without an authenticated user.");
            return;
        }

        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            Map<String, Object> data = new HashMap<>();
            data.put("uid", user.getUid());
            data.put("normalizedPhoneNumber", normalizePhoneNumber(phoneNumber));
            data.put("fcmToken", token);
            data.put("updatedAt", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance()
                    .collection("userDevices")
                    .document(user.getUid())
                    .set(data, SetOptions.merge())
                    .addOnFailureListener(error -> Log.e(TAG, "Failed to register FCM token", error));
        }).addOnFailureListener(error -> Log.e(TAG, "Failed to fetch FCM token", error));
    }

    static void fetchTurnIceServers(Context context, IceServerLoadListener listener) {
        if (!isConfigured(context) || FirebaseAuth.getInstance().getCurrentUser() == null) {
            listener.onIceServersLoaded(Collections.emptyList());
            return;
        }
        FirebaseFunctions.getInstance()
                .getHttpsCallable("getIceServers")
                .call()
                .addOnSuccessListener(result -> listener.onIceServersLoaded(
                        iceServersFromCallableResult(result.getData())
                ))
                .addOnFailureListener(error -> {
                    Log.i(TAG, "TURN relay credentials unavailable; using STUN only.", error);
                    listener.onIceServersLoaded(Collections.emptyList());
                });
    }

    static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    static boolean isSignedIn(Context context) {
        if (!isConfigured(context)) {
            return hasLocalPhoneNumber(context);
        }
        return FirebaseAuth.getInstance().getCurrentUser() != null && hasLocalPhoneNumber(context);
    }

    static boolean hasLocalPhoneNumber(Context context) {
        return !localPhoneNumber(context).isEmpty();
    }

    static void saveLocalPhoneNumber(Context context, String phoneNumber) {
        prefs(context).edit().putString(KEY_LOCAL_PHONE_NUMBER, phoneNumber).apply();
    }

    static void signOut(Context context) {
        prefs(context).edit()
                .remove(KEY_LOCAL_PHONE_NUMBER)
                .remove(KEY_LOCAL_DISPLAY_NAME)
                .remove(KEY_LOCAL_EMAIL)
                .remove(KEY_LOCAL_PHOTO_BASE64)
                .apply();
        if (isConfigured(context)) {
            FirebaseAuth.getInstance().signOut();
        }
    }

    static String currentPhoneNumberOrFallback(Context context) {
        String localPhoneNumber = localPhoneNumber(context);
        if (!localPhoneNumber.isEmpty()) {
            return localPhoneNumber;
        }
        if (isConfigured(context)) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
                return user.getPhoneNumber();
            }
        }
        return MainActivity.PATIENT_PHONE_NUMBER;
    }

    static String currentDisplayNameOrFallback(Context context) {
        String cachedName = prefs(context).getString(KEY_LOCAL_DISPLAY_NAME, "");
        if (!cachedName.isEmpty()) {
            return cachedName;
        }
        if (isConfigured(context)) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                    return user.getDisplayName().trim();
                }
                if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                    return user.getEmail().trim();
                }
            }
        }
        return "EZ Call Patient";
    }

    static String currentPhotoBase64OrEmpty(Context context) {
        return ProfilePhotoUtils.sanitizeBase64(prefs(context).getString(KEY_LOCAL_PHOTO_BASE64, ""));
    }

    static String currentEmailOrEmpty(Context context) {
        FirebaseUser user = isConfigured(context) ? FirebaseAuth.getInstance().getCurrentUser() : null;
        return authenticatedEmail(user);
    }

    private static String authenticatedEmail(FirebaseUser user) {
        if (user == null) {
            return "";
        }
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail().trim();
        }
        for (UserInfo provider : user.getProviderData()) {
            if (provider.getEmail() != null && !provider.getEmail().trim().isEmpty()) {
                return provider.getEmail().trim();
            }
        }
        return "";
    }

    static UserProfile cachedProfile(Context context) {
        String phoneNumber = currentPhoneNumberOrFallback(context);
        FirebaseUser user = isConfigured(context) ? FirebaseAuth.getInstance().getCurrentUser() : null;
        return new UserProfile(
                user == null ? "" : user.getUid(),
                currentDisplayNameOrFallback(context),
                currentEmailOrEmpty(context),
                phoneNumber,
                normalizePhoneNumber(phoneNumber),
                currentPhotoBase64OrEmpty(context),
                false
        );
    }

    static void saveUserProfile(
            Context context,
            String uid,
            String displayName,
            String email,
            String phoneNumber,
            String photoBase64,
            ProfileSaveListener listener
    ) {
        if (!isConfigured(context)) {
            listener.onFailure(new IllegalStateException("Firebase is not configured."));
            return;
        }

        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedPhoneNumber.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Phone number is required."));
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String resolvedUid = uid == null || uid.trim().isEmpty()
                ? currentUser == null ? "" : currentUser.getUid()
                : uid.trim();
        String resolvedEmail = email == null || email.trim().isEmpty()
                ? authenticatedEmail(currentUser)
                : email.trim();
        String resolvedPhotoBase64 = ProfilePhotoUtils.sanitizeBase64(photoBase64);

        Map<String, Object> data = new HashMap<>();
        data.put("uid", resolvedUid);
        data.put("displayName", displayName.trim());
        data.put("phoneNumber", phoneNumber.trim());
        data.put("normalizedPhoneNumber", normalizedPhoneNumber);
        data.put("photoBase64", resolvedPhotoBase64);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        DocumentReference profileReference = FirebaseFirestore.getInstance()
                .collection("users")
                .document(normalizedPhoneNumber);
        profileReference.get()
                .addOnSuccessListener(existingProfile -> {
                    if (PhoneProfileOwnership.belongsToDifferentUser(
                            existingProfile.exists(),
                            existingProfile.getString("uid"),
                            resolvedUid
                    )) {
                        listener.onFailure(PhoneProfileOwnership.alreadyRegisteredError());
                        return;
                    }
                    profileReference.set(data, SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                cacheProfile(
                                        context,
                                        new UserProfile(
                                                resolvedUid,
                                                displayName.trim(),
                                                resolvedEmail,
                                                phoneNumber.trim(),
                                                normalizedPhoneNumber,
                                                resolvedPhotoBase64,
                                                false
                                        )
                                );
                                listener.onSuccess();
                            })
                            .addOnFailureListener(error -> reportProfileWriteFailure(
                                    profileReference,
                                    resolvedUid,
                                    error,
                                    listener
                            ));
                })
                .addOnFailureListener(listener::onFailure);
    }

    static void updateCurrentUserProfile(
            Context context,
            String displayName,
            String phoneNumber,
            String photoBase64,
            ProfileSaveListener listener
    ) {
        if (!isConfigured(context)) {
            listener.onFailure(new IllegalStateException("Firebase is not configured."));
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            listener.onFailure(new IllegalStateException("You are not logged in."));
            return;
        }

        String cleanedName = displayName == null ? "" : displayName.trim();
        String cleanedPhone = phoneNumber == null ? "" : phoneNumber.trim();
        String normalizedPhoneNumber = normalizePhoneNumber(cleanedPhone);
        if (cleanedName.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Name is required."));
            return;
        }
        if (normalizedPhoneNumber.isEmpty()) {
            listener.onFailure(new IllegalArgumentException("Phone number is required."));
            return;
        }

        String oldNormalizedPhoneNumber = normalizePhoneNumber(currentPhoneNumberOrFallback(context));
        String email = authenticatedEmail(currentUser);
        String resolvedPhotoBase64 = ProfilePhotoUtils.sanitizeBase64(photoBase64);

        Map<String, Object> data = new HashMap<>();
        data.put("uid", currentUser.getUid());
        data.put("displayName", cleanedName);
        data.put("phoneNumber", cleanedPhone);
        data.put("normalizedPhoneNumber", normalizedPhoneNumber);
        data.put("photoBase64", resolvedPhotoBase64);
        data.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference profileReference = db.collection("users")
                .document(normalizedPhoneNumber);
        // Creating users/{phone} requires request.auth.token.phone_number to match. After a
        // phone-number change the cached ID token still carries the old claim, so force a
        // refresh before writing or Firestore rejects the create.
        currentUser.getIdToken(true).addOnCompleteListener(tokenRefresh ->
                profileReference.get()
                .addOnSuccessListener(existingProfile -> {
                    if (PhoneProfileOwnership.belongsToDifferentUser(
                            existingProfile.exists(),
                            existingProfile.getString("uid"),
                            currentUser.getUid()
                    )) {
                        listener.onFailure(PhoneProfileOwnership.alreadyRegisteredError());
                        return;
                    }
                    profileReference.set(data, SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                if (!oldNormalizedPhoneNumber.isEmpty()
                                        && !oldNormalizedPhoneNumber.equals(normalizedPhoneNumber)) {
                                    db.collection("users")
                                            .document(oldNormalizedPhoneNumber)
                                            .delete()
                                            .addOnFailureListener(error -> Log.w(
                                                    TAG,
                                                    "Failed to delete old profile document",
                                                    error
                                            ));
                                }
                                UserProfile profile = new UserProfile(
                                        currentUser.getUid(),
                                        cleanedName,
                                        email,
                                        cleanedPhone,
                                        normalizedPhoneNumber,
                                        resolvedPhotoBase64,
                                        false
                                );
                                cacheProfile(context, profile);
                                registerDeviceForPhoneNumber(context, cleanedPhone);
                                listener.onSuccess();
                            })
                            .addOnFailureListener(error -> reportProfileWriteFailure(
                                    profileReference,
                                    currentUser.getUid(),
                                    error,
                                    listener
                            ));
                })
                .addOnFailureListener(listener::onFailure));
    }

    private static void reportProfileWriteFailure(
            DocumentReference profileReference,
            String requestedUid,
            Exception originalError,
            ProfileSaveListener listener
    ) {
        profileReference.get()
                .addOnSuccessListener(latestProfile -> {
                    if (PhoneProfileOwnership.belongsToDifferentUser(
                            latestProfile.exists(),
                            latestProfile.getString("uid"),
                            requestedUid
                    )) {
                        listener.onFailure(PhoneProfileOwnership.alreadyRegisteredError());
                    } else {
                        listener.onFailure(originalError);
                    }
                })
                .addOnFailureListener(unused -> listener.onFailure(originalError));
    }

    static void fetchAndCacheProfileForCurrentUser(Context context, ProfileLoadListener listener) {
        if (!isConfigured(context)) {
            listener.onFailure(new IllegalStateException("Firebase is not configured."));
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            listener.onFailure(new IllegalStateException("You are not logged in."));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("uid", currentUser.getUid())
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        listener.onMissingProfile();
                        return;
                    }
                    FirebaseUser signedInUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (signedInUser == null || !signedInUser.getUid().equals(currentUser.getUid())) {
                        listener.onFailure(new IllegalStateException("Your signed-in account changed. Reopen your profile."));
                        return;
                    }
                    UserProfile storedProfile = profileFromDocument(snapshot.getDocuments().get(0));
                    // Public calling profiles intentionally do not store login email.
                    UserProfile profile = new UserProfile(
                            storedProfile.uid, storedProfile.displayName, authenticatedEmail(signedInUser),
                            storedProfile.phoneNumber, storedProfile.normalizedPhoneNumber,
                            storedProfile.photoBase64, storedProfile.hiddenFromContacts
                    );
                    if (profile.phoneNumber.isEmpty()) {
                        listener.onMissingProfile();
                        return;
                    }
                    cacheProfile(context, profile);
                    registerDeviceForPhoneNumber(context, profile.phoneNumber);
                    listener.onProfileLoaded(profile);
                })
                .addOnFailureListener(listener::onFailure);
    }

    static ListenerRegistration listenForRegisteredUsers(Context context, UserProfilesListener listener) {
        if (!isConfigured(context)) {
            Log.w(TAG, "Firebase is not configured yet. Add app/google-services.json to load users.");
            return null;
        }

        String currentPhoneNumber = normalizePhoneNumber(currentPhoneNumberOrFallback(context));
        return FirebaseFirestore.getInstance()
                .collection("users")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onProfilesChanged(Collections.emptyList(), error);
                        return;
                    }
                    if (snapshot == null) {
                        listener.onProfilesChanged(Collections.emptyList(), null);
                        return;
                    }

                    List<UserProfile> profiles = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        try {
                            UserProfile profile = profileFromDocument(document);
                            String skipReason = userProfileSkipReason(profile, currentPhoneNumber);
                            if (!skipReason.isEmpty()) {
                                Log.i(TAG, "Skipping user " + document.getId() + ": " + skipReason);
                                continue;
                            }
                            profiles.add(profile);
                        } catch (RuntimeException exception) {
                            Log.w(TAG, "Skipping malformed user document " + document.getId(), exception);
                        }
                    }

                    Log.i(TAG, "Loaded " + snapshot.size() + " user documents, showing " + profiles.size() + " contacts");
                    Collections.sort(profiles, Comparator.comparing(profile -> profile.displayName.toLowerCase()));
                    listener.onProfilesChanged(profiles, null);
                });
    }

    static Task<Void> createOutgoingCallInvite(
            Context context,
            String callerPhoneNumber,
            String calleeName,
            String calleePhoneNumber,
            String calleeUid,
            String callId
    ) {
        if (!isConfigured(context)) {
            Log.w(TAG, "Firebase is not configured yet. Add app/google-services.json to send real call invites.");
            return Tasks.forException(new IllegalStateException("Firebase is not configured."));
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || calleeUid == null || calleeUid.trim().isEmpty()) {
            Log.w(TAG, "Authenticated caller and callee UID are required for a call invite.");
            return Tasks.forException(new IllegalStateException(
                    "Authenticated caller and callee UID are required for a call invite."
            ));
        }

        Map<String, Object> request = new HashMap<>();
        request.put("callId", callId);
        request.put("callerPhoneNumber", callerPhoneNumber);
        request.put("calleeName", calleeName);
        request.put("calleePhoneNumber", calleePhoneNumber);
        request.put("calleeUid", calleeUid.trim());

        return FirebaseFunctions.getInstance()
                .getHttpsCallable("createCallInvite")
                .call(request)
                .continueWith(task -> {
                    task.getResult();
                    return (Void) null;
                })
                .addOnSuccessListener(unused -> Log.i(TAG, "Created outgoing call invite " + callId))
                .addOnFailureListener(error -> Log.e(TAG, "Failed to create call invite", error));
    }

    static Task<Void> markCallInviteStatus(Context context, String callId, String status) {
        if (!isConfigured(context) || callId == null || callId.isEmpty()) {
            return Tasks.forException(new IllegalStateException(
                    "Firebase and a call ID are required to update call status."
            ));
        }

        Map<String, Object> request = new HashMap<>();
        request.put("callId", callId);
        request.put("status", status);
        return FirebaseFunctions.getInstance()
                .getHttpsCallable("transitionCallState")
                .call(request)
                .continueWith(task -> {
                    Object response = task.getResult().getData();
                    if (response instanceof Map) {
                        Object accepted = ((Map<?, ?>) response).get("accepted");
                        if (Boolean.FALSE.equals(accepted)) {
                            Object authoritativeStatus = ((Map<?, ?>) response).get("status");
                            throw new IllegalStateException(
                                    "The server kept call " + callId + " in "
                                            + authoritativeStatus + "."
                            );
                        }
                    }
                    return (Void) null;
                })
                .addOnSuccessListener(unused -> Log.i(
                        TAG,
                        "Server accepted call " + callId + " transition to " + status
                ))
                .addOnFailureListener(error -> Log.e(
                        TAG,
                        "Server rejected call " + callId + " transition to " + status,
                        error
                ));
    }

    static Task<Void> markCallInviteDelivered(Context context, String callId) {
        if (!isConfigured(context) || callId == null || callId.isEmpty()) {
            return Tasks.forException(new IllegalStateException(
                    "Firebase and a call ID are required to acknowledge delivery."
            ));
        }

        return markCallInviteStatus(context, callId, "delivered").addOnSuccessListener(unused ->
                Log.i(TAG, "Call " + callId + " delivery was acknowledged.")
        ).addOnFailureListener(error ->
                Log.e(TAG, "Failed to acknowledge call " + callId + " delivery.", error)
        );
    }

    static ListenerRegistration listenForCallStatus(
            Context context,
            String callId,
            CallStatusListener listener
    ) {
        if (!isConfigured(context) || callId == null || callId.isEmpty()) {
            return null;
        }

        return FirebaseFirestore.getInstance()
                .collection("callInvites")
                .document(callId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Failed to listen for call status", error);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        String status = valueOrDefault(snapshot.get("status"), "");
                        if (!status.isEmpty()) {
                            listener.onCallStatus(status);
                        }
                    }
                });
    }

    static ListenerRegistration listenForCallHistory(
            Context context,
            CallHistoryListener listener
    ) {
        if (!isConfigured(context)) {
            listener.onHistoryChanged(
                    Collections.emptyList(),
                    new IllegalStateException("Firebase is not configured.")
            );
            return null;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            listener.onHistoryChanged(
                    Collections.emptyList(),
                    new IllegalStateException("You are not logged in.")
            );
            return null;
        }

        Object historyLock = new Object();
        Map<String, CallHistoryEntry> outgoingEntries = new HashMap<>();
        Map<String, CallHistoryEntry> incomingEntries = new HashMap<>();

        ListenerRegistration outgoingRegistration = FirebaseFirestore.getInstance()
                .collection("callInvites")
                .whereEqualTo("callerUid", currentUser.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    synchronized (historyLock) {
                        replaceHistoryEntries(outgoingEntries, snapshot, false);
                        listener.onHistoryChanged(
                                combinedHistory(outgoingEntries, incomingEntries),
                                error
                        );
                    }
                });

        ListenerRegistration incomingRegistration = FirebaseFirestore.getInstance()
                .collection("callInvites")
                .whereEqualTo("calleeUid", currentUser.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    synchronized (historyLock) {
                        replaceHistoryEntries(incomingEntries, snapshot, true);
                        listener.onHistoryChanged(
                                combinedHistory(outgoingEntries, incomingEntries),
                                error
                        );
                    }
                });

        return () -> {
            outgoingRegistration.remove();
            incomingRegistration.remove();
        };
    }

    private static void replaceHistoryEntries(
            Map<String, CallHistoryEntry> target,
            com.google.firebase.firestore.QuerySnapshot snapshot,
            boolean incoming
    ) {
        if (snapshot == null) {
            return;
        }

        target.clear();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Map<String, Object> data = document.getData();
            if (data == null) {
                continue;
            }

            String otherUid = firstValue(
                    data,
                    incoming ? "callerUid" : "calleeUid"
            );
            String otherPhoneNumber = firstValue(
                    data,
                    incoming ? "callerPhoneNumber" : "calleePhoneNumber"
            );
            String otherName = firstValue(
                    data,
                    incoming ? "callerName" : "calleeName"
            );
            String otherPhotoBase64 = firstValue(
                    data,
                    incoming ? "callerPhotoBase64" : "calleePhotoBase64"
            );
            String otherNormalizedPhoneNumber = firstValue(
                    data,
                    incoming
                            ? "callerPhoneNumberNormalized"
                            : "calleePhoneNumberNormalized"
            );
            if (otherNormalizedPhoneNumber.isEmpty()) {
                otherNormalizedPhoneNumber = normalizePhoneNumber(otherPhoneNumber);
            }

            long createdAtMillis = millisFromValue(data.get("createdAtMillis"));
            if (createdAtMillis <= 0 && data.get("createdAt") instanceof Timestamp) {
                createdAtMillis = ((Timestamp) data.get("createdAt")).toDate().getTime();
            }
            if (createdAtMillis <= 0) {
                continue;
            }

            target.put(
                    document.getId(),
                    new CallHistoryEntry(
                            document.getId(),
                            otherUid,
                            otherName,
                            otherPhoneNumber,
                            otherNormalizedPhoneNumber,
                            ProfilePhotoUtils.sanitizeBase64(otherPhotoBase64),
                            valueOrDefault(data.get("status"), ""),
                            createdAtMillis,
                            incoming
                    )
            );
        }
    }

    private static List<CallHistoryEntry> combinedHistory(
            Map<String, CallHistoryEntry> outgoingEntries,
            Map<String, CallHistoryEntry> incomingEntries
    ) {
        List<CallHistoryEntry> history = new ArrayList<>(
                outgoingEntries.size() + incomingEntries.size()
        );
        history.addAll(outgoingEntries.values());
        history.addAll(incomingEntries.values());
        history.sort((left, right) -> Long.compare(
                right.createdAtMillis,
                left.createdAtMillis
        ));
        return history;
    }

    static ListenerRegistration listenForIncomingCalls(
            Context context,
            IncomingCallListener listener
    ) {
        if (!isConfigured(context)) {
            Log.w(TAG, "Firebase is not configured yet. Add app/google-services.json to listen for calls.");
            return null;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "Cannot listen for incoming calls without an authenticated user.");
            return null;
        }

        return FirebaseFirestore.getInstance()
                .collection("callInvites")
                .whereEqualTo("calleeUid", currentUser.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Failed to listen for incoming calls", error);
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        return;
                    }

                    for (int i = 0; i < snapshot.getDocuments().size(); i++) {
                        Map<String, Object> data = snapshot.getDocuments().get(i).getData();
                        if (data == null) {
                            continue;
                        }
                        String inviteStatus = valueOrDefault(data.get("status"), "");
                        if (!"ringing".equals(inviteStatus) && !"sent".equals(inviteStatus)) {
                            continue;
                        }
                        long createdAtMillis = millisFromValue(data.get("createdAtMillis"));
                        if (createdAtMillis <= 0
                                || System.currentTimeMillis() - createdAtMillis > INCOMING_CALL_WINDOW_MILLIS) {
                            continue;
                        }

                        String callId = valueOrDefault(
                                data.get("callId"),
                                "memory-calls-default"
                        );
                        if (CallSessionGuard.shouldIgnoreIncomingCall(context, callId)) {
                            continue;
                        }

                        listener.onIncomingCall(
                                valueOrDefault(data.get("callerName"), "Incoming caller"),
                                valueOrDefault(data.get("callerPhoneNumber"), "Unknown number"),
                                callId,
                                valueOrDefault(data.get("callerPhotoBase64"), "")
                        );
                        return;
                    }
                });
    }

    static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.replaceAll("[^0-9]", "");
    }

    private static List<IceServerConfiguration> iceServersFromCallableResult(Object resultData) {
        if (!(resultData instanceof Map)) {
            return Collections.emptyList();
        }
        Object values = ((Map<?, ?>) resultData).get("iceServers");
        if (!(values instanceof List)) {
            return Collections.emptyList();
        }

        List<IceServerConfiguration> servers = new ArrayList<>();
        for (Object value : (List<?>) values) {
            if (!(value instanceof Map)) {
                continue;
            }
            Map<?, ?> server = (Map<?, ?>) value;
            Object urlsValue = server.get("urls");
            if (!(urlsValue instanceof List)) {
                continue;
            }
            List<String> urls = new ArrayList<>();
            for (Object urlValue : (List<?>) urlsValue) {
                String url = valueOrDefault(urlValue, "").trim();
                if (url.matches("(?i)^turns?:.+") && url.length() <= 500) {
                    urls.add(url);
                }
            }
            String username = valueOrDefault(server.get("username"), "").trim();
            String credential = valueOrDefault(server.get("credential"), "").trim();
            if (!urls.isEmpty() && !username.isEmpty() && !credential.isEmpty()) {
                servers.add(new IceServerConfiguration(urls, username, credential));
            }
        }
        return servers;
    }

    private static String localPhoneNumber(Context context) {
        return prefs(context).getString(KEY_LOCAL_PHONE_NUMBER, "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void cacheProfile(Context context, UserProfile profile) {
        prefs(context).edit()
                .putString(KEY_LOCAL_PHONE_NUMBER, profile.phoneNumber)
                .putString(KEY_LOCAL_DISPLAY_NAME, profile.displayName)
                .putString(KEY_LOCAL_EMAIL, profile.email)
                .putString(KEY_LOCAL_PHOTO_BASE64, profile.photoBase64)
                .apply();
    }

    private static UserProfile profileFromDocument(DocumentSnapshot document) {
        Map<String, Object> data = document.getData();
        if (data == null) {
            data = Collections.emptyMap();
        }

        String phoneNumber = firstValue(
                data,
                "phoneNumber",
                "phone",
                "phone_number",
                "mobile",
                "mobileNumber",
                "mobile_number",
                "contactNumber",
                "contact_number",
                "number"
        );
        String normalizedPhoneNumber = firstValue(
                data,
                "normalizedPhoneNumber",
                "phoneNumberNormalized",
                "normalized_phone_number",
                "phoneNormalized",
                "phone_normalized"
        );
        if (normalizedPhoneNumber.isEmpty()) {
            normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        }
        if (normalizedPhoneNumber.isEmpty()) {
            normalizedPhoneNumber = normalizePhoneNumber(document.getId());
        }
        if (phoneNumber.isEmpty() && !normalizedPhoneNumber.isEmpty()) {
            phoneNumber = "+" + normalizedPhoneNumber;
        }

        String email = firstValue(data, "email", "emailAddress", "email_address");
        String displayName = firstValue(
                data,
                "displayName",
                "display_name",
                "name",
                "fullName",
                "full_name",
                "username",
                "userName",
                "user_name"
        );
        if (displayName.isEmpty()) {
            String firstName = firstValue(data, "firstName", "first_name", "givenName", "given_name");
            String lastName = firstValue(data, "lastName", "last_name", "familyName", "family_name");
            displayName = (firstName + " " + lastName).trim();
        }
        if (displayName.isEmpty()) {
            displayName = email;
        }
        if (displayName.isEmpty() && !document.getId().trim().isEmpty()) {
            displayName = "User " + document.getId();
        }

        String photoBase64 = firstValue(data, "photoBase64", "photo", "photo_base64", "profilePhotoBase64");
        boolean hiddenFromContacts = isTestUserDocument(data) || isFirebaseTestPhoneNumber(normalizedPhoneNumber);

        return new UserProfile(
                firstValue(data, "uid", "userId", "user_id"),
                displayName,
                email,
                phoneNumber,
                normalizedPhoneNumber,
                ProfilePhotoUtils.sanitizeBase64(photoBase64),
                hiddenFromContacts
        );
    }

    private static long millisFromValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException error) {
                return -1;
            }
        }
        return -1;
    }

    private static String userProfileSkipReason(UserProfile profile, String currentPhoneNumber) {
        if (profile.uid.trim().isEmpty()) {
            return "missing Firebase UID";
        }
        if (profile.displayName.trim().isEmpty()) {
            return "missing display name";
        }
        if (profile.phoneNumber.trim().isEmpty() || profile.normalizedPhoneNumber.trim().isEmpty()) {
            return "missing phone number";
        }
        if (profile.normalizedPhoneNumber.equals(currentPhoneNumber)) {
            return "current signed-in user";
        }
        if (profile.hiddenFromContacts) {
            return "test/hidden user";
        }
        return "";
    }

    private static boolean isTestUserDocument(Map<String, Object> data) {
        return booleanValue(data.get("isTestUser"))
                || booleanValue(data.get("testUser"))
                || booleanValue(data.get("is_test_user"))
                || booleanValue(data.get("hiddenFromContacts"))
                || booleanValue(data.get("hideFromContacts"))
                || booleanValue(data.get("hidden_from_contacts"))
                || booleanValue(data.get("hide_from_contacts"));
    }

    private static boolean isFirebaseTestPhoneNumber(String normalizedPhoneNumber) {
        return normalizedPhoneNumber != null && normalizedPhoneNumber.startsWith(FIREBASE_TEST_PHONE_PREFIX);
    }

    private static String firstValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            String value = valueOrDefault(data.get(key), "");
            if (!value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String valueOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.trim().isEmpty() ? fallback : text;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim().toLowerCase();
            return "true".equals(text) || "yes".equals(text) || "1".equals(text);
        }
        return false;
    }

    interface IncomingCallListener {
        void onIncomingCall(String callerName, String callerPhoneNumber, String callId, String callerPhotoBase64);
    }

    interface CallStatusListener {
        void onCallStatus(String status);
    }

    interface CallHistoryListener {
        void onHistoryChanged(List<CallHistoryEntry> history, Exception error);
    }

    interface ProfileSaveListener {
        void onSuccess();

        void onFailure(Exception error);
    }

    interface IceServerLoadListener {
        void onIceServersLoaded(List<IceServerConfiguration> iceServers);
    }

    interface ProfileLoadListener {
        void onProfileLoaded(UserProfile profile);

        void onMissingProfile();

        void onFailure(Exception error);
    }

    interface UserProfilesListener {
        void onProfilesChanged(List<UserProfile> profiles, Exception error);
    }

    static final class UserProfile {
        final String uid;
        final String displayName;
        final String email;
        final String phoneNumber;
        final String normalizedPhoneNumber;
        final String photoBase64;
        final boolean hiddenFromContacts;

        UserProfile(
                String uid,
                String displayName,
                String email,
                String phoneNumber,
                String normalizedPhoneNumber,
                String photoBase64,
                boolean hiddenFromContacts
        ) {
            this.uid = uid;
            this.displayName = displayName;
            this.email = email;
            this.phoneNumber = phoneNumber;
            this.normalizedPhoneNumber = normalizedPhoneNumber;
            this.photoBase64 = photoBase64;
            this.hiddenFromContacts = hiddenFromContacts;
        }
    }

    static final class IceServerConfiguration {
        final List<String> urls;
        final String username;
        final String credential;

        IceServerConfiguration(List<String> urls, String username, String credential) {
            this.urls = new ArrayList<>(urls);
            this.username = username;
            this.credential = credential;
        }
    }

    static final class CallHistoryEntry {
        final String callId;
        final String otherUid;
        final String otherName;
        final String otherPhoneNumber;
        final String otherNormalizedPhoneNumber;
        final String otherPhotoBase64;
        final String status;
        final long createdAtMillis;
        final boolean incoming;

        CallHistoryEntry(
                String callId,
                String otherUid,
                String otherName,
                String otherPhoneNumber,
                String otherNormalizedPhoneNumber,
                String otherPhotoBase64,
                String status,
                long createdAtMillis,
                boolean incoming
        ) {
            this.callId = callId;
            this.otherUid = otherUid;
            this.otherName = otherName;
            this.otherPhoneNumber = otherPhoneNumber;
            this.otherNormalizedPhoneNumber = otherNormalizedPhoneNumber;
            this.otherPhotoBase64 = otherPhotoBase64;
            this.status = status;
            this.createdAtMillis = createdAtMillis;
            this.incoming = incoming;
        }
    }
}
