package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.telecom.DisconnectCause;

import org.junit.Test;

public class TelecomDisconnectPolicyTest {
    @Test
    public void terminalInviteStatusesDisconnectTelecom() {
        assertTrue(TelecomDisconnectPolicy.shouldDisconnectForInviteStatus("declined"));
        assertTrue(TelecomDisconnectPolicy.shouldDisconnectForInviteStatus("missed"));
        assertTrue(TelecomDisconnectPolicy.shouldDisconnectForInviteStatus("ended"));
        assertTrue(TelecomDisconnectPolicy.shouldDisconnectForInviteStatus("notification_failed"));
        assertFalse(TelecomDisconnectPolicy.shouldDisconnectForInviteStatus("answered"));
        assertFalse(TelecomDisconnectPolicy.shouldDisconnectForInviteStatus("delivered"));
    }

    @Test
    public void inviteStatusesMapToSystemDisconnectCauses() {
        assertEquals(
                DisconnectCause.REJECTED,
                TelecomDisconnectPolicy.causeForInviteStatus("declined", DisconnectCause.LOCAL)
        );
        assertEquals(
                DisconnectCause.MISSED,
                TelecomDisconnectPolicy.causeForInviteStatus("missed", DisconnectCause.LOCAL)
        );
        assertEquals(
                DisconnectCause.ERROR,
                TelecomDisconnectPolicy.causeForInviteStatus(
                        "notification_failed",
                        DisconnectCause.LOCAL
                )
        );
        assertEquals(
                DisconnectCause.REMOTE,
                TelecomDisconnectPolicy.causeForInviteStatus("ended", DisconnectCause.REMOTE)
        );
    }

    @Test
    public void systemDisconnectMapsBackToSharedInviteStatus() {
        assertEquals(
                "declined",
                TelecomDisconnectPolicy.inviteStatusForSystemDisconnect(
                        DisconnectCause.REJECTED,
                        true,
                        false
                )
        );
        assertEquals(
                "missed",
                TelecomDisconnectPolicy.inviteStatusForSystemDisconnect(
                        DisconnectCause.MISSED,
                        false,
                        false
                )
        );
        assertEquals(
                "ended",
                TelecomDisconnectPolicy.inviteStatusForSystemDisconnect(
                        DisconnectCause.LOCAL,
                        true,
                        true
                )
        );
    }
}
