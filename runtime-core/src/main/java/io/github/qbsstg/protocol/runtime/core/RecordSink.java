package io.github.qbsstg.protocol.runtime.core;

@FunctionalInterface
public interface RecordSink<T> extends RuntimeLifecycle {

    void accept(ParsedRecord<T> record);

    static <T> RecordSink<T> noop() {
        return ignored -> {
        };
    }
}
