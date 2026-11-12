package com.example.predictor.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PinsTest {

    @Test
    void normalPinsAreAccepted() {
        assertNull(Pins.validate("4071"));
        assertNull(Pins.validate("920385"));
        assertNull(Pins.validate("13572468"));
    }

    @Test
    void lengthIsChecked() {
        assertNotNull(Pins.validate("071"));
        assertNotNull(Pins.validate("123456789"));
        assertTrue(Pins.validate("071").contains("4"));
    }

    @Test
    void onlyDigitsAreAllowed() {
        assertTrue(Pins.validate("40a1").contains("цифр"));
        assertTrue(Pins.validate("40 1").contains("цифр"));
    }

    @Test
    void emptyPinIsRejected() {
        assertNotNull(Pins.validate(null));
        assertNotNull(Pins.validate("   "));
    }

    @Test
    void repeatedDigitsAreRejected() {
        assertTrue(Pins.validate("1111").contains("одинаковые"));
        assertTrue(Pins.validate("000000").contains("одинаковые"));
    }

    @Test
    void straightSequencesAreRejected() {
        assertTrue(Pins.validate("1234").contains("подряд"));
        assertTrue(Pins.validate("4321").contains("подряд"));
        assertTrue(Pins.validate("456789").contains("подряд"));
    }

    @Test
    void almostSequencesAreFine() {
        assertNull(Pins.validate("1235"));
        assertNull(Pins.validate("1243"));
    }

    @Test
    void spacesAroundThePinAreIgnored() {
        assertEquals("4071", Pins.normalize("  4071 "));
        assertNull(Pins.validate(" 4071 "));
    }
}
