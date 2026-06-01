package io.github.qbsstg.protocol.runtime.core;

import java.util.List;

public interface RuntimeParserBinding<T> extends RuntimeLifecycle {

    String protocol();

    List<RuntimeParseResult<T>> parse(IngressEnvelope envelope);

    default void reset(SourceId sourceId) {
    }
}
