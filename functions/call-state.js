"use strict";

const PENDING_STATUSES = new Set(["ringing", "sent", "delivered"]);
const TERMINAL_STATUSES = new Set([
  "declined",
  "missed",
  "ended",
  "notification_failed",
  "callee_not_registered",
  "callee_missing_uid",
  "callee_missing_fcm_token",
]);

const CALLEE_PENDING_STATUSES = new Set(["delivered", "answered", "declined", "missed"]);
const SERVER_RINGING_STATUSES = new Set([
  "sent",
  "notification_failed",
  "callee_not_registered",
  "callee_missing_uid",
  "callee_missing_fcm_token",
]);

function normalizeStatus(value) {
  return String(value || "").trim().toLowerCase();
}

function isPendingStatus(status) {
  return PENDING_STATUSES.has(normalizeStatus(status));
}

function isTerminalStatus(status) {
  return TERMINAL_STATUSES.has(normalizeStatus(status));
}

function resolveCallTransition(currentValue, requestedValue, actorRole) {
  const currentStatus = normalizeStatus(currentValue);
  const requestedStatus = normalizeStatus(requestedValue);
  const role = String(actorRole || "").trim().toLowerCase();

  if (!currentStatus || !requestedStatus) {
    return {allowed: false, idempotent: false, reason: "missing_status"};
  }
  if (currentStatus === requestedStatus) {
    return {allowed: true, idempotent: true, status: currentStatus};
  }
  if (isTerminalStatus(currentStatus)) {
    return {allowed: false, idempotent: false, reason: "terminal_state"};
  }

  if (role === "caller") {
    if (isPendingStatus(currentStatus) && requestedStatus === "missed") {
      return {allowed: true, idempotent: false, status: requestedStatus};
    }
    if (currentStatus === "answered" && requestedStatus === "ended") {
      return {allowed: true, idempotent: false, status: requestedStatus};
    }
  }

  if (role === "callee") {
    if (isPendingStatus(currentStatus) && CALLEE_PENDING_STATUSES.has(requestedStatus)) {
      return {allowed: true, idempotent: false, status: requestedStatus};
    }
    if (currentStatus === "answered" && requestedStatus === "ended") {
      return {allowed: true, idempotent: false, status: requestedStatus};
    }
  }

  if (role === "server") {
    if (currentStatus === "ringing" && SERVER_RINGING_STATUSES.has(requestedStatus)) {
      return {allowed: true, idempotent: false, status: requestedStatus};
    }
    if (isPendingStatus(currentStatus) && requestedStatus === "missed") {
      return {allowed: true, idempotent: false, status: requestedStatus};
    }
  }

  return {allowed: false, idempotent: false, reason: "invalid_transition"};
}

module.exports = {
  isPendingStatus,
  isTerminalStatus,
  normalizeStatus,
  resolveCallTransition,
};
