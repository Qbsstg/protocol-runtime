package io.github.qbsstg.protocol.runtime.iec101;

import io.github.qbsstg.protocol.core.ParseResult;
import io.github.qbsstg.protocol.iec101.Iec101Frame;
import io.github.qbsstg.protocol.iec101.Iec101ParserConfig;
import io.github.qbsstg.protocol.iec101.Iec101StreamDecoder;
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

public final class Iec101RuntimeBinding implements RuntimeParserBinding<Iec101Frame> {

    public static final String PROTOCOL = "iec101";

    private final ConcurrentMap<SourceId, Iec101StreamDecoder> decoders = new ConcurrentHashMap<>();
    private final Iec101ParserConfig parserConfig;

    public Iec101RuntimeBinding() {
        this(Iec101ParserConfig.defaultUnbalanced());
    }

    public Iec101RuntimeBinding(Iec101ParserConfig parserConfig) {
        this.parserConfig = Objects.requireNonNull(parserConfig, "parserConfig must not be null");
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public List<RuntimeParseResult<Iec101Frame>> parse(IngressEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Iec101StreamDecoder decoder = decoders.computeIfAbsent(
                envelope.sourceId(),
                ignored -> new Iec101StreamDecoder(parserConfig));

        List<ParseResult<Iec101Frame>> parsedFrames = decoder.decode(envelope.payload());
        List<RuntimeParseResult<Iec101Frame>> results = new ArrayList<>(parsedFrames.size());
        for (ParseResult<Iec101Frame> parsedFrame : parsedFrames) {
            if (parsedFrame.isSuccess()) {
                Iec101Frame frame = parsedFrame.getFrame();
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
        Iec101StreamDecoder decoder = decoders.remove(sourceId);
        if (decoder != null) {
            decoder.reset();
        }
    }
}
