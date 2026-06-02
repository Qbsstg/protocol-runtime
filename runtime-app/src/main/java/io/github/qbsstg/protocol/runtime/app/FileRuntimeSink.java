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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class FileRuntimeSink<T> implements RecordSink<T>, FailureSink {

    private final Path output;
    private final FileSinkRotationConfig rotation;
    private BufferedWriter writer;
    private long currentBytes;
    private boolean stopped;

    public FileRuntimeSink(Path output) {
        this(output, FileSinkRotationConfig.defaults());
    }

    public FileRuntimeSink(Path output, FileSinkRotationConfig rotation) {
        this.output = Objects.requireNonNull(output, "output must not be null");
        this.rotation = Objects.requireNonNull(rotation, "rotation must not be null");
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
            currentBytes = Files.size(output);
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
        byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        rotateIfNeeded(bytes.length);
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
            currentBytes += bytes.length;
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to write sink file: " + output, ex);
        }
    }

    private void rotateIfNeeded(long incomingBytes) {
        if (currentBytes == 0 || currentBytes + incomingBytes <= rotation.maxBytes()) {
            return;
        }
        closeWriterForRotation();
        rotateFiles();
        start();
    }

    private void closeWriterForRotation() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
            writer = null;
            currentBytes = 0;
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to rotate sink file: " + output, ex);
        }
    }

    private void rotateFiles() {
        try {
            for (int i = rotation.maxHistory(); i >= 1; i--) {
                Path source = rotatedPath(i);
                Path target = rotatedPath(i + 1);
                if (!Files.exists(source)) {
                    continue;
                }
                if (i == rotation.maxHistory()) {
                    Files.delete(source);
                } else {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (Files.exists(output)) {
                Files.move(output, rotatedPath(1), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to rotate sink file: " + output, ex);
        }
    }

    private Path rotatedPath(int index) {
        return output.resolveSibling(output.getFileName() + "." + index);
    }
}
