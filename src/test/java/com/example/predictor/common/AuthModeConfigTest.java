package com.example.predictor.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class AuthModeConfigTest {

    @Test
    void simpleModeTurnsPasswordsOffAndRequestsOn() {
        AuthModeConfig config = new AuthModeConfig(AuthMode.SIMPLE);
        assertFalse(config.passwordsEnabled());
        assertTrue(config.requestsEnabled());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, config::requirePasswords);
        assertEquals(409, e.getStatusCode().value());
        assertThrows(ResponseStatusException.class, config::requireInviteCodes);
        config.requireRequests(); // не бросает
    }

    @Test
    void passwordModeTurnsRequestsOffAndPasswordsOn() {
        AuthModeConfig config = new AuthModeConfig(AuthMode.PASSWORD);
        assertTrue(config.passwordsEnabled());
        assertFalse(config.requestsEnabled());

        config.requirePasswords();
        config.requireInviteCodes();
        ResponseStatusException e = assertThrows(ResponseStatusException.class, config::requireRequests);
        assertEquals(409, e.getStatusCode().value());
    }

    @Test
    void messagesExplainWhatToDoInstead() {
        AuthModeConfig simple = new AuthModeConfig(AuthMode.SIMPLE);
        String byCode = assertThrows(ResponseStatusException.class, simple::requireInviteCodes).getMessage();
        assertTrue(byCode.contains("заявке"), byCode);
    }
}
