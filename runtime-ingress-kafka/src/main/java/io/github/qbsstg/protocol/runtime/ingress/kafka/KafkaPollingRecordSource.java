package io.github.qbsstg.protocol.runtime.ingress.kafka;

import java.time.Duration;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

public final class KafkaPollingRecordSource implements KafkaRecordSource {

    private final KafkaIngressConsumerConfig config;
    private final Supplier<KafkaConsumer<byte[], byte[]>> consumerFactory;
    private final AtomicReference<KafkaConsumer<byte[], byte[]>> consumer = new AtomicReference<>();
    private volatile boolean running;
    private volatile Thread pollThread;
    private volatile RuntimeException lastFailure;

    public KafkaPollingRecordSource(KafkaIngressConsumerConfig config) {
        this(config, () -> new KafkaConsumer<>(consumerProperties(config)));
    }

    KafkaPollingRecordSource(
            KafkaIngressConsumerConfig config,
            Supplier<KafkaConsumer<byte[], byte[]>> consumerFactory) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.consumerFactory = Objects.requireNonNull(consumerFactory, "consumerFactory must not be null");
    }

    public RuntimeException lastFailure() {
        return lastFailure;
    }

    @Override
    public synchronized void start(KafkaRecordReceiver receiver) {
        Objects.requireNonNull(receiver, "receiver must not be null");
        if (running) {
            return;
        }
        running = true;
        lastFailure = null;
        Thread thread = new Thread(() -> poll(receiver), "protocol-runtime-kafka-" + config.consumerName());
        thread.setDaemon(true);
        pollThread = thread;
        thread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        KafkaConsumer<byte[], byte[]> activeConsumer = consumer.get();
        if (activeConsumer != null) {
            activeConsumer.wakeup();
        }
        Thread thread = pollThread;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(Math.max(1000L, config.pollTimeoutMillis() * 2));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping Kafka consumer " + config.consumerName(), ex);
            }
        }
        pollThread = null;
    }

    @Override
    public boolean isRunning() {
        return running && lastFailure == null;
    }

    private void poll(KafkaRecordReceiver receiver) {
        try (KafkaConsumer<byte[], byte[]> activeConsumer = consumerFactory.get()) {
            consumer.set(activeConsumer);
            subscribe(activeConsumer);
            while (running) {
                ConsumerRecords<byte[], byte[]> records =
                        activeConsumer.poll(Duration.ofMillis(config.pollTimeoutMillis()));
                boolean commit = false;
                for (var record : records) {
                    KafkaIngressResult result = receiver.accept(record);
                    commit = commit || result.commitAllowed();
                }
                if (commit) {
                    activeConsumer.commitAsync();
                }
            }
        } catch (WakeupException ex) {
            if (running) {
                lastFailure = ex;
                running = false;
            }
        } catch (RuntimeException ex) {
            lastFailure = ex;
            running = false;
        } finally {
            consumer.set(null);
            running = false;
        }
    }

    private void subscribe(KafkaConsumer<byte[], byte[]> activeConsumer) {
        if (!config.topics().isEmpty()) {
            activeConsumer.subscribe(config.topics());
            return;
        }
        activeConsumer.subscribe(Pattern.compile(config.topicPattern()));
    }

    private static Properties consumerProperties(KafkaIngressConsumerConfig config) {
        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.groupId());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.autoOffsetReset());
        properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Integer.toString(config.maxPollRecords()));
        return properties;
    }
}
