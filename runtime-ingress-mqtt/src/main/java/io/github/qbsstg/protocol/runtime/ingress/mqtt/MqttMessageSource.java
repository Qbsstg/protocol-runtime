package io.github.qbsstg.protocol.runtime.ingress.mqtt;

public interface MqttMessageSource {

    void start(MqttMessageReceiver receiver);

    void stop();

    boolean isRunning();
}
