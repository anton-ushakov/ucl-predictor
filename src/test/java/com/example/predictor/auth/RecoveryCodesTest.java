package com.example.predictor.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryCodesTest {

    @Test
    void generatedCodeLooksLikeFourFourFour() {
        String code = RecoveryCodes.generate();
        assertEquals(14, code.length());
        assertEquals('-', code.charAt(4));
        assertEquals('-', code.charAt(9));
        assertEquals(12, RecoveryCodes.normalize(code).length());
    }

    @Test
    void generatedCodesDiffer() {
        assertNotEquals(RecoveryCodes.generate(), RecoveryCodes.generate());
    }

    @Test
    void codeHasNoAmbiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            String code = RecoveryCodes.normalize(RecoveryCodes.generate());
            assertFalse(code.contains("I"), code);
            assertFalse(code.contains("O"), code);
            assertFalse(code.contains("0"), code);
            assertFalse(code.contains("1"), code);
        }
    }

    @Test
    void normalizeIgnoresCaseSpacesAndDashes() {
        assertEquals("K7M2QX9P4RTB", RecoveryCodes.normalize(" k7m2-qx9p 4rtb "));
        assertEquals("K7M2QX9P4RTB", RecoveryCodes.normalize("K7M2QX9P4RTB"));
    }

    @Test
    void normalizeDropsCharactersThatCannotBeInACode() {
        // в коде нет I, O, 0 и 1: такой код просто не совпадёт
        assertEquals("", RecoveryCodes.normalize("01IO"));
    }

    @Test
    void normalizeOfNothingIsEmpty() {
        assertEquals("", RecoveryCodes.normalize(null));
        assertEquals("", RecoveryCodes.normalize("  ???  "));
    }
}
