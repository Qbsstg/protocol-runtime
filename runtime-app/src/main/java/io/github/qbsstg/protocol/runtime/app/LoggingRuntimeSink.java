package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.FailureSink;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RecordSink;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoggingRuntimeSink<T> implements RecordSink<T>, FailureSink {

    private final Logger logger;

    public LoggingRuntimeSink(Logger logger) {
        this.logger = logger == null ? Logger.getLogger(LoggingRuntimeSink.class.getName()) : logger;
    }

    @Override
    public void accept(ParsedRecord<T> record) {
        logger.info(RuntimeRecordFormat.formatRecord(record));
    }

    @Override
    public void accept(ParseFailure failure) {
        logger.log(Level.WARNING, RuntimeRecordFormat.formatFailure(failure), failure.cause());
    }
}
