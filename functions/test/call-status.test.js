"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  canSendIncomingInvite,
  notificationFailedUpdate,
  notificationSentUpdate,
  receiverStatusUpdate,
} = require("../call-status");

test("incoming push is only sent while an invite is still ringing", () => {
  assert.equal(canSendIncomingInvite("ringing"), true);
  assert.equal(canSendIncomingInvite("missed"), false);
  assert.equal(canSendIncomingInvite("ended"), false);
});

test("pre-answer cancellation becomes a receiver missed-call update", () => {
  assert.equal(receiverStatusUpdate("ringing", "missed"), "missed");
  assert.equal(receiverStatusUpdate("sent", "missed"), "missed");
  assert.equal(receiverStatusUpdate("delivered", "missed"), "missed");
});

test("legacy pre-answer ended status is normalized to missed", () => {
  assert.equal(receiverStatusUpdate("ringing", "ended"), "missed");
  assert.equal(receiverStatusUpdate("delivered", "ended"), "missed");
  assert.equal(receiverStatusUpdate("answered", "ended"), null);
});

test("non-terminal and repeated status changes do not send receiver updates", () => {
  assert.equal(receiverStatusUpdate("ringing", "sent"), null);
  assert.equal(receiverStatusUpdate("missed", "missed"), null);
  assert.equal(receiverStatusUpdate("ringing", "declined"), null);
});

test("notification sent advances a ringing invite", () => {
  assert.deepEqual(notificationSentUpdate("ringing", "timestamp"), {
    notificationStatus: "sent",
    sentAt: "timestamp",
    status: "sent",
  });
});

test("notification sent preserves delivered or terminal invite states", () => {
  assert.deepEqual(notificationSentUpdate("delivered", "timestamp"), {
    notificationStatus: "sent",
    sentAt: "timestamp",
  });
  assert.deepEqual(notificationSentUpdate("answered", "timestamp"), {
    notificationStatus: "sent",
    sentAt: "timestamp",
  });
  assert.deepEqual(notificationSentUpdate("declined", "timestamp"), {
    notificationStatus: "sent",
    sentAt: "timestamp",
  });
  assert.deepEqual(notificationSentUpdate("ended", "timestamp"), {
    notificationStatus: "sent",
    sentAt: "timestamp",
  });
});

test("notification failure preserves a terminal invite", () => {
  assert.deepEqual(notificationFailedUpdate("declined", "fcm/error", "timestamp"), {
    notificationStatus: "failed",
    notificationErrorCode: "fcm/error",
    notificationAttemptedAt: "timestamp",
  });
});

test("notification failure reports an unreachable ringing callee", () => {
  assert.deepEqual(notificationFailedUpdate("ringing", "fcm/error", "timestamp"), {
    notificationStatus: "failed",
    notificationErrorCode: "fcm/error",
    notificationAttemptedAt: "timestamp",
    status: "notification_failed",
  });
});
