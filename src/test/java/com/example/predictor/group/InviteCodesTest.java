package com.example.predictor.group;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InviteCodesTest {

    @Test
    void codeHasExpectedLengthAndAlphabet() {
        for (int i = 0; i < 1000; i++) {
            String code = InviteCodes.generate();
            assertEquals(InviteCodes.LENGTH, code.length());
            for (char c : code.toCharArray()) {
                assertTrue(InviteCodes.ALPHABET.indexOf(c) >= 0, "неожиданный символ " + c);
            }
        }
    }

    @Test
    void codesDoNotContainAmbiguousCharacters() {
        for (char c : new char[]{'0', 'O', '1', 'I'}) {
            assertEquals(-1, InviteCodes.ALPHABET.indexOf(c));
        }
    }
}
