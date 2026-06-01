package io.github.qbsstg.protocol.runtime.core;

@FunctionalInterface
public interface FailureSink extends RuntimeLifecycle {

    void accept(ParseFailure failure);

    static FailureSink noop() {
        return ignored -> {
        };
    }
}
