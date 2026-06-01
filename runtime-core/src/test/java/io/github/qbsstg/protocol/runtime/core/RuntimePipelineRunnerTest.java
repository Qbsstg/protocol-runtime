package io.github.qbsstg.protocol.runtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimePipelineRunnerTest {

    @Test
    void requiresLifecycleBeforeAcceptingEnvelopes() {
        RuntimePipelineRunner<String> runner = new RuntimePipelineRunner<>(
                new FixedBinding(List.of()),
                RecordSink.noop(),
                FailureSink.noop(),
                BackpressureStrategy.acceptAll());

        assertFalse(runner.isRunning());
        assertThrows(IllegalStateException.class, () -> runner.accept(envelope()));

        runner.start();
        assertTrue(runner.isRunning());
        assertEquals(BackpressureDecision.ACCEPT, runner.accept(envelope()));

        runner.stop();
        assertFalse(runner.isRunning());
        assertThrows(IllegalStateException.class, () -> runner.accept(envelope()));
    }

    @Test
    void routesParsedRecordsAndFailuresToConfiguredSinks() {
        ParsedRecord<String> record = parsedRecord("ok");
        ParseFailure failure = parseFailure("bad frame");
        List<ParsedRecord<String>> records = new ArrayList<>();
        List<ParseFailure> failures = new ArrayList<>();
        RuntimePipelineRunner<String> runner = new RuntimePipelineRunner<>(
                new FixedBinding(List.of(
                        RuntimeParseResult.success(record),
                        RuntimeParseResult.failure(failure))),
                records::add,
                failures::add,
                BackpressureStrategy.acceptAll());

        runner.start();
        BackpressureDecision decision = runner.accept(envelope());

        assertEquals(BackpressureDecision.ACCEPT, decision);
        assertEquals(List.of(record), records);
        assertEquals(List.of(failure), failures);
    }

    @Test
    void backpressureDecisionSkipsParserAndSinks() {
        CountingBinding binding = new CountingBinding();
        List<ParsedRecord<String>> records = new ArrayList<>();
        RuntimePipelineRunner<String> runner = new RuntimePipelineRunner<>(
                binding,
                records::add,
                FailureSink.noop(),
                BackpressureStrategy.fixed(BackpressureDecision.RETRY_LATER));

        runner.start();
        BackpressureDecision decision = runner.accept(envelope());

        assertEquals(BackpressureDecision.RETRY_LATER, decision);
        assertEquals(0, binding.parseCalls);
        assertTrue(records.isEmpty());
    }

    @Test
    void parserExceptionsAreReportedAsFailures() {
        RuntimeException cause = new RuntimeException("decoder failed");
        List<ParseFailure> failures = new ArrayList<>();
        RuntimePipelineRunner<String> runner = new RuntimePipelineRunner<>(
                new ThrowingBinding(cause),
                RecordSink.noop(),
                failures::add,
                BackpressureStrategy.acceptAll());

        runner.start();
        BackpressureDecision decision = runner.accept(envelope());

        assertEquals(BackpressureDecision.ACCEPT, decision);
        assertEquals(1, failures.size());
        assertEquals("decoder failed", failures.get(0).message());
        assertSame(cause, failures.get(0).cause());
        assertEquals("test", failures.get(0).protocol());
    }

    @Test
    void externalFailuresCanBeReportedToFailureSink() {
        ParseFailure failure = parseFailure("connection failed");
        List<ParseFailure> failures = new ArrayList<>();
        RuntimePipelineRunner<String> runner = new RuntimePipelineRunner<>(
                new FixedBinding(List.of()),
                RecordSink.noop(),
                failures::add,
                BackpressureStrategy.acceptAll());

        assertThrows(IllegalStateException.class, () -> runner.reportFailure(failure));

        runner.start();
        runner.reportFailure(failure);

        assertEquals(List.of(failure), failures);
    }

    @Test
    void startAndStopLifecycleComponentsInPipelineOrder() {
        List<String> events = new ArrayList<>();
        LifecycleBinding binding = new LifecycleBinding(events);
        LifecycleRecordSink recordSink = new LifecycleRecordSink(events);
        LifecycleFailureSink failureSink = new LifecycleFailureSink(events);
        RuntimePipelineRunner<String> runner = new RuntimePipelineRunner<>(
                binding,
                recordSink,
                failureSink,
                BackpressureStrategy.acceptAll());

        runner.start();
        runner.stop();

        assertEquals(List.of(
                "binding-start",
                "record-start",
                "failure-start",
                "failure-stop",
                "record-stop",
                "binding-stop"), events);
    }

    private static IngressEnvelope envelope() {
        return new IngressEnvelope(
                SourceId.of("test", "source-1"),
                "test",
                new byte[] {1, 2, 3},
                Instant.EPOCH,
                Map.of("session", "s1"));
    }

    private static ParsedRecord<String> parsedRecord(String value) {
        return new ParsedRecord<>(
                SourceId.of("test", "source-1"),
                "test",
                "sample",
                value,
                new byte[] {1},
                Instant.EPOCH,
                Map.of());
    }

    private static ParseFailure parseFailure(String message) {
        return new ParseFailure(
                SourceId.of("test", "source-1"),
                "test",
                message,
                new byte[] {1},
                Instant.EPOCH,
                null,
                Map.of());
    }

    private static final class FixedBinding implements RuntimeParserBinding<String> {

        private final List<RuntimeParseResult<String>> results;

        private FixedBinding(List<RuntimeParseResult<String>> results) {
            this.results = results;
        }

        @Override
        public String protocol() {
            return "test";
        }

        @Override
        public List<RuntimeParseResult<String>> parse(IngressEnvelope envelope) {
            return results;
        }
    }

    private static final class CountingBinding implements RuntimeParserBinding<String> {

        private int parseCalls;

        @Override
        public String protocol() {
            return "test";
        }

        @Override
        public List<RuntimeParseResult<String>> parse(IngressEnvelope envelope) {
            parseCalls++;
            return List.of(RuntimeParseResult.success(parsedRecord("ok")));
        }
    }

    private static final class ThrowingBinding implements RuntimeParserBinding<String> {

        private final RuntimeException cause;

        private ThrowingBinding(RuntimeException cause) {
            this.cause = cause;
        }

        @Override
        public String protocol() {
            return "test";
        }

        @Override
        public List<RuntimeParseResult<String>> parse(IngressEnvelope envelope) {
            throw cause;
        }
    }

    private static final class LifecycleBinding implements RuntimeParserBinding<String> {

        private final List<String> events;

        private LifecycleBinding(List<String> events) {
            this.events = events;
        }

        @Override
        public void start() {
            events.add("binding-start");
        }

        @Override
        public void stop() {
            events.add("binding-stop");
        }

        @Override
        public String protocol() {
            return "test";
        }

        @Override
        public List<RuntimeParseResult<String>> parse(IngressEnvelope envelope) {
            return List.of();
        }
    }

    private static final class LifecycleRecordSink implements RecordSink<String> {

        private final List<String> events;

        private LifecycleRecordSink(List<String> events) {
            this.events = events;
        }

        @Override
        public void start() {
            events.add("record-start");
        }

        @Override
        public void stop() {
            events.add("record-stop");
        }

        @Override
        public void accept(ParsedRecord<String> record) {
        }
    }

    private static final class LifecycleFailureSink implements FailureSink {

        private final List<String> events;

        private LifecycleFailureSink(List<String> events) {
            this.events = events;
        }

        @Override
        public void start() {
            events.add("failure-start");
        }

        @Override
        public void stop() {
            events.add("failure-stop");
        }

        @Override
        public void accept(ParseFailure failure) {
        }
    }
}
