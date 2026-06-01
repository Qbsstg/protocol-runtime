package io.github.qbsstg.protocol.runtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SourceIdTest {

    @Test
    void exposesQualifiedValue() {
        assertEquals("iec104:station-1", SourceId.of("iec104", "station-1").qualifiedValue());
    }

    @Test
    void rejectsBlankParts() {
        assertThrows(IllegalArgumentException.class, () -> SourceId.of("", "station-1"));
        assertThrows(IllegalArgumentException.class, () -> SourceId.of("iec104", " "));
    }
}
