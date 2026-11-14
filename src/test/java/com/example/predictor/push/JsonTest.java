package com.example.predictor.push;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Разбор файла ключа Firebase и сборка сообщения. */
class JsonTest {

    @Test
    void fieldIsReadFromTheFile() {
        String file = """
                {
                  "type": "service_account",
                  "project_id": "shaurmaleague",
                  "client_email": "firebase-adminsdk@shaurmaleague.iam.gserviceaccount.com",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }""";
        assertEquals("shaurmaleague", Json.stringField(file, "project_id"));
        assertEquals("https://oauth2.googleapis.com/token", Json.stringField(file, "token_uri"));
        assertEquals("firebase-adminsdk@shaurmaleague.iam.gserviceaccount.com",
                Json.stringField(file, "client_email"));
    }

    @Test
    void lineBreaksInsideTheKeyAreRestored() {
        // в файле ключа переводы строк записаны как \n, и без разворачивания ключ не прочитается
        String file = "{\"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvQ==\\n-----END PRIVATE KEY-----\\n\"}";
        String key = Json.stringField(file, "private_key");
        assertEquals("-----BEGIN PRIVATE KEY-----\nMIIEvQ==\n-----END PRIVATE KEY-----\n", key);
    }

    @Test
    void missingFieldGivesNothing() {
        assertNull(Json.stringField("{\"a\":\"b\"}", "project_id"));
        assertNull(Json.stringField(null, "project_id"));
        assertNull(Json.stringField("{\"count\": 7}", "count"));
    }

    @Test
    void accessTokenIsReadFromTheAnswer() {
        String answer = "{\"access_token\":\"ya29.abc-def_123\",\"expires_in\":3599,\"token_type\":\"Bearer\"}";
        assertEquals("ya29.abc-def_123", Json.stringField(answer, "access_token"));
    }

    @Test
    void quotesAndBreaksAreEscaped() {
        assertEquals("Реал 2:1 Барса", Json.escape("Реал 2:1 Барса"));
        assertEquals("он сказал \\\"да\\\"", Json.escape("он сказал \"да\""));
        assertEquals("первая\\nвторая", Json.escape("первая\nвторая"));
        assertEquals("путь\\\\домой", Json.escape("путь\\домой"));
        assertEquals("", Json.escape(null));
    }

    @Test
    void escapedTextCanBeReadBack() {
        String text = "Иван: \"ну и как\"\nответ";
        String json = "{\"body\":\"" + Json.escape(text) + "\"}";
        assertEquals(text, Json.stringField(json, "body"));
    }

    @Test
    void onlyTheWholeNameMatches() {
        // «id» не должно подхватываться из «project_id»
        String file = "{\"project_id\":\"a\",\"id\":\"b\"}";
        assertEquals("b", Json.stringField(file, "id"));
    }
}
