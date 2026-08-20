package com.ezcall.onetoone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CallEndStatusTest {
    @Test
    public void outgoingCancellationBeforeAnswerIsMissed() {
        assertEquals("missed", CallEndStatus.forLocalExit(false, false));
    }

    @Test
    public void incomingDismissalBeforeAnswerIsDeclined() {
        assertEquals("declined", CallEndStatus.forLocalExit(true, false));
    }

    @Test
    public void connectedCallAlwaysEndsNormally() {
        assertEquals("ended", CallEndStatus.forLocalExit(false, true));
        assertEquals("ended", CallEndStatus.forLocalExit(true, true));
    }
}
