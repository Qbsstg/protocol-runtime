package io.github.qbsstg.protocol.runtime.core;

public record DownstreamSinkIdentity(String type, String name) {

    public DownstreamSinkIdentity {
        type = requireNonBlank(type, "type");
        name = requireNonBlank(name, "name");
    }

    public String qualifiedName() {
        return type + ":" + name;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
