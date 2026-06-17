package io.github.qbsstg.protocol.runtime.app;

import java.io.UncheckedIOException;

final class SinkDeliveryFailures {

    private SinkDeliveryFailures() {
    }

    static SinkDeliveryFailureType typeOf(RuntimeException failure) {
        if (failure instanceof SinkDeliveryException sinkFailure) {
            return sinkFailure.failureType();
        }
        if (failure instanceof UncheckedIOException) {
            return SinkDeliveryFailureType.WRITE_ERROR;
        }
        if (failure instanceof IllegalArgumentException || failure instanceof IllegalStateException) {
            return SinkDeliveryFailureType.PERMANENT_FAILURE;
        }
        return SinkDeliveryFailureType.UNKNOWN_FAILURE;
    }

    static boolean retryable(RuntimeException failure) {
        if (failure instanceof SinkDeliveryException sinkFailure) {
            return sinkFailure.retryable();
        }
        return typeOf(failure).retryable();
    }
}
