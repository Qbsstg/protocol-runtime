package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.RuntimeLifecycle;
import io.github.qbsstg.protocol.runtime.core.RuntimeParserBinding;
import io.github.qbsstg.protocol.runtime.core.RuntimePipelineRunner;
import io.github.qbsstg.protocol.runtime.iec101.Iec101RuntimeBinding;
import io.github.qbsstg.protocol.runtime.iec103.Iec103RuntimeBinding;
import io.github.qbsstg.protocol.runtime.iec104.Iec104RuntimeBinding;
import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressServer;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaIngressConsumerConfig;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaIngressModule;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaPollingRecordSource;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaRecordHandler;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaRecordSource;
import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttIngressClientConfig;
import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttIngressModule;
import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttMessageHandler;
import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttMessageSource;
import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttPahoMessageSource;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServer;
import io.github.qbsstg.protocol.runtime.modbus.ModbusRuntimeBinding;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.Supplier;

public final class StandaloneCollector implements RuntimeLifecycle {

    private final StandaloneCollectorAppConfig appConfig;
    private final RuntimeSinks sinks;
    private final List<TcpListenerRuntime> tcpListeners;
    private final List<HttpListenerRuntime> httpListeners;
    private final List<KafkaConsumerRuntime<?>> kafkaConsumers;
    private final List<MqttClientRuntime<?>> mqttClients;
    private final List<RuntimeListener> listeners;
    private final Clock clock;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private CollectorLifecycleState state = CollectorLifecycleState.CONFIGURED;
    private Instant startedAt;
    private Instant stoppedAt;
    private String startupFailureReason;
    private Throwable lastException;

    private StandaloneCollector(StandaloneCollectorAppConfig appConfig, RuntimeSinks sinks) {
        this(appConfig, sinks, Clock.systemUTC());
    }

    private StandaloneCollector(StandaloneCollectorAppConfig appConfig, RuntimeSinks sinks, Clock clock) {
        this(
                appConfig,
                sinks,
                clock,
                config -> new KafkaPollingRecordSource(config.kafka()),
                config -> new MqttPahoMessageSource(config.mqtt()));
    }

    StandaloneCollector(
            StandaloneCollectorAppConfig appConfig,
            RuntimeSinks sinks,
            Clock clock,
            Function<KafkaConsumerConfig, KafkaRecordSource> kafkaRecordSourceFactory) {
        this(
                appConfig,
                sinks,
                clock,
                kafkaRecordSourceFactory,
                config -> new MqttPahoMessageSource(config.mqtt()));
    }

    StandaloneCollector(
            StandaloneCollectorAppConfig appConfig,
            RuntimeSinks sinks,
            Clock clock,
            Function<KafkaConsumerConfig, KafkaRecordSource> kafkaRecordSourceFactory,
            Function<MqttClientConfig, MqttMessageSource> mqttMessageSourceFactory) {
        this.appConfig = Objects.requireNonNull(appConfig, "appConfig must not be null");
        this.sinks = Objects.requireNonNull(sinks, "sinks must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(kafkaRecordSourceFactory, "kafkaRecordSourceFactory must not be null");
        Objects.requireNonNull(mqttMessageSourceFactory, "mqttMessageSourceFactory must not be null");
        this.tcpListeners = appConfig.tcpListeners().stream()
                .map(listener -> new TcpListenerRuntime(listener, createTcpServer(listener)))
                .toList();
        this.httpListeners = appConfig.httpListeners().stream()
                .map(listener -> new HttpListenerRuntime(listener, createHttpServer(listener)))
                .toList();
        List<KafkaConsumerRuntime<?>> kafkaConsumerRuntimes = new ArrayList<>();
        for (KafkaConsumerConfig consumer : appConfig.kafkaConsumers()) {
            kafkaConsumerRuntimes.add(createKafkaConsumer(consumer, kafkaRecordSourceFactory.apply(consumer)));
        }
        this.kafkaConsumers = List.copyOf(kafkaConsumerRuntimes);
        List<MqttClientRuntime<?>> mqttClientRuntimes = new ArrayList<>();
        for (MqttClientConfig client : appConfig.mqttClients()) {
            mqttClientRuntimes.add(createMqttClient(client, mqttMessageSourceFactory.apply(client)));
        }
        this.mqttClients = List.copyOf(mqttClientRuntimes);
        List<RuntimeListener> runtimeListeners =
                new ArrayList<>(
                        tcpListeners.size() + httpListeners.size() + kafkaConsumers.size() + mqttClients.size());
        runtimeListeners.addAll(tcpListeners);
        runtimeListeners.addAll(httpListeners);
        runtimeListeners.addAll(kafkaConsumers);
        runtimeListeners.addAll(mqttClients);
        this.listeners = List.copyOf(runtimeListeners);
    }

    public static StandaloneCollector create(StandaloneCollectorConfig config) {
        return create(StandaloneCollectorAppConfig.fromSingle(config));
    }

    public static StandaloneCollector create(StandaloneCollectorAppConfig config) {
        return new StandaloneCollector(config, RuntimeSinks.from(config));
    }

    public StandaloneCollectorConfig config() {
        return appConfig.singleCollectorConfig();
    }

    public StandaloneCollectorAppConfig appConfig() {
        return appConfig;
    }

    public Optional<InMemoryRuntimeSink<Object>> inMemorySink() {
        return sinks.inMemory();
    }

    public synchronized CollectorLifecycleState state() {
        return state;
    }

    public synchronized CollectorStatusSnapshot statusSnapshot() {
        List<CollectorSourceStatus> sourceStatuses = appConfig.sources().stream()
                .map(source -> new CollectorSourceStatus(
                        source.name(),
                        source.sourceId().qualifiedValue(),
                        source.protocol().configValue()))
                .toList();
        List<TcpListenerStatus> tcpListenerStatuses = tcpListeners.stream()
                .map(this::tcpListenerStatus)
                .toList();
        List<HttpListenerStatus> httpListenerStatuses = httpListeners.stream()
                .map(this::httpListenerStatus)
                .toList();
        List<KafkaConsumerStatus> kafkaConsumerStatuses = kafkaConsumers.stream()
                .map(this::kafkaConsumerStatus)
                .toList();
        List<MqttClientStatus> mqttClientStatuses = mqttClients.stream()
                .map(this::mqttClientStatus)
                .toList();
        return new CollectorStatusSnapshot(
                state,
                startedAt,
                stoppedAt,
                startupFailureReason,
                lastException == null ? null : lastException.getClass().getName(),
                lastException == null ? null : lastException.getMessage(),
                sourceStatuses,
                tcpListenerStatuses,
                httpListenerStatuses,
                kafkaConsumerStatuses,
                mqttClientStatuses,
                tcpListenerStatuses.stream().mapToInt(TcpListenerStatus::activeConnectionCount).sum(),
                sinks.metricsSnapshot(),
                appConfig.sinkType(),
                sinks.fileSinkStatus(),
                appConfig.fileSinkRotation(),
                appConfig.backpressureDecision(),
                appConfig.backpressureMaxPayloadBytes(),
                appConfig.oversizedPayloadDecision(),
                appConfig.strictAsduParsing());
    }

    public synchronized boolean isRunning() {
        return state == CollectorLifecycleState.RUNNING
                && !listeners.isEmpty()
                && tcpListeners.stream().allMatch(listener -> listener.server().isRunning())
                && httpListeners.stream().allMatch(listener -> listener.server().isRunning())
                && kafkaConsumers.stream().allMatch(KafkaConsumerRuntime::isRunning)
                && mqttClients.stream().allMatch(MqttClientRuntime::isRunning);
    }

    public synchronized InetSocketAddress localAddress() {
        return singleServer().localAddress();
    }

    public synchronized List<InetSocketAddress> localAddresses() {
        return tcpListeners.stream().map(listener -> listener.server().localAddress()).toList();
    }

    public synchronized int port() {
        return singleServer().port();
    }

    public synchronized List<Integer> ports() {
        return tcpListeners.stream().map(listener -> listener.server().port()).toList();
    }

    public synchronized List<InetSocketAddress> httpLocalAddresses() {
        return httpListeners.stream().map(listener -> listener.server().localAddress()).toList();
    }

    public synchronized List<Integer> httpPorts() {
        return httpListeners.stream().map(listener -> listener.server().port()).toList();
    }

    public synchronized int activeConnectionCount() {
        return tcpListeners.stream().mapToInt(listener -> listener.server().activeConnectionCount()).sum();
    }

    @Override
    public synchronized void start() {
        if (state == CollectorLifecycleState.RUNNING || state == CollectorLifecycleState.STARTING) {
            return;
        }
        if (state == CollectorLifecycleState.STOPPING) {
            IllegalStateException failure = new IllegalStateException("collector is stopping");
            lastException = failure;
            throw failure;
        }
        if (state == CollectorLifecycleState.STOPPED || state == CollectorLifecycleState.FAILED) {
            IllegalStateException failure = new IllegalStateException("collector cannot be restarted after " + state);
            lastException = failure;
            throw failure;
        }
        state = CollectorLifecycleState.STARTING;
        stoppedAt = null;
        startupFailureReason = null;
        lastException = null;
        Exception failure = null;
        RuntimeListener failedListener = null;
        for (RuntimeListener listener : listeners) {
            try {
                listener.lifecycle().start();
            } catch (Exception ex) {
                failure = ex;
                failedListener = listener;
                break;
            }
        }
        if (failure != null) {
            recordStartupFailure(failedListener, failure);
            rollbackAfterStartFailure(failure);
            throw asRuntimeException(failure);
        }
        startedAt = clock.instant();
        state = CollectorLifecycleState.RUNNING;
    }

    @Override
    public synchronized void stop() {
        if (state == CollectorLifecycleState.STOPPED) {
            stopped.countDown();
            return;
        }
        if (state == CollectorLifecycleState.CONFIGURED) {
            stoppedAt = clock.instant();
            state = CollectorLifecycleState.STOPPED;
            stopped.countDown();
            return;
        }
        boolean wasFailed = state == CollectorLifecycleState.FAILED;
        if (!wasFailed) {
            state = CollectorLifecycleState.STOPPING;
        }
        RuntimeException failure = null;
        for (RuntimeListener listener : listeners) {
            try {
                listener.lifecycle().stop();
            } catch (RuntimeException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        try {
            sinks.stop();
        } catch (RuntimeException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        } finally {
            stoppedAt = clock.instant();
            if (failure != null) {
                lastException = failure;
                state = CollectorLifecycleState.FAILED;
            } else if (wasFailed) {
                state = CollectorLifecycleState.FAILED;
            } else {
                state = CollectorLifecycleState.STOPPED;
            }
            stopped.countDown();
        }
        if (failure != null) {
            throw failure;
        }
    }

    public void awaitShutdown() throws InterruptedException {
        stopped.await();
    }

    private TcpNettyServer<?> singleServer() {
        if (tcpListeners.size() != 1
                || !httpListeners.isEmpty()
                || !kafkaConsumers.isEmpty()
                || !mqttClients.isEmpty()) {
            throw new IllegalStateException("single listener API requires exactly one TCP listener");
        }
        return tcpListeners.get(0).server();
    }

    private TcpListenerStatus tcpListenerStatus(TcpListenerRuntime runtime) {
        TcpListenerConfig config = runtime.config();
        TcpNettyServer<?> server = runtime.server();
        boolean running = server.isRunning();
        String boundHost = null;
        Integer boundPort = null;
        if (running) {
            InetSocketAddress localAddress = server.localAddress();
            boundHost = localAddress.getHostString();
            boundPort = localAddress.getPort();
        }
        return new TcpListenerStatus(
                config.name(),
                config.sourceName(),
                config.sourceId().qualifiedValue(),
                config.protocol().configValue(),
                config.tcp().host(),
                config.tcp().port(),
                boundHost,
                boundPort,
                running,
                server.activeConnectionCount());
    }

    private HttpListenerStatus httpListenerStatus(HttpListenerRuntime runtime) {
        HttpListenerConfig config = runtime.config();
        HttpIngressServer<?> server = runtime.server();
        boolean running = server.isRunning();
        String boundHost = null;
        Integer boundPort = null;
        if (running) {
            InetSocketAddress localAddress = server.localAddress();
            boundHost = localAddress.getHostString();
            boundPort = localAddress.getPort();
        }
        return new HttpListenerStatus(
                config.name(),
                config.sourceName(),
                config.sourceId().qualifiedValue(),
                config.protocol().configValue(),
                config.http().host(),
                config.http().port(),
                config.http().path(),
                config.http().sourceIdMode(),
                config.http().sourceIdHeader(),
                config.http().maxPayloadBytes(),
                config.http().responseMode(),
                config.http().backlog(),
                config.http().workerThreads(),
                boundHost,
                boundPort,
                running);
    }

    private KafkaConsumerStatus kafkaConsumerStatus(KafkaConsumerRuntime<?> runtime) {
        KafkaConsumerConfig config = runtime.config();
        KafkaIngressConsumerConfig kafka = config.kafka();
        return new KafkaConsumerStatus(
                config.name(),
                config.sourceName(),
                config.sourceId().qualifiedValue(),
                config.protocol().configValue(),
                kafka.bootstrapServers(),
                kafka.groupId(),
                kafka.topics(),
                kafka.topicPattern(),
                kafka.sourceIdMode(),
                kafka.sourceIdHeader(),
                kafka.commitMode(),
                kafka.autoOffsetReset(),
                kafka.maxPollRecords(),
                kafka.pollTimeoutMillis(),
                runtime.isRunning());
    }

    private MqttClientStatus mqttClientStatus(MqttClientRuntime<?> runtime) {
        MqttClientConfig config = runtime.config();
        MqttIngressClientConfig mqtt = config.mqtt();
        return new MqttClientStatus(
                config.name(),
                config.sourceName(),
                config.sourceId().qualifiedValue(),
                config.protocol().configValue(),
                mqtt.brokerUri(),
                mqtt.clientId(),
                mqtt.topicFilters(),
                mqtt.qos(),
                mqtt.sourceIdMode(),
                mqtt.cleanSession(),
                mqtt.automaticReconnect(),
                mqtt.connectionTimeoutSeconds(),
                mqtt.keepAliveSeconds(),
                runtime.isRunning());
    }

    private void recordStartupFailure(RuntimeListener failedListener, Exception failure) {
        String listenerName = failedListener == null ? "unknown" : failedListener.name();
        String transport = failedListener == null ? "listener" : failedListener.transport();
        startupFailureReason = "Failed to start " + transport + " listener " + listenerName + ": "
                + failure.getMessage();
        lastException = failure;
        state = CollectorLifecycleState.FAILED;
    }

    private void rollbackAfterStartFailure(Exception failure) {
        for (RuntimeListener listener : listeners) {
            try {
                listener.lifecycle().stop();
            } catch (RuntimeException ex) {
                failure.addSuppressed(ex);
            }
        }
        try {
            sinks.stop();
        } catch (RuntimeException ex) {
            failure.addSuppressed(ex);
        } finally {
            stoppedAt = clock.instant();
            stopped.countDown();
        }
    }

    private static RuntimeException asRuntimeException(Exception failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("collector start failed", failure);
    }

    private TcpNettyServer<?> createTcpServer(TcpListenerConfig listener) {
        return switch (listener.protocol()) {
            case IEC104 -> createTypedServer(
                    listener,
                    () -> new Iec104RuntimeBinding(appConfig.strictAsduParsing()));
            case IEC101 -> createTypedServer(listener, Iec101RuntimeBinding::new);
            case IEC103 -> createTypedServer(listener, Iec103RuntimeBinding::new);
            case MODBUS -> createTypedServer(listener, ModbusRuntimeBinding::tcpStream);
        };
    }

    private HttpIngressServer<?> createHttpServer(HttpListenerConfig listener) {
        return switch (listener.protocol()) {
            case IEC104 -> createTypedHttpServer(
                    listener,
                    () -> new Iec104RuntimeBinding(appConfig.strictAsduParsing()));
            case IEC101 -> createTypedHttpServer(listener, Iec101RuntimeBinding::new);
            case IEC103 -> createTypedHttpServer(listener, Iec103RuntimeBinding::new);
            case MODBUS -> createTypedHttpServer(listener, ModbusRuntimeBinding::tcpStream);
        };
    }

    private KafkaConsumerRuntime<?> createKafkaConsumer(
            KafkaConsumerConfig consumer,
            KafkaRecordSource source) {
        return switch (consumer.protocol()) {
            case IEC104 -> createTypedKafkaConsumer(
                    consumer,
                    source,
                    () -> new Iec104RuntimeBinding(appConfig.strictAsduParsing()));
            case IEC101 -> createTypedKafkaConsumer(consumer, source, Iec101RuntimeBinding::new);
            case IEC103 -> createTypedKafkaConsumer(consumer, source, Iec103RuntimeBinding::new);
            case MODBUS -> createTypedKafkaConsumer(consumer, source, ModbusRuntimeBinding::tcpStream);
        };
    }

    private MqttClientRuntime<?> createMqttClient(
            MqttClientConfig client,
            MqttMessageSource source) {
        return switch (client.protocol()) {
            case IEC104 -> createTypedMqttClient(
                    client,
                    source,
                    () -> new Iec104RuntimeBinding(appConfig.strictAsduParsing()));
            case IEC101 -> createTypedMqttClient(client, source, Iec101RuntimeBinding::new);
            case IEC103 -> createTypedMqttClient(client, source, Iec103RuntimeBinding::new);
            case MODBUS -> createTypedMqttClient(client, source, ModbusRuntimeBinding::tcpStream);
        };
    }

    private <T> TcpNettyServer<T> createTypedServer(
            TcpListenerConfig listener,
            Supplier<RuntimeParserBinding<T>> parserFactory) {
        return new TcpNettyServer<>(
                listener.tcp(),
                channel -> new RuntimePipelineRunner<>(
                        parserFactory.get(),
                        sinks.runnerRecordSink(),
                        sinks.runnerFailureSink(),
                        sinks.backpressureStrategy(appConfig)),
                context -> listener.sourceId(),
                clock);
    }

    private <T> HttpIngressServer<T> createTypedHttpServer(
            HttpListenerConfig listener,
            Supplier<RuntimeParserBinding<T>> parserFactory) {
        return new HttpIngressServer<>(
                listener.http(),
                new RuntimePipelineRunner<>(
                        parserFactory.get(),
                        sinks.runnerRecordSink(),
                        sinks.runnerFailureSink(),
                        sinks.backpressureStrategy(appConfig)),
                clock);
    }

    private <T> KafkaConsumerRuntime<T> createTypedKafkaConsumer(
            KafkaConsumerConfig consumer,
            KafkaRecordSource source,
            Supplier<RuntimeParserBinding<T>> parserFactory) {
        RuntimePipelineRunner<T> runner = new RuntimePipelineRunner<>(
                parserFactory.get(),
                sinks.runnerRecordSink(),
                sinks.runnerFailureSink(),
                sinks.backpressureStrategy(appConfig));
        return new KafkaConsumerRuntime<>(
                consumer,
                source,
                runner,
                KafkaIngressModule.handler(consumer.kafka(), runner));
    }

    private <T> MqttClientRuntime<T> createTypedMqttClient(
            MqttClientConfig client,
            MqttMessageSource source,
            Supplier<RuntimeParserBinding<T>> parserFactory) {
        RuntimePipelineRunner<T> runner = new RuntimePipelineRunner<>(
                parserFactory.get(),
                sinks.runnerRecordSink(),
                sinks.runnerFailureSink(),
                sinks.backpressureStrategy(appConfig));
        return new MqttClientRuntime<>(
                client,
                source,
                runner,
                MqttIngressModule.handler(client.mqtt(), runner));
    }

    private interface RuntimeListener {

        String transport();

        String name();

        RuntimeLifecycle lifecycle();
    }

    private record TcpListenerRuntime(
            TcpListenerConfig config,
            TcpNettyServer<?> server) implements RuntimeListener {

        @Override
        public String transport() {
            return "TCP";
        }

        @Override
        public String name() {
            return config.name();
        }

        @Override
        public RuntimeLifecycle lifecycle() {
            return server;
        }
    }

    private record HttpListenerRuntime(
            HttpListenerConfig config,
            HttpIngressServer<?> server) implements RuntimeListener {

        @Override
        public String transport() {
            return "HTTP";
        }

        @Override
        public String name() {
            return config.name();
        }

        @Override
        public RuntimeLifecycle lifecycle() {
            return server;
        }
    }

    private record KafkaConsumerRuntime<T>(
            KafkaConsumerConfig config,
            KafkaRecordSource source,
            RuntimePipelineRunner<T> runner,
            KafkaRecordHandler<T> handler) implements RuntimeListener, RuntimeLifecycle {

        @Override
        public String transport() {
            return "Kafka consumer";
        }

        @Override
        public String name() {
            return config.name();
        }

        @Override
        public RuntimeLifecycle lifecycle() {
            return this;
        }

        @Override
        public void start() {
            runner.start();
            try {
                source.start(handler::accept);
            } catch (RuntimeException ex) {
                runner.stop();
                throw ex;
            }
        }

        @Override
        public void stop() {
            RuntimeException failure = null;
            try {
                source.stop();
            } catch (RuntimeException ex) {
                failure = ex;
            }
            try {
                runner.stop();
            } catch (RuntimeException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        boolean isRunning() {
            return source.isRunning() && runner.isRunning();
        }
    }

    private record MqttClientRuntime<T>(
            MqttClientConfig config,
            MqttMessageSource source,
            RuntimePipelineRunner<T> runner,
            MqttMessageHandler<T> handler) implements RuntimeListener, RuntimeLifecycle {

        @Override
        public String transport() {
            return "MQTT client";
        }

        @Override
        public String name() {
            return config.name();
        }

        @Override
        public RuntimeLifecycle lifecycle() {
            return this;
        }

        @Override
        public void start() {
            runner.start();
            try {
                source.start(handler::accept);
            } catch (RuntimeException ex) {
                runner.stop();
                throw ex;
            }
        }

        @Override
        public void stop() {
            RuntimeException failure = null;
            try {
                source.stop();
            } catch (RuntimeException ex) {
                failure = ex;
            }
            try {
                runner.stop();
            } catch (RuntimeException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        boolean isRunning() {
            return source.isRunning() && runner.isRunning();
        }
    }
}
