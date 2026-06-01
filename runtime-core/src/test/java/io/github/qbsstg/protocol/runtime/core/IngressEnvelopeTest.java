package io.github.qbsstg.protocol.runtime.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IngressEnvelopeTest {

    @Test
    void makesPayloadAndAttributesImmutable() {
        byte[] payload = new byte[] {1, 2, 3};
        Map<String, String> attributes = new HashMap<>();
        attributes.put("session", "a");

        IngressEnvelope envelope = new IngressEnvelope(
                SourceId.of("tcp", "device-1"),
                "tcp",
                payload,
                Instant.EPOCH,
                attributes);

        payload[0] = 9;
        attributes.put("session", "b");
        byte[] read = envelope.payload();
        read[1] = 8;

        assertArrayEquals(new byte[] {1, 2, 3}, envelope.payload());
        assertEquals("a", envelope.attributes().get("session"));
        assertThrows(UnsupportedOperationException.class,
                () -> envelope.attributes().put("x", "y"));
    }
}
