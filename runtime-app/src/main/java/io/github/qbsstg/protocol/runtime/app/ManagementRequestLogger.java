package io.github.qbsstg.protocol.runtime.app;

import java.util.logging.Logger;

final class ManagementRequestLogger {

    private static final Logger LOGGER = Logger.getLogger(ManagementHttpServer.class.getName());

    private ManagementRequestLogger() {
    }

    static void log(
            boolean enabled,
            String method,
            String path,
            int status,
            long durationMillis,
            String remoteAddress,
            String rejectionReason) {
        if (!enabled) {
            return;
        }
        LOGGER.info(() -> "management request"
                + " method=" + method
                + " path=" + path
                + " status=" + status
                + " durationMillis=" + durationMillis
                + " remoteAddress=" + remoteAddress
                + " rejectionReason=" + (rejectionReason == null ? "none" : rejectionReason));
    }
}
