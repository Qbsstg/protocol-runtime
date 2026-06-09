package io.github.qbsstg.protocol.runtime.ingress.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

@FunctionalInterface
public interface MqttClientFactory {

    MqttClient create(MqttIngressClientConfig config) throws MqttException;
}
