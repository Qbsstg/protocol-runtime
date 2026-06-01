package io.github.qbsstg.protocol.runtime.core;

public sealed interface RuntimeParseResult<T>
        permits ParsedRuntimeRecord, FailedRuntimeParse {

    boolean isSuccess();

    static <T> RuntimeParseResult<T> success(ParsedRecord<T> record) {
        return new ParsedRuntimeRecord<>(record);
    }

    static <T> RuntimeParseResult<T> failure(ParseFailure failure) {
        return new FailedRuntimeParse<>(failure);
    }
}
