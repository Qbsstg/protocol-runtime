package io.github.qbsstg.protocol.runtime.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qbsstg.protocol.modbus.ModbusFunctionCode;
import io.github.qbsstg.protocol.modbus.ModbusRegisterValues;
import io.github.qbsstg.protocol.modbus.ModbusTcpAdu;
import io.github.qbsstg.protocol.runtime.core.FailedRuntimeParse;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParsedRuntimeRecord;
import io.github.qbsstg.protocol.runtime.core.RuntimeParseResult;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModbusRuntimeBindingTest {

    @Test
    void mapsTcpSdkFramesToRuntimeRecords() {
        ModbusRuntimeBinding binding = ModbusRuntimeBinding.tcpStream();
        IngressEnvelope envelope = envelope(readHoldingRegistersRequest(), Map.of("session", "s1"));

        List<RuntimeParseResult<ModbusTcpAdu>> results = binding.parse(envelope);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        ParsedRuntimeRecord<ModbusTcpAdu> success =
                assertInstanceOf(ParsedRuntimeRecord.class, results.get(0));
        assertEquals("modbus", success.record().protocol());
        assertEquals("MODBUS_TCP_ADU", success.record().recordType());
        assertEquals("s1", success.record().attributes().get("session"));
        assertEquals(1, success.record().value().getTransactionId());
        assertEquals(ModbusFunctionCode.READ_HOLDING_REGISTERS,
                success.record().value().getPdu().getKnownFunctionCode());
    }

    @Test
    void keepsIncompleteTcpAdusBufferedPerSource() {
        ModbusRuntimeBinding binding = ModbusRuntimeBinding.tcpStream();
        SourceId station1 = SourceId.of("tcp", "station-1");
        SourceId station2 = SourceId.of("tcp", "station-2");
        byte[] frame = readHoldingRegistersRequest();

        List<RuntimeParseResult<ModbusTcpAdu>> first = binding.parse(envelope(station1, bytes(frame, 0, 5)));
        List<RuntimeParseResult<ModbusTcpAdu>> unrelated = binding.parse(envelope(station2, readHoldingRegistersRequest()));
        List<RuntimeParseResult<ModbusTcpAdu>> second =
                binding.parse(envelope(station1, bytes(frame, 5, frame.length - 5)));

        assertTrue(first.isEmpty());
        assertEquals(1, unrelated.size());
        assertFalse(second.isEmpty());
        ParsedRuntimeRecord<ModbusTcpAdu> success =
                assertInstanceOf(ParsedRuntimeRecord.class, second.get(0));
        assertEquals(ModbusFunctionCode.READ_HOLDING_REGISTERS,
                success.record().value().getPdu().getKnownFunctionCode());
    }

    @Test
    void routesTcpSdkErrorsToRuntimeFailuresAndContinues() {
        ModbusRuntimeBinding binding = ModbusRuntimeBinding.tcpStream();
        byte[] invalidThenValid = concat(bytes(
                0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x11),
                readHoldingRegistersRequest(2));

        List<RuntimeParseResult<ModbusTcpAdu>> results = binding.parse(envelope(invalidThenValid, Map.of()));

        assertEquals(2, results.size());
        FailedRuntimeParse<ModbusTcpAdu> failure = assertInstanceOf(FailedRuntimeParse.class, results.get(0));
        assertTrue(failure.failure().message().contains("Invalid Modbus ADU length"));
        assertEquals("modbus", failure.failure().protocol());
        assertEquals(invalidThenValid.length, failure.failure().rawPayload().length);
        assertTrue(results.get(1).isSuccess());
    }

    @Test
    void resetDropsBufferedTcpBytesForSource() {
        ModbusRuntimeBinding binding = ModbusRuntimeBinding.tcpStream();
        SourceId sourceId = SourceId.of("tcp", "station-1");
        byte[] frame = readHoldingRegistersRequest();

        assertTrue(binding.parse(envelope(sourceId, bytes(frame, 0, 5))).isEmpty());
        binding.reset(sourceId);
        List<RuntimeParseResult<ModbusTcpAdu>> results =
                binding.parse(envelope(sourceId, bytes(frame, 5, frame.length - 5)));

        assertFalse(results.isEmpty());
        assertTrue(results.stream().noneMatch(RuntimeParseResult::isSuccess));
    }

    @Test
    void mapsDatagramsToRuntimeRecords() {
        ModbusRuntimeBinding binding = ModbusRuntimeBinding.datagram();

        List<RuntimeParseResult<ModbusTcpAdu>> results = binding.parse(envelope(bytes(
                0x00, 0x01, 0x00, 0x00, 0x00, 0x07,
                0x11, 0x03, 0x04, 0x02, 0x2B, 0x00, 0x64), Map.of()));

        assertEquals(1, results.size());
        ParsedRuntimeRecord<ModbusTcpAdu> success =
                assertInstanceOf(ParsedRuntimeRecord.class, results.get(0));
        ModbusRegisterValues values = (ModbusRegisterValues) success.record().value().getPdu().getValue();
        assertEquals(2, values.getValues().length);
        assertEquals(555, values.getValues()[0]);
        assertEquals(100, values.getValues()[1]);
    }

    @Test
    void routesDatagramErrorsToRuntimeFailures() {
        ModbusRuntimeBinding binding = ModbusRuntimeBinding.datagram();

        List<RuntimeParseResult<ModbusTcpAdu>> results = binding.parse(envelope(bytes(
                0x00, 0x01, 0x00, 0x00, 0x00, 0x06,
                0x11, 0x03, 0x00, 0x6B, 0x00, 0x03, 0xFF), Map.of()));

        assertEquals(1, results.size());
        FailedRuntimeParse<ModbusTcpAdu> failure = assertInstanceOf(FailedRuntimeParse.class, results.get(0));
        assertTrue(failure.failure().message().contains("UDP datagram length"));
    }

    @Test
    void routesIncompleteDatagramsToRuntimeFailures() {
        ModbusRuntimeBinding binding = ModbusRuntimeBinding.datagram();

        List<RuntimeParseResult<ModbusTcpAdu>> results = binding.parse(envelope(bytes(0x00, 0x01, 0x00), Map.of()));

        assertEquals(1, results.size());
        FailedRuntimeParse<ModbusTcpAdu> failure = assertInstanceOf(FailedRuntimeParse.class, results.get(0));
        assertEquals("Incomplete Modbus datagram", failure.failure().message());
    }

    private static IngressEnvelope envelope(byte[] payload, Map<String, String> attributes) {
        return new IngressEnvelope(
                SourceId.of("tcp", "station-1"),
                "tcp",
                payload,
                Instant.EPOCH,
                attributes);
    }

    private static IngressEnvelope envelope(SourceId sourceId, byte[] payload) {
        return new IngressEnvelope(sourceId, "tcp", payload, Instant.EPOCH, Map.of());
    }

    private static byte[] readHoldingRegistersRequest() {
        return readHoldingRegistersRequest(1);
    }

    private static byte[] readHoldingRegistersRequest(int transactionId) {
        return bytes(
                (transactionId >> 8) & 0xFF, transactionId & 0xFF,
                0x00, 0x00, 0x00, 0x06,
                0x11, 0x03, 0x00, 0x6B, 0x00, 0x03);
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
