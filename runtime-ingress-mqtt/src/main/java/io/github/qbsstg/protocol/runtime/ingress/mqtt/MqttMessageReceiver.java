package io.github.qbsstg.protocol.runtime.ingress.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface MqttMessageReceiver {

    MqttIngressResult accept(String topic, MqttMessage message);
}
