const admin = require("firebase-admin");
const {
  onDocumentCreated,
  onDocumentUpdated,
} = require("firebase-functions/v2/firestore");
const {HttpsError, onCall} = require("firebase-functions/v2/https");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {
  canSendIncomingInvite,
  notificationFailedUpdate,
  notificationSentUpdate,
  receiverStatusUpdate,
} = require("./call-status");
const {
  isPendingStatus,
  resolveCallTransition,
} = require("./call-state");

admin.initializeApp();

const CALL_INVITE_LIFETIME_MILLIS = 120_000;
const MAX_CALL_ID_LENGTH = 180;

function requiredString(value, fieldName, maxLength = 200) {
  const normalized = String(value || "").trim();
  if (!normalized || normalized.length > maxLength) {
    throw new HttpsError(
        "invalid-argument",
        `${fieldName} is required and must be at most ${maxLength} characters.`,
    );
  }
  return normalized;
}

function normalizedPhone(value, fieldName) {
  const normalized = requiredString(value, fieldName, 40).replace(/[^0-9]/g, "");
  if (normalized.length < 7 || normalized.length > 15) {
    throw new HttpsError("invalid-argument", `${fieldName} is not a valid phone number.`);
  }
  return normalized;
}

function validateCallId(value) {
  const callId = requiredString(value, "callId", MAX_CALL_ID_LENGTH);
  if (!/^[A-Za-z0-9_-]+$/.test(callId)) {
    throw new HttpsError("invalid-argument", "callId contains unsupported characters.");
  }
  return callId;
}

function participantRole(invite, uid) {
  if (String(invite.callerUid || "") === uid) {
    return "caller";
  }
  if (String(invite.calleeUid || "") === uid) {
    return "callee";
  }
  return "";
}

function transitionUpdate(invite, status, actorRole, actorUid, timestamp) {
  const update = {
    status,
    stateRevision: Number(invite.stateRevision || 0) + 1,
    stateChangedAt: timestamp,
    updatedAt: timestamp,
    lastTransitionRole: actorRole,
    lastTransitionByUid: actorUid || "server",
  };
  if (!isPendingStatus(status)) {
    update.expiresAtMillis = admin.firestore.FieldValue.delete();
    update.expiresAt = admin.firestore.FieldValue.delete();
  }
  return update;
}

async function applyServerTransition(inviteRef, requestedStatus, extraUpdate = {}) {
  return admin.firestore().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(inviteRef);
    if (!snapshot.exists) {
      return null;
    }
    const invite = snapshot.data() || {};
    const decision = resolveCallTransition(invite.status, requestedStatus, "server");
    const update = {...extraUpdate};
    if (decision.allowed && !decision.idempotent) {
      Object.assign(update, transitionUpdate(
          invite,
          decision.status,
          "server",
          "server",
          admin.firestore.FieldValue.serverTimestamp(),
      ));
    }
    if (Object.keys(update).length > 0) {
      transaction.update(inviteRef, update);
    }
    return decision.allowed ? decision.status : String(invite.status || "");
  });
}

exports.createCallInvite = onCall({timeoutSeconds: 15}, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Sign in before starting a call.");
  }

  const data = request.data || {};
  const callId = validateCallId(data.callId);
  const callerPhoneNumberNormalized = normalizedPhone(
      data.callerPhoneNumber,
      "callerPhoneNumber",
  );
  const calleePhoneNumberNormalized = normalizedPhone(
      data.calleePhoneNumber,
      "calleePhoneNumber",
  );
  const calleeUid = requiredString(data.calleeUid, "calleeUid", 128);
  if (calleeUid === request.auth.uid) {
    throw new HttpsError("invalid-argument", "You cannot call your own account.");
  }

  const firestore = admin.firestore();
  const inviteRef = firestore.collection("callInvites").doc(callId);
  const callerRef = firestore.collection("users").doc(callerPhoneNumberNormalized);
  const calleeRef = firestore.collection("users").doc(calleePhoneNumberNormalized);

  return firestore.runTransaction(async (transaction) => {
    const [existingInvite, callerSnapshot, calleeSnapshot] = await Promise.all([
      transaction.get(inviteRef),
      transaction.get(callerRef),
      transaction.get(calleeRef),
    ]);
    if (existingInvite.exists) {
      throw new HttpsError("already-exists", "This call already exists.");
    }
    if (!callerSnapshot.exists || callerSnapshot.get("uid") !== request.auth.uid) {
      throw new HttpsError("permission-denied", "The caller profile is not verified.");
    }
    if (!calleeSnapshot.exists || calleeSnapshot.get("uid") !== calleeUid) {
      throw new HttpsError("not-found", "The selected contact is not registered.");
    }

    const caller = callerSnapshot.data() || {};
    const callee = calleeSnapshot.data() || {};
    const nowMillis = Date.now();
    const timestamp = admin.firestore.FieldValue.serverTimestamp();
    transaction.create(inviteRef, {
      type: "video_call",
      status: "ringing",
      stateRevision: 0,
      callStateVersion: 1,
      stateChangedAt: timestamp,
      lastTransitionRole: "caller",
      lastTransitionByUid: request.auth.uid,
      callerUid: request.auth.uid,
      calleeUid,
      callerName: String(caller.displayName || "EZ Call user").trim(),
      callerPhoneNumber: String(caller.phoneNumber || data.callerPhoneNumber || "").trim(),
      callerPhoneNumberNormalized,
      callerPhotoBase64: String(caller.photoBase64 || ""),
      calleeName: String(data.calleeName || callee.displayName || "EZ Call user").trim()
          .slice(0, 120),
      calleePhoneNumber: String(callee.phoneNumber || data.calleePhoneNumber || "").trim(),
      calleePhoneNumberNormalized,
      callId,
      createdAtMillis: nowMillis,
      expiresAtMillis: nowMillis + CALL_INVITE_LIFETIME_MILLIS,
      expiresAt: admin.firestore.Timestamp.fromMillis(
          nowMillis + CALL_INVITE_LIFETIME_MILLIS,
      ),
      createdAt: timestamp,
      updatedAt: timestamp,
    });
    return {callId, status: "ringing", stateRevision: 0};
  });
});

exports.transitionCallState = onCall({timeoutSeconds: 15}, async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Sign in before updating a call.");
  }
  const data = request.data || {};
  const callId = validateCallId(data.callId);
  const requestedStatus = requiredString(data.status, "status", 40).toLowerCase();
  const inviteRef = admin.firestore().collection("callInvites").doc(callId);

  return admin.firestore().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(inviteRef);
    if (!snapshot.exists) {
      throw new HttpsError("not-found", "This call no longer exists.");
    }
    const invite = snapshot.data() || {};
    const role = participantRole(invite, request.auth.uid);
    if (!role) {
      throw new HttpsError("permission-denied", "You are not a participant in this call.");
    }

    const nowMillis = Date.now();
    const expiresAtMillis = Number(invite.expiresAtMillis || 0);
    if (isPendingStatus(invite.status) && expiresAtMillis > 0 && nowMillis >= expiresAtMillis) {
      const update = transitionUpdate(
          invite,
          "missed",
          "server",
          "server",
          admin.firestore.FieldValue.serverTimestamp(),
      );
      transaction.update(inviteRef, update);
      if (requestedStatus !== "missed") {
        return {
          accepted: false,
          callId,
          status: "missed",
          stateRevision: update.stateRevision,
          reason: "expired",
        };
      }
      return {
        accepted: true,
        callId,
        status: "missed",
        stateRevision: update.stateRevision,
      };
    }

    const decision = resolveCallTransition(invite.status, requestedStatus, role);
    if (!decision.allowed) {
      throw new HttpsError(
          "failed-precondition",
          `Cannot change ${invite.status} to ${requestedStatus}.`,
          {currentStatus: String(invite.status || ""), reason: decision.reason},
      );
    }
    if (decision.idempotent) {
      return {
        accepted: true,
        callId,
        status: decision.status,
        stateRevision: Number(invite.stateRevision || 0),
        idempotent: true,
      };
    }

    const update = transitionUpdate(
        invite,
        decision.status,
        role,
        request.auth.uid,
        admin.firestore.FieldValue.serverTimestamp(),
    );
    transaction.update(inviteRef, update);
    return {
      accepted: true,
      callId,
      status: decision.status,
      stateRevision: update.stateRevision,
    };
  });
});

exports.expireUnansweredCalls = onSchedule(
    {schedule: "every 1 minutes", timeZone: "Etc/UTC"},
    async () => {
      const nowMillis = Date.now();
      const snapshot = await admin.firestore()
          .collection("callInvites")
          .where("expiresAt", "<=", admin.firestore.Timestamp.fromMillis(nowMillis))
          .limit(200)
          .get();
      const results = await Promise.allSettled(snapshot.docs.map(async (document) => {
        if (!isPendingStatus(document.get("status"))) {
          return;
        }
        await applyServerTransition(document.ref, "missed");
      }));
      const failures = results.filter((result) => result.status === "rejected");
      if (failures.length > 0) {
        console.error(`Failed to expire ${failures.length} unanswered calls.`);
      }
      console.info(`Checked ${snapshot.size} expired call invites.`);
    },
);

exports.sendIncomingCallInvite = onDocumentCreated("callInvites/{callId}", async (event) => {
  const triggerStartedAtMillis = Date.now();
  const invite = event.data && event.data.data();
  if (!invite || invite.type !== "video_call") {
    return;
  }

  const calleePhoneNumber = invite.calleePhoneNumberNormalized;
  if (!calleePhoneNumber) {
    console.warn("Call invite missing calleePhoneNumberNormalized", event.params.callId);
    return;
  }

  const firestore = admin.firestore();
  const inviteCalleeUid = String(invite.calleeUid || "").trim();
  const userPromise = firestore.collection("users").doc(calleePhoneNumber).get();
  const devicePromise = inviteCalleeUid
    ? firestore.collection("userDevices").doc(inviteCalleeUid).get()
    : null;
  const userDoc = await userPromise;
  if (!userDoc.exists) {
    console.warn("No registered user for phone number", calleePhoneNumber);
    await applyServerTransition(event.data.ref, "callee_not_registered");
    return;
  }

  const calleeUid = inviteCalleeUid || String(userDoc.get("uid") || "");
  if (!calleeUid) {
    console.warn("Registered user has no Firebase UID", calleePhoneNumber);
    await applyServerTransition(event.data.ref, "callee_missing_uid");
    return;
  }

  const deviceDoc = devicePromise
    ? await devicePromise
    : await firestore.collection("userDevices").doc(calleeUid).get();
  const fcmToken = deviceDoc.get("fcmToken");
  if (!fcmToken) {
    console.warn("Registered user has no FCM token", calleePhoneNumber);
    await applyServerTransition(event.data.ref, "callee_missing_fcm_token");
    return;
  }

  const currentInviteBeforeSend = await event.data.ref.get();
  if (!currentInviteBeforeSend.exists ||
      !canSendIncomingInvite(String(currentInviteBeforeSend.get("status") || ""))) {
    console.info("Skipped stale incoming-call push", event.params.callId);
    return;
  }

  const callerName = String(invite.callerName || "EZ Call Patient");
  const callerPhoneNumber = String(invite.callerPhoneNumber || "");
  const callId = String(invite.callId || "");
  const inviteCreatedAtMillis = Number(invite.createdAtMillis || 0);
  const pushSentAtMillis = Date.now();
  const messageData = {
    type: "incoming_call",
    callerName,
    callerPhoneNumber,
    // Keep the data message below FCM's payload limit. The full profile image
    // remains in Firestore and is not suitable for a push notification.
    callerPhotoBase64: "",
    callId,
    inviteCreatedAtMillis: String(inviteCreatedAtMillis),
    pushSentAtMillis: String(pushSentAtMillis)
  };

  try {
    const messageId = await admin.messaging().send({
      token: fcmToken,
      android: {
        priority: "high",
        ttl: 120000
      },
      apns: {
        headers: {
          "apns-priority": "10",
          "apns-push-type": "alert",
          "apns-expiration": String(Math.floor(Date.now() / 1000) + 120)
        },
        payload: {
          aps: {
            alert: {
              title: "Incoming video call",
              body: `${callerName} is calling`
            },
            sound: "default",
            category: "INCOMING_CALL"
          }
        }
      },
      data: messageData
    });
    const completedAtMillis = Date.now();
    console.info("Incoming-call push sent", JSON.stringify({
      callId: event.params.callId,
      messageId,
      inviteToTriggerMillis: inviteCreatedAtMillis > 0
        ? triggerStartedAtMillis - inviteCreatedAtMillis
        : null,
      lookupMillis: pushSentAtMillis - triggerStartedAtMillis,
      fcmSendMillis: completedAtMillis - pushSentAtMillis,
      totalFunctionMillis: completedAtMillis - triggerStartedAtMillis
    }));
  } catch (error) {
    const errorCode = String(error && error.code || "messaging/unknown");
    console.error("Failed to send incoming-call notification", event.params.callId, error);
    const timestamp = admin.firestore.FieldValue.serverTimestamp();
    await applyServerTransition(event.data.ref, "notification_failed", {
      ...notificationFailedUpdate("answered", errorCode, timestamp),
    });
    return;
  }

  const timestamp = admin.firestore.FieldValue.serverTimestamp();
  await applyServerTransition(event.data.ref, "sent", {
    ...notificationSentUpdate("answered", timestamp),
  });
});

exports.sendCallStatusUpdate = onDocumentUpdated(
    "callInvites/{callId}",
    async (event) => {
      const before = event.data && event.data.before && event.data.before.data();
      const after = event.data && event.data.after && event.data.after.data();
      if (!before || !after || after.type !== "video_call") {
        return;
      }

      const pushedStatus = receiverStatusUpdate(
          String(before.status || ""),
          String(after.status || ""),
      );
      if (!pushedStatus) {
        return;
      }

      const firestore = admin.firestore();
      const calleePhoneNumber = String(
          after.calleePhoneNumberNormalized || "",
      ).trim();
      const inviteCalleeUid = String(after.calleeUid || "").trim();
      let calleeUid = inviteCalleeUid;
      if (!calleeUid && calleePhoneNumber) {
        const userDoc = await firestore
            .collection("users")
            .doc(calleePhoneNumber)
            .get();
        calleeUid = userDoc.exists ? String(userDoc.get("uid") || "") : "";
      }
      if (!calleeUid) {
        console.warn(
            "Could not send call-status push without a callee UID",
            event.params.callId,
        );
        return;
      }

      const deviceDoc = await firestore
          .collection("userDevices")
          .doc(calleeUid)
          .get();
      const fcmToken = deviceDoc.get("fcmToken");
      if (!fcmToken) {
        console.warn(
            "Could not send call-status push without an FCM token",
            event.params.callId,
        );
        return;
      }

      const callId = String(after.callId || event.params.callId);
      const callerName = String(after.callerName || "Unknown caller");
      const callerPhoneNumber = String(after.callerPhoneNumber || "");
      try {
        const messageId = await admin.messaging().send({
          token: fcmToken,
          android: {
            priority: "high",
            ttl: 120000,
          },
          data: {
            type: "call_status",
            status: pushedStatus,
            callerName,
            callerPhoneNumber,
            callerPhotoBase64: "",
            callId,
            statusChangedAtMillis: String(Date.now()),
          },
        });
        console.info("Call-status push sent", JSON.stringify({
          callId,
          status: pushedStatus,
          messageId,
        }));
      } catch (error) {
        console.error(
            "Failed to send call-status push",
            event.params.callId,
            error,
        );
      }
    },
);
