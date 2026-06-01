package io.github.qbsstg.protocol.runtime.core;

import java.util.Objects;

public record ParsedRuntimeRecord<T>(ParsedRecord<T> record) implements RuntimeParseResult<T> {

    public ParsedRuntimeRecord {
        Objects.requireNonNull(record, "record must not be null");
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
