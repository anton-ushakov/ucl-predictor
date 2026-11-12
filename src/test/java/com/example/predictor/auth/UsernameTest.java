package com.example.predictor.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsernameTest {

    @Test
    void spacesAroundTheNameAreIgnored() {
        assertEquals("ivan", AuthService.normalizeUsername("  ivan  "));
        assertEquals("ivan", AuthService.normalizeUsername("ivan"));
    }

    @Test
    void caseIsKeptAsTyped() {
        // написание сохраняется, а уникальность обеспечивает индекс по lower(username)
        assertEquals("Ivan", AuthService.normalizeUsername("Ivan"));
        assertNotEquals(AuthService.normalizeUsername("Ivan"), AuthService.normalizeUsername("ivan"));
    }

    @Test
    void emptyInputIsSafe() {
        assertEquals("", AuthService.normalizeUsername(null));
        assertEquals("", AuthService.normalizeUsername("   "));
    }
}
