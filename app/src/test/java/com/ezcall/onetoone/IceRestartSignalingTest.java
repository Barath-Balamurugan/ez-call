package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IceRestartSignalingTest {
    @Test
    public void missingGenerationIsCompatibleWithInitialNegotiation() {
        assertEquals(1, IceRestartSignaling.generation(null));
        assertEquals(1, IceRestartSignaling.generation("not-a-number"));
    }

    @Test
    public void offerIsHandledOncePerGeneration() {
        assertTrue(IceRestartSignaling.shouldHandleOffer(2, 1, 0));
        assertFalse(IceRestartSignaling.shouldHandleOffer(2, 2, 0));
        assertFalse(IceRestartSignaling.shouldHandleOffer(2, 1, 2));
    }

    @Test
    public void answerMustMatchCurrentLocalOfferGeneration() {
        assertTrue(IceRestartSignaling.shouldHandleAnswer(3, 3, 2, 0));
        assertFalse(IceRestartSignaling.shouldHandleAnswer(2, 3, 1, 0));
        assertFalse(IceRestartSignaling.shouldHandleAnswer(3, 3, 3, 0));
    }

    @Test
    public void candidatesOnlyApplyToMatchingRemoteDescription() {
        assertTrue(IceRestartSignaling.candidateMatches(4, 4));
        assertFalse(IceRestartSignaling.candidateMatches(3, 4));
    }
}
