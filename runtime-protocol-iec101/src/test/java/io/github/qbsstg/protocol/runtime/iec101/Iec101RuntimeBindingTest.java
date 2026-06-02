package io.github.qbsstg.protocol.runtime.iec101;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.iec101.Iec101AsduType;
import io.github.qbsstg.protocol.iec101.Iec101Frame;
import io.github.qbsstg.protocol.iec101.Iec101FrameFormat;
import io.github.qbsstg.protocol.runtime.core.FailedRuntimeParse;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParsedRuntimeRecord;
import io.github.qbsstg.protocol.runtime.core.RuntimeParseResult;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Iec101RuntimeBindingTest {

    @Test
    void mapsSdkFramesToRuntimeRecords() {
        Iec101RuntimeBinding binding = new Iec101RuntimeBinding();
        IngressEnvelope envelope = envelope(variableSinglePointFrame(), Map.of("session", "s1"));

        List<RuntimeParseResult<Iec101Frame>> results = binding.parse(envelope);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        ParsedRuntimeRecord<Iec101Frame> success =
                assertInstanceOf(ParsedRuntimeRecord.class, results.get(0));
        assertEquals("iec101", success.record().protocol());
        assertEquals("VARIABLE_LENGTH", success.record().recordType());
        assertEquals("s1", success.record().attributes().get("session"));
        assertEquals(Iec101FrameFormat.VARIABLE_LENGTH, success.record().value().getFormat());
        assertEquals(Iec101AsduType.M_SP_NA_1, success.record().value().getAsdu().getType());
    }

    @Test
    void keepsIncompleteFramesBufferedPerSource() {
        Iec101RuntimeBinding binding = new Iec101RuntimeBinding();
        SourceId station1 = SourceId.of("serial", "station-1");
        SourceId station2 = SourceId.of("serial", "station-2");
        byte[] frame = variableSinglePointFrame();

        List<RuntimeParseResult<Iec101Frame>> first = binding.parse(envelope(station1, bytes(frame, 0, 5)));
        List<RuntimeParseResult<Iec101Frame>> unrelated = binding.parse(envelope(station2, bytes(0xE5)));
        List<RuntimeParseResult<Iec101Frame>> second =
                binding.parse(envelope(station1, bytes(frame, 5, frame.length - 5)));

        assertTrue(first.isEmpty());
        assertEquals(1, unrelated.size());
        assertFalse(second.isEmpty());
        ParsedRuntimeRecord<Iec101Frame> success =
                assertInstanceOf(ParsedRuntimeRecord.class, second.get(0));
        assertEquals(Iec101AsduType.M_SP_NA_1, success.record().value().getAsdu().getType());
    }

    @Test
    void routesSdkErrorsToRuntimeFailuresAndContinues() {
        Iec101RuntimeBinding binding = new Iec101RuntimeBinding();
        byte[] bad = variableSinglePointFrame();
        bad[bad.length - 2] = 0x00;

        List<RuntimeParseResult<Iec101Frame>> results = binding.parse(envelope(concat(bad, bytes(0xE5)), Map.of()));

        assertEquals(2, results.size());
        FailedRuntimeParse<Iec101Frame> failure = assertInstanceOf(FailedRuntimeParse.class, results.get(0));
        assertTrue(failure.failure().message().contains("Invalid IEC101 checksum"));
        assertEquals("iec101", failure.failure().protocol());
        assertEquals(variableSinglePointFrame().length + 1, failure.failure().rawPayload().length);
        assertTrue(results.get(1).isSuccess());
    }

    @Test
    void resetDropsBufferedBytesForSource() {
        Iec101RuntimeBinding binding = new Iec101RuntimeBinding();
        SourceId sourceId = SourceId.of("serial", "station-1");
        byte[] frame = variableSinglePointFrame();

        assertTrue(binding.parse(envelope(sourceId, bytes(frame, 0, 5))).isEmpty());
        binding.reset(sourceId);
        List<RuntimeParseResult<Iec101Frame>> results =
                binding.parse(envelope(sourceId, bytes(frame, 5, frame.length - 5)));

        assertEquals(1, results.size());
        assertFalse(results.get(0).isSuccess());
    }

    private static IngressEnvelope envelope(byte[] payload, Map<String, String> attributes) {
        return new IngressEnvelope(
                SourceId.of("serial", "station-1"),
                "serial",
                payload,
                Instant.EPOCH,
                attributes);
    }

    private static IngressEnvelope envelope(SourceId sourceId, byte[] payload) {
        return new IngressEnvelope(sourceId, "serial", payload, Instant.EPOCH, Map.of());
    }

    private static byte[] variableSinglePointFrame() {
        return variableFrame(0x08, 0x01, bytes(
                0x01, 0x01, 0x03, 0x00,
                0x01, 0x00, 0x01, 0x00, 0x00,
                0x01));
    }

    private static byte[] variableFrame(int control, int linkAddress, byte[] asdu) {
        byte[] payload = new byte[2 + asdu.length];
        payload[0] = (byte) control;
        payload[1] = (byte) linkAddress;
        System.arraycopy(asdu, 0, payload, 2, asdu.length);
        int checksum = checksum(payload);
        byte[] frame = new byte[6 + payload.length];
        frame[0] = 0x68;
        frame[1] = (byte) payload.length;
        frame[2] = (byte) payload.length;
        frame[3] = 0x68;
        System.arraycopy(payload, 0, frame, 4, payload.length);
        frame[4 + payload.length] = (byte) checksum;
        frame[5 + payload.length] = 0x16;
        return frame;
    }

    private static int checksum(byte[] bytes) {
        int value = 0;
        for (byte current : bytes) {
            value = (value + (current & 0xFF)) & 0xFF;
        }
        return value;
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }

    private static byte[] bytes(byte[] source, int offset, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(source, offset, bytes, 0, length);
        return bytes;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] bytes = new byte[first.length + second.length];
        System.arraycopy(first, 0, bytes, 0, first.length);
        System.arraycopy(second, 0, bytes, first.length, second.length);
        return bytes;
    }
}
