package io.github.qbsstg.protocol.runtime.core;

public record SourceId(String namespace, String value) {

    public SourceId {
        namespace = requireNonBlank(namespace, "namespace");
        value = requireNonBlank(value, "value");
    }

    public static SourceId of(String namespace, String value) {
        return new SourceId(namespace, value);
    }

    public String qualifiedValue() {
        return namespace + ":" + value;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
