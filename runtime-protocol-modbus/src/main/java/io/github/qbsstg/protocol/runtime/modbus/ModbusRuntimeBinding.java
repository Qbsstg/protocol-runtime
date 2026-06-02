package io.github.qbsstg.protocol.runtime.modbus;

import io.github.qbsstg.protocol.core.ParseResult;
import io.github.qbsstg.protocol.modbus.ModbusDatagramDecoder;
import io.github.qbsstg.protocol.modbus.ModbusParserConfig;
import io.github.qbsstg.protocol.modbus.ModbusTcpAdu;
import io.github.qbsstg.protocol.modbus.ModbusTcpStreamDecoder;
import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.ParseFailure;
import io.github.qbsstg.protocol.runtime.core.ParsedRecord;
import io.github.qbsstg.protocol.runtime.core.RuntimeParseResult;
import io.github.qbsstg.protocol.runtime.core.RuntimeParserBinding;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ModbusRuntimeBinding implements RuntimeParserBinding<ModbusTcpAdu> {

    public static final String PROTOCOL = "modbus";

    public enum Framing {
        TCP_STREAM,
        DATAGRAM
    }

    private final ConcurrentMap<SourceId, ModbusTcpStreamDecoder> streamDecoders = new ConcurrentHashMap<>();
    private final ModbusParserConfig parserConfig;
    private final Framing framing;
    private final ModbusDatagramDecoder datagramDecoder;

    public ModbusRuntimeBinding() {
        this(ModbusParserConfig.defaults(), Framing.TCP_STREAM);
    }

    public ModbusRuntimeBinding(ModbusParserConfig parserConfig) {
        this(parserConfig, Framing.TCP_STREAM);
    }

    public ModbusRuntimeBinding(ModbusParserConfig parserConfig, Framing framing) {
        this.parserConfig = Objects.requireNonNull(parserConfig, "parserConfig must not be null");
        this.framing = Objects.requireNonNull(framing, "framing must not be null");
        this.datagramDecoder = new ModbusDatagramDecoder(parserConfig);
    }

    public static ModbusRuntimeBinding tcpStream() {
        return new ModbusRuntimeBinding(ModbusParserConfig.defaults(), Framing.TCP_STREAM);
    }

    public static ModbusRuntimeBinding datagram() {
        return new ModbusRuntimeBinding(ModbusParserConfig.defaults(), Framing.DATAGRAM);
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public List<RuntimeParseResult<ModbusTcpAdu>> parse(IngressEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        if (framing == Framing.DATAGRAM) {
            return parseDatagram(envelope);
        }
        return parseTcpStream(envelope);
    }

    @Override
    public void reset(SourceId sourceId) {
        if (sourceId == null) {
            return;
        }
        ModbusTcpStreamDecoder decoder = streamDecoders.remove(sourceId);
        if (decoder != null) {
            decoder.reset();
        }
    }

    private List<RuntimeParseResult<ModbusTcpAdu>> parseTcpStream(IngressEnvelope envelope) {
        ModbusTcpStreamDecoder decoder = streamDecoders.computeIfAbsent(
                envelope.sourceId(),
                ignored -> new ModbusTcpStreamDecoder(parserConfig));

        List<ParseResult<ModbusTcpAdu>> parsedFrames = decoder.decode(envelope.payload());
        List<RuntimeParseResult<ModbusTcpAdu>> results = new ArrayList<>(parsedFrames.size());
        for (ParseResult<ModbusTcpAdu> parsedFrame : parsedFrames) {
            addResult(results, parsedFrame, envelope, null);
        }
        return List.copyOf(results);
    }

    private List<RuntimeParseResult<ModbusTcpAdu>> parseDatagram(IngressEnvelope envelope) {
        ParseResult<ModbusTcpAdu> parsedFrame = datagramDecoder.decode(envelope.payload());
        if (parsedFrame.isIncomplete()) {
            return List.of(RuntimeParseResult.failure(new ParseFailure(
                    envelope.sourceId(),
                    protocol(),
                    "Incomplete Modbus datagram",
                    envelope.payload(),
                    envelope.receivedAt(),
                    null,
                    envelope.attributes())));
        }

        List<RuntimeParseResult<ModbusTcpAdu>> results = new ArrayList<>(1);
        addResult(results, parsedFrame, envelope, null);
        return List.copyOf(results);
    }

    private void addResult(
            List<RuntimeParseResult<ModbusTcpAdu>> results,
            ParseResult<ModbusTcpAdu> parsedFrame,
            IngressEnvelope envelope,
            Throwable cause) {
        if (parsedFrame.isSuccess()) {
            ModbusTcpAdu frame = parsedFrame.getFrame();
            results.add(RuntimeParseResult.success(new ParsedRecord<>(
                    envelope.sourceId(),
                    protocol(),
                    frame.getFrameType(),
                    frame,
                    frame.getRawBytes(),
                    envelope.receivedAt(),
                    envelope.attributes())));
        } else if (parsedFrame.isError()) {
            results.add(RuntimeParseResult.failure(new ParseFailure(
                    envelope.sourceId(),
                    protocol(),
                    parsedFrame.getMessage(),
                    envelope.payload(),
                    envelope.receivedAt(),
                    cause,
                    envelope.attributes())));
        }
    }
}
