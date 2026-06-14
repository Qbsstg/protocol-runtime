package io.github.qbsstg.protocol.runtime.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record CollectorDeploymentConfig(
        String profile,
        Path runtimeDir,
        Path confDir,
        Path logsDir,
        Path dataDir,
        Path runDir,
        Path tmpDir,
        Path pidFile,
        Path statusFile,
        Path logFile,
        boolean createDirectories) {

    public static final String DEFAULT_PROFILE = "local";

    public CollectorDeploymentConfig {
        profile = normalizeProfile(profile);
        runtimeDir = normalizePath(Objects.requireNonNull(runtimeDir, "runtimeDir must not be null"));
        confDir = normalizePath(Objects.requireNonNull(confDir, "confDir must not be null"));
        logsDir = normalizePath(Objects.requireNonNull(logsDir, "logsDir must not be null"));
        dataDir = normalizePath(Objects.requireNonNull(dataDir, "dataDir must not be null"));
        runDir = normalizePath(Objects.requireNonNull(runDir, "runDir must not be null"));
        tmpDir = normalizePath(Objects.requireNonNull(tmpDir, "tmpDir must not be null"));
        pidFile = normalizeOptionalPath(pidFile);
        statusFile = normalizeOptionalPath(statusFile);
        logFile = normalizeOptionalPath(logFile);
    }

    public static CollectorDeploymentConfig defaults() {
        Path runtimeDir = Path.of(".").normalize();
        return new CollectorDeploymentConfig(
                DEFAULT_PROFILE,
                runtimeDir,
                runtimeDir.resolve("conf"),
                runtimeDir.resolve("logs"),
                runtimeDir.resolve("data"),
                runtimeDir.resolve("run"),
                runtimeDir.resolve("tmp"),
                null,
                null,
                runtimeDir.resolve("logs").resolve("protocol-runtime.log"),
                false);
    }

    public void prepareDirectories() throws IOException {
        if (!createDirectories) {
            return;
        }
        Files.createDirectories(confDir);
        Files.createDirectories(logsDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(runDir);
        Files.createDirectories(tmpDir);
        createParentDirectory(pidFile);
        createParentDirectory(statusFile);
        createParentDirectory(logFile);
    }

    private static void createParentDirectory(Path path) throws IOException {
        if (path == null) {
            return;
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    static boolean isValidProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return false;
        }
        for (int i = 0; i < profile.length(); i++) {
            char c = profile.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeProfile(String profile) {
        if (!isValidProfile(profile)) {
            throw new IllegalArgumentException("profile must contain only letters, digits, dash, underscore, or dot");
        }
        return profile.trim();
    }

    private static Path normalizePath(Path path) {
        return path.normalize();
    }

    private static Path normalizeOptionalPath(Path path) {
        return path == null ? null : normalizePath(path);
    }
}
