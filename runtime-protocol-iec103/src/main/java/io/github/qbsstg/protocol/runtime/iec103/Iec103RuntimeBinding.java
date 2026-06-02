package io.github.qbsstg.protocol.runtime.iec103;

import io.github.qbsstg.protocol.core.ParseResult;
import io.github.qbsstg.protocol.iec103.Iec103Frame;
import io.github.qbsstg.protocol.iec103.Iec103ParserConfig;
import io.github.qbsstg.protocol.iec103.Iec103StreamDecoder;
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

public final class Iec103RuntimeBinding implements RuntimeParserBinding<Iec103Frame> {

    public static final String PROTOCOL = "iec103";

    private final ConcurrentMap<SourceId, Iec103StreamDecoder> decoders = new ConcurrentHashMap<>();
    private final Iec103ParserConfig parserConfig;

    public Iec103RuntimeBinding() {
        this(Iec103ParserConfig.defaultUnbalanced());
    }

    public Iec103RuntimeBinding(Iec103ParserConfig parserConfig) {
        this.parserConfig = Objects.requireNonNull(parserConfig, "parserConfig must not be null");
    }

    @Override
    public String protocol() {
        return PROTOCOL;
    }

    @Override
    public List<RuntimeParseResult<Iec103Frame>> parse(IngressEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Iec103StreamDecoder decoder = decoders.computeIfAbsent(
                envelope.sourceId(),
                ignored -> new Iec103StreamDecoder(parserConfig));

        List<ParseResult<Iec103Frame>> parsedFrames = decoder.decode(envelope.payload());
        List<RuntimeParseResult<Iec103Frame>> results = new ArrayList<>(parsedFrames.size());
        for (ParseResult<Iec103Frame> parsedFrame : parsedFrames) {
            if (parsedFrame.isSuccess()) {
                Iec103Frame frame = parsedFrame.getFrame();
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
        Iec103StreamDecoder decoder = decoders.remove(sourceId);
        if (decoder != null) {
            decoder.reset();
        }
    }
}
