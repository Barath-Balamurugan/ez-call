"use strict";

const PRE_ANSWER_STATUSES = new Set(["ringing", "sent", "delivered"]);

function canSendIncomingInvite(currentStatus) {
  return currentStatus === "ringing";
}

function receiverStatusUpdate(previousStatus, currentStatus) {
  if (previousStatus === currentStatus) {
    return null;
  }
  if (currentStatus === "missed") {
    return "missed";
  }
  if (currentStatus === "ended" && PRE_ANSWER_STATUSES.has(previousStatus)) {
    return "missed";
  }
  return null;
}

function notificationSentUpdate(currentStatus, timestamp) {
  const update = {
    notificationStatus: "sent",
    sentAt: timestamp,
  };
  if (currentStatus === "ringing") {
    update.status = "sent";
  }
  return update;
}

function notificationFailedUpdate(currentStatus, errorCode, timestamp) {
  const update = {
    notificationStatus: "failed",
    notificationErrorCode: errorCode,
    notificationAttemptedAt: timestamp,
  };
  if (currentStatus === "ringing") {
    update.status = "notification_failed";
  }
  return update;
}

module.exports = {
  canSendIncomingInvite,
  notificationFailedUpdate,
  notificationSentUpdate,
  receiverStatusUpdate,
};
