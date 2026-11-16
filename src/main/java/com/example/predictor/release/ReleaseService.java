package com.example.predictor.release;

import com.example.predictor.common.AdminAccess;
import com.example.predictor.domain.AppRelease;
import com.example.predictor.domain.AppUser;
import com.example.predictor.notify.AnnouncementService;
import com.example.predictor.repository.AppReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

/**
 * Раздача новых версий приложения. Владелец выкладывает apk прямо из приложения, сервер хранит
 * файл у себя и отдаёт его остальным. Раньше для этого была ссылка на чужой файлообменник —
 * здесь надёжнее: тот же адрес, тот же вход, и приложение может скачать файл само.
 */
@Service
public class ReleaseService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);

    /** Больше этого apk не бывает: защита от случайной загрузки чего-то не того. */
    private static final long MAX_SIZE = 200L * 1024 * 1024;

    /** Любой apk — это zip, а zip всегда начинается с этих четырёх байт. */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** Сведения о последней версии для экрана обновления. */
    public record ReleaseDto(boolean available, int versionCode, String versionName, String note,
                             long sizeBytes, Instant publishedAt) {

        static ReleaseDto none() {
            return new ReleaseDto(false, 0, "", null, 0, null);
        }
    }

    public record PublishResult(int versionCode, String versionName, long sizeBytes, String message) {
    }

    private final AdminAccess admin;
    private final AppReleaseRepository releases;
    private final AnnouncementService announcements;
    private final Path dir;

    public ReleaseService(AdminAccess admin, AppReleaseRepository releases,
                          AnnouncementService announcements,
                          @Value("${app.releases.dir:/app/releases}") String dir) {
        this.admin = admin;
        this.releases = releases;
        this.announcements = announcements;
        this.dir = Path.of(dir);
    }

    @Transactional(readOnly = true)
    public ReleaseDto latest() {
        return releases.findTopByOrderByVersionCodeDesc()
                .map(r -> new ReleaseDto(true, r.getVersionCode(), r.getVersionName(), r.getNote(),
                        r.getSizeBytes(), r.getCreatedAt()))
                .orElseGet(ReleaseDto::none);
    }

    /** Файл последней версии. */
    @Transactional(readOnly = true)
    public Path latestFile() {
        AppRelease release = releases.findTopByOrderByVersionCodeDesc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Новая версия ещё не выложена"));
        Path file = dir.resolve(release.getFileName());
        if (!Files.isReadable(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Файл версии потерялся на сервере, выложите его заново");
        }
        return file;
    }

    @Transactional(readOnly = true)
    public String latestFileName() {
        return releases.findTopByOrderByVersionCodeDesc()
                .map(r -> "shaurma-liga-" + r.getVersionName() + ".apk")
                .orElse("shaurma-liga.apk");
    }

    /**
     * Выложить новую версию. Номер версии приложение читает из самого apk, поэтому ошибиться
     * в нём нельзя. Та же версия, выложенная заново, заменяет файл и никого не беспокоит.
     */
    @Transactional
    public PublishResult publish(AppUser user, MultipartFile file, int versionCode,
                                 String versionName, String note, String link, boolean announce) {
        admin.require(user);
        if (versionCode <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Непонятный номер версии");
        }
        String name = versionName == null ? "" : versionName.trim();
        if (name.isBlank() || name.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите название версии");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл не выбран");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл слишком большой");
        }
        checkApk(file);

        Optional<AppRelease> newer = releases.findTopByOrderByVersionCodeDesc()
                .filter(r -> r.getVersionCode() > versionCode);
        if (newer.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "На сервере уже лежит версия " + newer.get().getVersionName()
                            + ": поднимите versionCode в сборке");
        }

        String fileName = "shaurma-" + versionCode + ".apk";
        Instant now = Instant.now();
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Не удалось сохранить apk", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Сервер не смог сохранить файл: " + e.getMessage());
        }

        String cleanNote = note == null || note.isBlank() ? null
                : note.trim().substring(0, Math.min(note.trim().length(), 400));
        AppRelease release = releases.findByVersionCode(versionCode).orElse(null);
        if (release == null) {
            release = new AppRelease(versionCode, name, cleanNote, fileName, file.getSize(), now,
                    user.getId());
        } else {
            release.replace(name, cleanNote, fileName, file.getSize(), now, user.getId());
        }
        releases.save(release);
        log.info("Выложена версия {} ({}), {} КБ", name, versionCode, file.getSize() / 1024);

        String message = "Версия " + name + " выложена";
        if (announce) {
            AnnouncementService.Result sent = announcements.announceRelease(user, name, cleanNote, link);
            message = message + ". " + sent.message();
        }
        return new PublishResult(versionCode, name, file.getSize(), message);
    }

    private static void checkApk(MultipartFile file) {
        byte[] head = new byte[ZIP_MAGIC.length];
        try (InputStream in = file.getInputStream()) {
            if (in.read(head) != head.length) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл пустой");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл не читается");
        }
        if (!looksLikeApk(head)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Это не apk: выберите файл сборки приложения");
        }
    }

    /** Любой apk — это zip, поэтому первые четыре байта у него всегда одни и те же. */
    static boolean looksLikeApk(byte[] head) {
        if (head == null || head.length < ZIP_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (head[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
