package dev.chatmoderation.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModerationResultTest {

    @Test
    void allowResult() {
        ModerationResult result = ModerationResult.allow("hello");

        assertTrue(result.allowed());
        assertEquals(ModerationAction.ALLOW, result.action());
        assertEquals("hello", result.outputMessage());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void blockResult() {
        ModerationResult result =
                ModerationResult.block(List.of(ModerationReason.PROFANITY));

        assertFalse(result.allowed());
        assertEquals(ModerationAction.BLOCK, result.action());
        assertNull(result.outputMessage());
        assertEquals(List.of(ModerationReason.PROFANITY), result.reasons());
    }
}
