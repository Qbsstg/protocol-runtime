package io.github.qbsstg.protocol.runtime.app;

import java.util.List;

public record CollectorConfigValidation(List<String> errors) {

    public CollectorConfigValidation {
        errors = List.copyOf(errors);
    }

    public static CollectorConfigValidation valid() {
        return new CollectorConfigValidation(List.of());
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public void throwIfInvalid() {
        if (!isValid()) {
            throw new IllegalArgumentException("Invalid collector configuration: " + String.join("; ", errors));
        }
    }
}
