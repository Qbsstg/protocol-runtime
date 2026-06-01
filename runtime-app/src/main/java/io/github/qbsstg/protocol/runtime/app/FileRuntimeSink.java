package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class FileRuntimeSink<T> implements RecordSink<T>, FailureSink {

    private final Path output;
    private BufferedWriter writer;
    private boolean stopped;

    public FileRuntimeSink(Path output) {
        this.output = Objects.requireNonNull(output, "output must not be null");
    }

    @Override
    public synchronized void start() {
        if (writer != null) {
            return;
        }
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writer = Files.newBufferedWriter(
                    output,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            stopped = false;
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to open sink file: " + output, ex);
        }
    }

    @Override
    public void accept(ParsedRecord<T> record) {
        write(RuntimeRecordFormat.formatRecord(record));
    }

    @Override
    public void accept(ParseFailure failure) {
        write(RuntimeRecordFormat.formatFailure(failure));
    }

    @Override
    public synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (writer == null) {
            return;
        }
        try {
            writer.close();
            writer = null;
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to close sink file: " + output, ex);
        }
    }

    private synchronized void write(String line) {
        if (writer == null) {
            start();
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to write sink file: " + output, ex);
        }
    }
}
