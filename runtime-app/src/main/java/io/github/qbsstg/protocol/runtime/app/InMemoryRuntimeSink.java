package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryRuntimeSink<T> implements RecordSink<T>, FailureSink {

    private final CopyOnWriteArrayList<ParsedRecord<T>> records = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ParseFailure> failures = new CopyOnWriteArrayList<>();

    @Override
    public void accept(ParsedRecord<T> record) {
        records.add(record);
    }

    @Override
    public void accept(ParseFailure failure) {
        failures.add(failure);
    }

    public List<ParsedRecord<T>> records() {
        return List.copyOf(records);
    }

    public List<ParseFailure> failures() {
        return List.copyOf(failures);
    }
}
