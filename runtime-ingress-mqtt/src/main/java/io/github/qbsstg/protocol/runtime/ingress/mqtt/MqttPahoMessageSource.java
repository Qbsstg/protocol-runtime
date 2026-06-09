package io.github.qbsstg.protocol.runtime.ingress.mqtt;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public final class MqttPahoMessageSource implements MqttMessageSource {

    private final MqttIngressClientConfig config;
    private final MqttClientFactory clientFactory;
    private final AtomicReference<MqttClient> client = new AtomicReference<>();
    private volatile boolean running;
    private volatile RuntimeException lastFailure;

    public MqttPahoMessageSource(MqttIngressClientConfig config) {
        this(config, mqttConfig -> new MqttClient(mqttConfig.brokerUri(), mqttConfig.clientId()));
    }

    MqttPahoMessageSource(MqttIngressClientConfig config, MqttClientFactory clientFactory) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
    }

    public RuntimeException lastFailure() {
        return lastFailure;
    }

    @Override
    public synchronized void start(MqttMessageReceiver receiver) {
        Objects.requireNonNull(receiver, "receiver must not be null");
        if (running) {
            return;
        }
        lastFailure = null;
        try {
            MqttClient activeClient = clientFactory.create(config);
            client.set(activeClient);
            activeClient.setCallback(callback(receiver));
            activeClient.connect(connectOptions(config));
            running = true;
            activeClient.subscribe(
                    config.topicFilters().toArray(String[]::new),
                    qosArray(config.topicFilters().size(), config.qos()));
        } catch (MqttException ex) {
            lastFailure = new IllegalStateException("Failed to start MQTT client " + config.clientName(), ex);
            running = false;
            closeQuietly(client.getAndSet(null));
            throw lastFailure;
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        MqttClient activeClient = client.getAndSet(null);
        if (activeClient == null) {
            return;
        }
        try {
            if (activeClient.isConnected()) {
                activeClient.disconnect();
            }
            activeClient.close();
        } catch (MqttException ex) {
            lastFailure = new IllegalStateException("Failed to stop MQTT client " + config.clientName(), ex);
            throw lastFailure;
        }
    }

    @Override
    public boolean isRunning() {
        MqttClient activeClient = client.get();
        return running && lastFailure == null && activeClient != null && activeClient.isConnected();
    }

    private MqttCallbackExtended callback(MqttMessageReceiver receiver) {
        return new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                // Paho owns reconnect scheduling; runtime policy stays outside runtime-core.
            }

            @Override
            public void connectionLost(Throwable cause) {
                lastFailure = new IllegalStateException("MQTT connection lost for " + config.clientName(), cause);
                running = false;
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (running) {
                    receiver.accept(topic, message);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Ingress-only adapter; downstream MQTT publishing is out of scope.
            }
        };
    }

    private static MqttConnectOptions connectOptions(MqttIngressClientConfig config) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(config.cleanSession());
        options.setAutomaticReconnect(config.automaticReconnect());
        options.setConnectionTimeout(config.connectionTimeoutSeconds());
        options.setKeepAliveInterval(config.keepAliveSeconds());
        return options;
    }

    private static int[] qosArray(int size, int qos) {
        int[] values = new int[size];
        Arrays.fill(values, qos);
        return values;
    }

    private static void closeQuietly(MqttClient activeClient) {
        if (activeClient == null) {
            return;
        }
        try {
            activeClient.close();
        } catch (MqttException ignored) {
            // Preserve the original startup failure.
        }
    }
}
