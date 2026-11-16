package com.example.predictor.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Проверка, что на сервер пришла именно сборка приложения, а не случайный файл. */
class ApkCheckTest {

    @Test
    void apkStartsWithZipSignature() {
        assertTrue(ReleaseService.looksLikeApk(new byte[]{0x50, 0x4B, 0x03, 0x04}));
    }

    @Test
    void textFileIsRejected() {
        assertFalse(ReleaseService.looksLikeApk("прив".getBytes()));
    }

    @Test
    void shortFileIsRejected() {
        assertFalse(ReleaseService.looksLikeApk(new byte[]{0x50, 0x4B}));
    }
}
