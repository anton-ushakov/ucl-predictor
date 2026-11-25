package com.example.predictor.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.predictor.domain.AppUser;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Владелец приложения: по имени из настроек или по номеру участника. */
class AdminAccessTest {

    /** Номер участника присваивает база, поэтому в тесте ставим его сами. */
    private static AppUser user(Long id, String username) {
        AppUser user = new AppUser(username, "хеш");
        try {
            Field field = AppUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return user;
    }

    @Test
    void nameFromSettingsMakesAdmin() {
        AdminAccess access = new AdminAccess("Антон, анна");

        assertTrue(access.isAdmin(user(1L, "антон")));
        assertTrue(access.isAdmin(user(2L, "АННА")));
        assertFalse(access.isAdmin(user(3L, "миша")));
        assertTrue(access.configured());
    }

    @Test
    void numberSurvivesRenaming() {
        AdminAccess access = new AdminAccess("id:3");

        // имя поменялось, номер остался — права на месте
        assertTrue(access.isAdmin(user(3L, "антон")));
        assertTrue(access.isAdmin(user(3L, "тренер")));
        assertFalse(access.isAdmin(user(4L, "антон")));
        assertEquals(Set.of(3L), access.ids());
        assertTrue(access.names().isEmpty());
    }

    @Test
    void namesAndNumbersTogether() {
        AdminAccess access = new AdminAccess("id:3, анна");

        assertTrue(access.isAdmin(user(3L, "кто угодно")));
        assertTrue(access.isAdmin(user(9L, "анна")));
        assertFalse(access.isAdmin(user(9L, "миша")));
    }

    @Test
    void brokenNumberIsTreatedAsName() {
        AdminAccess access = new AdminAccess("id:");

        assertFalse(access.isAdmin(user(1L, "антон")));
        assertTrue(access.ids().isEmpty());
    }

    @Test
    void emptySettingsMeanNoAdmins() {
        AdminAccess access = new AdminAccess("  ");

        assertFalse(access.configured());
        assertFalse(access.isAdmin(user(1L, "антон")));
        assertFalse(access.isAdmin(null));
    }
}
