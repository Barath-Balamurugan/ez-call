package com.ezcall.onetoone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneProfileOwnershipTest {
    @Test
    public void detectsNumberOwnedByAnotherUser() {
        assertTrue(PhoneProfileOwnership.belongsToDifferentUser(
                true,
                "user-1",
                "user-2"
        ));
    }

    @Test
    public void permitsExistingNumberOwnedByCurrentUser() {
        assertFalse(PhoneProfileOwnership.belongsToDifferentUser(
                true,
                "user-1",
                "user-1"
        ));
    }

    @Test
    public void permitsUnclaimedNumber() {
        assertFalse(PhoneProfileOwnership.belongsToDifferentUser(
                false,
                "",
                "user-1"
        ));
    }

    @Test
    public void treatsOwnerlessExistingProfileAsClaimed() {
        assertTrue(PhoneProfileOwnership.belongsToDifferentUser(
                true,
                "",
                "user-1"
        ));
    }

    @Test
    public void duplicateErrorUsesRegistrationMessage() {
        assertEquals(
                "Already registered phone number.",
                PhoneProfileOwnership.alreadyRegisteredError().getMessage()
        );
    }
}
