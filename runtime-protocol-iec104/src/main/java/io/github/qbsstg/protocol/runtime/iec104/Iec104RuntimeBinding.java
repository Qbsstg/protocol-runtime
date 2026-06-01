package io.github.qbsstg.protocol.runtime.iec104;

import io.github.qbsstg.protocol.core.ParseResult;
import io.github.qbsstg.protocol.iec104.Iec104Frame;
import io.github.qbsstg.protocol.iec104.Iec104StreamDecoder;
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

public final class Iec104RuntimeBinding implements RuntimeParserBinding<Iec104Frame> {

    public static final String PROTOCOL = "iec104";

    private final ConcurrentMap<SourceId, Iec104StreamDecoder> decoders = new ConcurrentHashMap<>();
    private final boolean strictAsduParsing;

    public Iec104RuntimeBinding() {
        this(false);
    }

    public Iec104RuntimeBinding(boolean strictAsduParsing) {
        this.strictAsduParsing = strictAsduParsing;
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public List<RuntimeParseResult<Iec104Frame>> parse(IngressEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Iec104StreamDecoder decoder = decoders.computeIfAbsent(
                envelope.sourceId(),
                ignored -> new Iec104StreamDecoder(strictAsduParsing));

        List<ParseResult<Iec104Frame>> parsedFrames = decoder.decode(envelope.payload());
        List<RuntimeParseResult<Iec104Frame>> results = new ArrayList<>(parsedFrames.size());
        for (ParseResult<Iec104Frame> parsedFrame : parsedFrames) {
            if (parsedFrame.isSuccess()) {
                Iec104Frame frame = parsedFrame.getFrame();
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
                        null,
                        envelope.attributes())));
            }
        }
        return List.copyOf(results);
    }

    @Override
    public void reset(SourceId sourceId) {
        if (sourceId == null) {
            return;
        }
        Iec104StreamDecoder decoder = decoders.remove(sourceId);
        if (decoder != null) {
            decoder.reset();
        }
    }
}
