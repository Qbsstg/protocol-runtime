package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

final class FailedRecordIsolation {

    private final SinkFailureIsolationConfig config;
    private final AtomicLong sampleCount = new AtomicLong();
    private final AtomicLong isolationFailureCount = new AtomicLong();
    private volatile Path lastSampleFile;
    private volatile Instant lastSampleAt;
    private volatile SinkDeliveryFailure lastFailure;
    private volatile String lastIsolationFailureMessage;

    FailedRecordIsolation(SinkFailureIsolationConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    void isolate(ParsedRecord<?> record, SinkDeliveryFailure failure) {
        if (!config.enabled() || config.maxSamples() == 0) {
            return;
        }
        write(RuntimeRecordFormat.formatFailedRecord(record, failure), failure);
    }

    void isolate(ParseFailure parseFailure, SinkDeliveryFailure failure) {
        if (!config.enabled() || config.maxSamples() == 0) {
            return;
        }
        write(RuntimeRecordFormat.formatFailedParseFailure(parseFailure, failure), failure);
    }

    FailedRecordIsolationStatus status() {
        SinkDeliveryFailure failure = lastFailure;
        return new FailedRecordIsolationStatus(
                config.enabled(),
                config.directory(),
                config.maxSamples(),
                sampleCount.get(),
                retainedSampleCount(),
                lastSampleFile,
                lastSampleAt,
                failure == null ? null : failure.target(),
                failure == null ? null : failure.sourceId(),
                failure == null ? null : failure.failureType().name(),
                failure == null ? null : failure.message(),
                isolationFailureCount.get(),
                lastIsolationFailureMessage);
    }

    private synchronized void write(String sample, SinkDeliveryFailure failure) {
        try {
            Files.createDirectories(config.directory());
            Path sampleFile = config.directory().resolve(sampleFileName(failure));
            Files.writeString(sampleFile, sample + System.lineSeparator(), StandardCharsets.UTF_8);
            sampleCount.incrementAndGet();
            lastSampleFile = sampleFile;
            lastSampleAt = Instant.now();
            lastFailure = failure;
            lastIsolationFailureMessage = null;
            retainMaxSamples();
        } catch (RuntimeException | IOException ex) {
            isolationFailureCount.incrementAndGet();
            lastFailure = failure;
            lastIsolationFailureMessage = ex.getClass().getName() + ":" + ex.getMessage();
        }
    }

    private String sampleFileName(SinkDeliveryFailure failure) {
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(':', '-')
                .replace('.', '-');
        return "failed-" + timestamp + "-" + sampleCount.get() + "-" + sanitize(failure.target()) + ".json";
    }

    private void retainMaxSamples() throws IOException {
        List<Path> samples = sampleFiles();
        int deleteCount = samples.size() - config.maxSamples();
        for (int i = 0; i < deleteCount; i++) {
            Files.deleteIfExists(samples.get(i));
        }
    }

    private int retainedSampleCount() {
        try {
            return sampleFiles().size();
        } catch (IOException ex) {
            return 0;
        }
    }

    private List<Path> sampleFiles() throws IOException {
        if (!Files.isDirectory(config.directory())) {
            return List.of();
        }
        try (var stream = Files.list(config.directory())) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("failed-"))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(this::lastModifiedMillis))
                    .toList();
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0;
        }
    }

    private static String sanitize(String value) {
        StringBuilder sanitized = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                sanitized.append(ch);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }
}
