package io.github.qbsstg.protocol.runtime.core;

import java.util.Objects;

public record FailedRuntimeParse<T>(ParseFailure failure) implements RuntimeParseResult<T> {

    public FailedRuntimeParse {
        Objects.requireNonNull(failure, "failure must not be null");
    }

    @Override
    public boolean isSuccess() {
        return false;
    }
}
