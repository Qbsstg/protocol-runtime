package io.github.qbsstg.protocol.runtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DownstreamSinkSpiTest {

    @Test
    void createsRecordDeliveryRequestsFromParsedRecords() {
        ParsedRecord<String> record = new ParsedRecord<>(
                SourceId.of("test", "source-1"),
                "test",
                "sample",
                "value",
                new byte[] {1},
                Instant.EPOCH,
                Map.of("a", "b"));

        DownstreamDeliveryRequest<String> request = DownstreamDeliveryRequest.forRecord(record);

        assertEquals(DownstreamRecordKind.RECORD, request.kind());
        assertEquals("test:source-1", request.sourceId().qualifiedValue());
        assertEquals("test", request.protocol());
        assertEquals("sample", request.recordType());
        assertEquals(Map.of("a", "b"), request.attributes());
    }

    @Test
    void createsFailureDeliveryRequestsFromParseFailures() {
        ParseFailure failure = new ParseFailure(
                SourceId.of("test", "source-1"),
                "test",
                "bad frame",
                new byte[] {1},
                Instant.EPOCH,
                null,
                Map.of("a", "b"));

        DownstreamDeliveryRequest<Object> request = DownstreamDeliveryRequest.forFailure(failure);

        assertEquals(DownstreamRecordKind.PARSE_FAILURE, request.kind());
        assertEquals("test:source-1", request.sourceId().qualifiedValue());
        assertEquals("test", request.protocol());
        assertEquals(null, request.recordType());
        assertEquals(Map.of("a", "b"), request.attributes());
    }

    @Test
    void deliveryOutcomeCarriesRetryAndDeliverySemantics() {
        DownstreamDeliveryResult delivered = DownstreamDeliveryResult.success();
        DownstreamDeliveryResult timeout = DownstreamDeliveryResult.failure(
                DownstreamDeliveryOutcome.TIMEOUT,
                "broker timeout",
                "java.util.concurrent.TimeoutException",
                Map.of("endpoint", "redacted"));

        assertTrue(delivered.delivered());
        assertFalse(delivered.retryable());
        assertFalse(timeout.delivered());
        assertTrue(timeout.retryable());
        assertEquals(BackpressureDecision.ACCEPT, timeout.backpressureDecision());
        assertEquals(Map.of("endpoint", "redacted"), timeout.diagnostics());
    }

    @Test
    void deliveryRequestRejectsMismatchedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> new DownstreamDeliveryRequest<>(
                DownstreamRecordKind.RECORD,
                null,
                null,
                Instant.EPOCH,
                Map.of()));
    }
}
