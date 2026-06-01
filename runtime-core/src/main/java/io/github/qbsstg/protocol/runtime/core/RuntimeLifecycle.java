package io.github.qbsstg.protocol.runtime.core;

public interface RuntimeLifecycle extends AutoCloseable {

    default void start() {
    }

    default void stop() {
    }

    @Override
    default void close() {
        stop();
    }
}
