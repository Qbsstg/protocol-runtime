package io.github.qbsstg.protocol.runtime.iec104;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParsedRuntimeRecord;
import io.github.qbsstg.protocol.runtime.core.RuntimeParseResult;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Iec104RuntimeBindingTest {

    @Test
    void mapsSdkFramesToRuntimeRecords() {
        Iec104RuntimeBinding binding = new Iec104RuntimeBinding();
        IngressEnvelope envelope = new IngressEnvelope(
                SourceId.of("tcp", "station-1"),
                "tcp",
                bytes(0x68, 0x0E, 0x00, 0x00, 0x00, 0x00,
                        0x01, 0x01, 0x03, 0x00, 0x01, 0x00,
                        0x01, 0x00, 0x00, 0x01),
                Instant.EPOCH,
                Map.of("session", "s1"));

        List<RuntimeParseResult<Iec104Frame>> results = binding.parse(envelope);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        ParsedRuntimeRecord<Iec104Frame> success =
                assertInstanceOf(ParsedRuntimeRecord.class, results.get(0));
        assertEquals("iec104", success.record().protocol());
        assertEquals("I_FORMAT", success.record().recordType());
        assertEquals("s1", success.record().attributes().get("session"));
    }

    @Test
    void keepsIncompleteFramesBuffered() {
        Iec104RuntimeBinding binding = new Iec104RuntimeBinding();
        SourceId sourceId = SourceId.of("tcp", "station-1");

        List<RuntimeParseResult<Iec104Frame>> first = binding.parse(new IngressEnvelope(
                sourceId,
                "tcp",
                bytes(0x68, 0x0E, 0x00),
                Instant.EPOCH,
                Map.of()));
        List<RuntimeParseResult<Iec104Frame>> second = binding.parse(new IngressEnvelope(
                sourceId,
                "tcp",
                bytes(0x00, 0x00, 0x00, 0x01, 0x01, 0x03, 0x00, 0x01, 0x00,
                        0x01, 0x00, 0x00, 0x01),
                Instant.EPOCH,
                Map.of()));

        assertTrue(first.isEmpty());
        assertFalse(second.isEmpty());
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }
}
