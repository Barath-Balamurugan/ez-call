"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  isPendingStatus,
  isTerminalStatus,
  resolveCallTransition,
} = require("../call-state");

test("caller can cancel pending calls but cannot answer them", () => {
  assert.deepEqual(resolveCallTransition("ringing", "missed", "caller"), {
    allowed: true,
    idempotent: false,
    status: "missed",
  });
  assert.equal(resolveCallTransition("delivered", "answered", "caller").allowed, false);
});

test("callee controls delivery, answer, and decline before connection", () => {
  for (const requested of ["delivered", "answered", "declined", "missed"]) {
    assert.equal(resolveCallTransition("sent", requested, "callee").allowed, true);
  }
  assert.equal(resolveCallTransition("ringing", "ended", "callee").allowed, false);
});

test("either participant can end only an answered call", () => {
  assert.equal(resolveCallTransition("answered", "ended", "caller").allowed, true);
  assert.equal(resolveCallTransition("answered", "ended", "callee").allowed, true);
  assert.equal(resolveCallTransition("delivered", "ended", "caller").allowed, false);
});

test("terminal states are immutable and repeated requests are idempotent", () => {
  assert.equal(resolveCallTransition("missed", "answered", "callee").allowed, false);
  assert.equal(resolveCallTransition("declined", "ended", "caller").allowed, false);
  assert.deepEqual(resolveCallTransition("ended", "ended", "callee"), {
    allowed: true,
    idempotent: true,
    status: "ended",
  });
});

test("server controls notification states and unanswered expiry", () => {
  assert.equal(resolveCallTransition("ringing", "sent", "server").allowed, true);
  assert.equal(resolveCallTransition("ringing", "notification_failed", "server").allowed, true);
  assert.equal(resolveCallTransition("delivered", "missed", "server").allowed, true);
  assert.equal(resolveCallTransition("sent", "answered", "server").allowed, false);
});

test("status classifiers distinguish pending and terminal states", () => {
  assert.equal(isPendingStatus("delivered"), true);
  assert.equal(isPendingStatus("answered"), false);
  assert.equal(isTerminalStatus("notification_failed"), true);
  assert.equal(isTerminalStatus("answered"), false);
});
