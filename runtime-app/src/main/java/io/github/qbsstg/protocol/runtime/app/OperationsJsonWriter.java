package io.github.qbsstg.protocol.runtime.app;

import java.time.Instant;
import java.util.List;

final class OperationsJsonWriter {
    private final StringBuilder value = new StringBuilder();
    private boolean[] firstStack = new boolean[32];
    private int depth;
    private boolean afterName;

    OperationsJsonWriter() {
        firstStack[0] = true;
    }

    OperationsJsonWriter beginObject() {
        beforeValue();
        value.append('{');
        push();
        return this;
    }

    OperationsJsonWriter endObject() {
        value.append('}');
        pop();
        return this;
    }

    OperationsJsonWriter beginArray() {
        beforeValue();
        value.append('[');
        push();
        return this;
    }

    OperationsJsonWriter endArray() {
        value.append(']');
        pop();
        return this;
    }

    OperationsJsonWriter name(String name) {
        beforeName();
        string(name);
        value.append(':');
        afterName = true;
        return this;
    }

    OperationsJsonWriter value(String text) {
        if (text == null) {
            return nullValue();
        }
        beforeValue();
        string(text);
        return this;
    }

    OperationsJsonWriter value(Instant instant) {
        return instant == null ? nullValue() : value(instant.toString());
    }

    OperationsJsonWriter value(long number) {
        beforeValue();
        value.append(number);
        return this;
    }

    OperationsJsonWriter value(int number) {
        beforeValue();
        value.append(number);
        return this;
    }

    OperationsJsonWriter value(boolean bool) {
        beforeValue();
        value.append(bool);
        return this;
    }

    OperationsJsonWriter nullValue() {
        beforeValue();
        value.append("null");
        return this;
    }

    OperationsJsonWriter strings(List<String> strings) {
        beginArray();
        for (String text : strings) {
            value(text);
        }
        endArray();
        return this;
    }

    private void beforeName() {
        if (!firstStack[depth]) {
            value.append(',');
        }
        firstStack[depth] = false;
    }

    private void beforeValue() {
        if (afterName) {
            afterName = false;
            return;
        }
        if (!firstStack[depth]) {
            value.append(',');
        }
        firstStack[depth] = false;
    }

    private void push() {
        depth++;
        if (depth == firstStack.length) {
            boolean[] expanded = new boolean[firstStack.length * 2];
            System.arraycopy(firstStack, 0, expanded, 0, firstStack.length);
            firstStack = expanded;
        }
        firstStack[depth] = true;
        afterName = false;
    }

    private void pop() {
        depth--;
        afterName = false;
    }

    private void string(String text) {
        value.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> value.append("\\\"");
                case '\\' -> value.append("\\\\");
                case '\b' -> value.append("\\b");
                case '\f' -> value.append("\\f");
                case '\n' -> value.append("\\n");
                case '\r' -> value.append("\\r");
                case '\t' -> value.append("\\t");
                default -> {
                    if (c < 0x20) {
                        value.append(String.format("\\u%04x", (int) c));
                    } else {
                        value.append(c);
                    }
                }
            }
        }
        value.append('"');
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
