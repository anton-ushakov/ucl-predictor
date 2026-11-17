package com.example.predictor.release;

import com.example.predictor.auth.AuthService;
import com.example.predictor.domain.AppUser;
import com.example.predictor.release.ReleaseService.PublishResult;
import com.example.predictor.release.ReleaseService.ReleaseDto;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

/** Новая версия приложения: сведения, скачивание и выкладывание. */
@RestController
public class ReleaseController {

    private static final MediaType APK = MediaType.parseMediaType("application/vnd.android.package-archive");

    private final AuthService auth;
    private final ReleaseService releases;
    private final DownloadLinks links;

    public ReleaseController(AuthService auth, ReleaseService releases, DownloadLinks links) {
        this.auth = auth;
        this.releases = releases;
        this.links = links;
    }

    /** Ссылка на скачивание. Приложение дописывает к ключу свой адрес сервера. */
    @GetMapping("/api/app/link")
    public DownloadLinks.Link link(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.authenticate(authorization);
        // убеждаемся, что отдавать есть что: ссылка на пустоту никому не нужна
        releases.latestFile();
        return links.issue();
    }

    /**
     * Скачивание по ссылке: без входа, но только с живым ключом. Сюда приходит браузер,
     * у которого нет ни пароля, ни заголовков приложения.
     */
    @GetMapping("/app/get/{token}")
    public ResponseEntity<Resource> openDownload(@PathVariable String token) {
        if (!links.valid(token)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ссылка устарела. Возьмите новую в приложении.");
        }
        Path file = releases.latestFile();
        return ResponseEntity.ok()
                .contentType(APK)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + releases.latestFileName() + "\"")
                .body(new FileSystemResource(file));
    }

    /** Что лежит на сервере: приложение сравнивает это со своей версией. */
    @GetMapping("/api/app/latest")
    public ReleaseDto latest(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.authenticate(authorization);
        return releases.latest();
    }

    /** Сам файл. Приложение скачивает его и предлагает установить. */
    @GetMapping("/api/app/download")
    public ResponseEntity<Resource> download(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.authenticate(authorization);
        Path file = releases.latestFile();
        return ResponseEntity.ok()
                .contentType(APK)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + releases.latestFileName() + "\"")
                .body(new FileSystemResource(file));
    }

    /** Выложить новую версию. Только владелец приложения. */
    @PostMapping(value = "/api/admin/app", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PublishResult publish(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestPart("file") MultipartFile file,
            @RequestParam("versionCode") int versionCode,
            @RequestParam("versionName") String versionName,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "link", required = false) String link,
            @RequestParam(value = "announce", defaultValue = "true") boolean announce) {
        AppUser user = auth.authenticate(authorization);
        return releases.publish(user, file, versionCode, versionName, note, link, announce);
    }
}
