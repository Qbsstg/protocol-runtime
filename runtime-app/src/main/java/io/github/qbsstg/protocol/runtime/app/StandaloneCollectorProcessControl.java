package io.github.qbsstg.protocol.runtime.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class StandaloneCollectorProcessControl {

    private StandaloneCollectorProcessControl() {
    }

    static void writePid(Path pidFile) throws IOException {
        if (pidFile == null) {
            return;
        }
        Path parent = pidFile.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()) + System.lineSeparator());
    }

    static void clearPid(Path pidFile) {
        if (pidFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException ignored) {
            // PID cleanup must not hide collector shutdown status.
        }
    }

    static StopResult stop(Path pidFile) {
        Objects.requireNonNull(pidFile, "pidFile must not be null");
        if (!Files.exists(pidFile)) {
            return new StopResult(StopStatus.NOT_RUNNING, "pid file does not exist: " + pidFile);
        }
        String rawPid;
        try {
            rawPid = Files.readString(pidFile).trim();
        } catch (IOException ex) {
            return new StopResult(StopStatus.INVALID_PID_FILE, "unable to read pid file: " + pidFile);
        }
        long pid;
        try {
            pid = Long.parseLong(rawPid);
        } catch (NumberFormatException ex) {
            return new StopResult(StopStatus.INVALID_PID_FILE, "pid file does not contain a numeric pid: " + pidFile);
        }
        if (pid <= 0) {
            return new StopResult(StopStatus.INVALID_PID_FILE, "pid must be positive: " + pidFile);
        }
        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle == null || !handle.isAlive()) {
            clearPid(pidFile);
            return new StopResult(StopStatus.NOT_RUNNING, "process is not running for pid " + pid);
        }
        boolean signaled = handle.destroy();
        return new StopResult(
                signaled ? StopStatus.SIGNALED : StopStatus.SIGNAL_FAILED,
                (signaled ? "stop signal sent to pid " : "unable to signal pid ") + pid);
    }

    enum StopStatus {
        SIGNALED,
        NOT_RUNNING,
        INVALID_PID_FILE,
        SIGNAL_FAILED
    }

    record StopResult(StopStatus status, String message) {
        boolean success() {
            return status == StopStatus.SIGNALED || status == StopStatus.NOT_RUNNING;
        }
    }
}
