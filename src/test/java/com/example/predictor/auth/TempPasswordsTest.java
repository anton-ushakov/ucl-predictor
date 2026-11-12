package com.example.predictor.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TempPasswordsTest {

    @Test
    void temporaryPasswordIsLongEnoughForTheRules() {
        String password = TempPasswords.generate();
        assertEquals(10, password.length());
        assertTrue(password.length() >= 6, "пароль не короче 6 символов");
    }

    @Test
    void temporaryPasswordsDiffer() {
        assertNotEquals(TempPasswords.generate(), TempPasswords.generate());
    }
}
