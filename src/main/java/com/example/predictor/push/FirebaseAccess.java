package com.example.predictor.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Доступ к Firebase по ключу служебной учётной записи.
 *
 * Готовой библиотеки Firebase здесь намеренно нет: она тянет за собой десятки мегабайт
 * зависимостей, а на домашнем сервере памяти немного. Нужно всего одно: подписать ключом
 * короткий пропуск и обменять его на временный токен доступа. Токен живёт час,
 * поэтому держим его в памяти и обновляем заранее.
 *
 * Файл с ключом в проект не попадает: его кладут рядом с .env и указывают путь
 * переменной окружения FIREBASE_CREDENTIALS.
 */
@Component
public class FirebaseAccess {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAccess.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final RestClient http = RestClient.create();

    private final String projectId;
    private final String clientEmail;
    private final String tokenUri;
    private final PrivateKey privateKey;

    private volatile String accessToken;
    private volatile Instant accessTokenUntil = Instant.EPOCH;

    public FirebaseAccess(
            @Value("${app.push.credentials:${FIREBASE_CREDENTIALS:}}") String credentialsPath,
            @Value("${app.push.project-id:${FIREBASE_PROJECT_ID:}}") String configuredProjectId) {
        String id = null;
        String email = null;
        String uri = "https://oauth2.googleapis.com/token";
        PrivateKey key = null;

        if (credentialsPath != null && !credentialsPath.isBlank()) {
            Path path = Path.of(credentialsPath.trim());
            try {
                String file = Files.readString(path, StandardCharsets.UTF_8);
                id = Json.stringField(file, "project_id");
                email = Json.stringField(file, "client_email");
                String rawKey = Json.stringField(file, "private_key");
                String maybeUri = Json.stringField(file, "token_uri");
                if (maybeUri != null && !maybeUri.isBlank()) {
                    uri = maybeUri;
                }
                key = readKey(rawKey);
                log.info("Firebase: ключ прочитан, проект {}", id);
            } catch (Exception e) {
                log.warn("Firebase: не удалось прочитать ключ {}: {}", path, e.getMessage());
            }
        }
        if (configuredProjectId != null && !configuredProjectId.isBlank()) {
            id = configuredProjectId.trim();
        }

        this.projectId = id;
        this.clientEmail = email;
        this.tokenUri = uri;
        this.privateKey = key;

        if (!isConfigured()) {
            log.info("Firebase не настроен: мгновенные оповещения выключены, "
                    + "приложение будет забирать их опросом");
        }
    }

    public boolean isConfigured() {
        return projectId != null && clientEmail != null && privateKey != null;
    }

    public String projectId() {
        return projectId;
    }

    /** Токен доступа к Firebase. Обновляется сам, когда срок подходит к концу. */
    public synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(accessTokenUntil)) {
            return accessToken;
        }
        Instant now = Instant.now();
        long issued = now.getEpochSecond();
        long expires = issued + 3600;

        String header = base64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String claims = base64("{"
                + "\"iss\":\"" + Json.escape(clientEmail) + "\","
                + "\"scope\":\"" + SCOPE + "\","
                + "\"aud\":\"" + Json.escape(tokenUri) + "\","
                + "\"iat\":" + issued + ","
                + "\"exp\":" + expires
                + "}");
        String signed = header + "." + claims;
        String assertion = signed + "." + sign(signed);

        String response = http.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + assertion)
                .retrieve()
                .body(String.class);

        String token = Json.stringField(response, "access_token");
        if (token == null) {
            throw new IllegalStateException("Firebase не выдал токен доступа");
        }
        accessToken = token;
        // обновляем за пять минут до конца, чтобы не попасть на просроченный
        accessTokenUntil = now.plusSeconds(3600 - 300);
        return accessToken;
    }

    private String sign(String data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось подписать пропуск для Firebase", e);
        }
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Ключ в файле лежит в текстовом виде PEM: убираем обрамление и переводы строк. */
    private static PrivateKey readKey(String pem) throws Exception {
        if (pem == null) {
            return null;
        }
        String body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(body);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }
}
