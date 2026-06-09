package io.github.qbsstg.protocol.runtime.ingress.mqtt;

public final class MqttIngressAttributes {

    public static final String CLIENT_NAME = "mqtt.client.name";
    public static final String CLIENT_ID = "mqtt.client.id";
    public static final String BROKER_URI = "mqtt.broker.uri";
    public static final String TOPIC = "mqtt.topic";
    public static final String PACKET_ID = "mqtt.packet.id";
    public static final String QOS = "mqtt.qos";
    public static final String RETAINED = "mqtt.retained";
    public static final String DUPLICATE = "mqtt.duplicate";
    public static final String SOURCE_ID_MODE = "mqtt.source.id.mode";
    public static final String SOURCE_NAMESPACE = "mqtt.source.namespace";
    public static final String SOURCE_VALUE = "mqtt.source.value";
    public static final String PROTOCOL = "protocol";

    private MqttIngressAttributes() {
    }
}
