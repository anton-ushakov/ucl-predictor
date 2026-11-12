package com.example.predictor.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenUtilTest {

    @Test
    void tokensAreDifferentAndLongEnough() {
        String a = TokenUtil.newToken();
        String b = TokenUtil.newToken();
        assertNotEquals(a, b);
        assertTrue(a.length() >= 43); // 32 байта в Base64 без заполнителей
    }

    @Test
    void hashHasKnownValueAndFixedLength() {
        // SHA-256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TokenUtil.sha256Hex("abc"));
        assertEquals(64, TokenUtil.sha256Hex(TokenUtil.newToken()).length());
    }
}
