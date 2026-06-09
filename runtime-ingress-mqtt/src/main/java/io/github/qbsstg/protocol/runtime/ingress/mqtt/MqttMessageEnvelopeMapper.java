package io.github.qbsstg.protocol.runtime.ingress.mqtt;

import io.github.qbsstg.protocol.runtime.core.IngressEnvelope;
import io.github.qbsstg.protocol.runtime.core.SourceId;

import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public final class MqttMessageEnvelopeMapper {

    private final MqttIngressClientConfig config;
    private final Clock clock;

    public MqttMessageEnvelopeMapper(MqttIngressClientConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public IngressEnvelope toEnvelope(String topic, MqttMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        String resolvedTopic = requireTopic(topic);
        SourceId sourceId = resolveSourceId(resolvedTopic);
        byte[] payload = message.getPayload() == null
                ? new byte[0]
                : Arrays.copyOf(message.getPayload(), message.getPayload().length);
        return new IngressEnvelope(
                sourceId,
                MqttIngressModule.TRANSPORT,
                payload,
                clock.instant(),
                attributes(resolvedTopic, message, sourceId));
    }

    private SourceId resolveSourceId(String topic) {
        return switch (config.sourceIdMode()) {
            case CONFIGURED -> config.configuredSourceId();
            case TOPIC -> parseSourceId(topic);
        };
    }

    private Map<String, String> attributes(String topic, MqttMessage message, SourceId sourceId) {
        Map<String, String> attributes = new LinkedHashMap<>();
        put(attributes, MqttIngressAttributes.CLIENT_NAME, config.clientName());
        put(attributes, MqttIngressAttributes.CLIENT_ID, config.clientId());
        put(attributes, MqttIngressAttributes.BROKER_URI, config.brokerUri());
        put(attributes, MqttIngressAttributes.TOPIC, topic);
        put(attributes, MqttIngressAttributes.PACKET_ID, String.valueOf(message.getId()));
        put(attributes, MqttIngressAttributes.QOS, String.valueOf(message.getQos()));
        put(attributes, MqttIngressAttributes.RETAINED, String.valueOf(message.isRetained()));
        put(attributes, MqttIngressAttributes.DUPLICATE, String.valueOf(message.isDuplicate()));
        put(attributes, MqttIngressAttributes.SOURCE_ID_MODE, config.sourceIdMode().name());
        put(attributes, MqttIngressAttributes.SOURCE_NAMESPACE, sourceId.namespace());
        put(attributes, MqttIngressAttributes.SOURCE_VALUE, sourceId.value());
        put(attributes, MqttIngressAttributes.PROTOCOL, config.protocol());
        return attributes;
    }

    private static String requireTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        return topic;
    }

    private SourceId parseSourceId(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("source id must use namespace:value");
        }
        return SourceId.of(value.substring(0, separator), value.substring(separator + 1));
    }

    private static void put(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
