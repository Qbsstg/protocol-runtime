package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressResponseMode;
import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressServerConfig;
import io.github.qbsstg.protocol.runtime.ingress.http.HttpIngressSourceIdMode;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaCommitMode;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaIngressConsumerConfig;
import io.github.qbsstg.protocol.runtime.ingress.kafka.KafkaSourceIdMode;
import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttIngressClientConfig;
import io.github.qbsstg.protocol.runtime.ingress.mqtt.MqttSourceIdMode;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public record StandaloneCollectorConfig(
        TcpNettyServerConfig tcp,
        SourceId sourceId,
        BackpressureDecision backpressureDecision,
        long backpressureMaxPayloadBytes,
        BackpressureDecision oversizedPayloadDecision,
        long sinkFailureBackpressureThreshold,
        BackpressureDecision sinkFailureBackpressureDecision,
        SinkType sinkType,
        Path sinkFile,
        FileSinkRotationConfig fileSinkRotation,
        RuntimeProtocol protocol,
        boolean strictAsduParsing) {

    public static final String TCP_HOST = "collector.tcp.host";
    public static final String TCP_PORT = "collector.tcp.port";
    public static final String TCP_BOSS_THREADS = "collector.tcp.bossThreads";
    public static final String TCP_WORKER_THREADS = "collector.tcp.workerThreads";
    public static final String TCP_LISTENERS = "collector.tcp.listeners";
    public static final String TCP_LISTENER_PREFIX = "collector.tcp.listener.";
    public static final String TCP_LISTENER_HOST_SUFFIX = ".host";
    public static final String TCP_LISTENER_PORT_SUFFIX = ".port";
    public static final String TCP_LISTENER_BOSS_THREADS_SUFFIX = ".bossThreads";
    public static final String TCP_LISTENER_WORKER_THREADS_SUFFIX = ".workerThreads";
    public static final String TCP_LISTENER_SOURCE_SUFFIX = ".source";
    public static final String HTTP_LISTENERS = "collector.http.listeners";
    public static final String HTTP_LISTENER_PREFIX = "collector.http.listener.";
    public static final String HTTP_LISTENER_HOST_SUFFIX = ".host";
    public static final String HTTP_LISTENER_PORT_SUFFIX = ".port";
    public static final String HTTP_LISTENER_PATH_SUFFIX = ".path";
    public static final String HTTP_LISTENER_SOURCE_SUFFIX = ".source";
    public static final String HTTP_LISTENER_SOURCE_ID_MODE_SUFFIX = ".sourceIdMode";
    public static final String HTTP_LISTENER_SOURCE_ID_HEADER_SUFFIX = ".sourceIdHeader";
    public static final String HTTP_LISTENER_MAX_PAYLOAD_BYTES_SUFFIX = ".maxPayloadBytes";
    public static final String HTTP_LISTENER_RESPONSE_MODE_SUFFIX = ".responseMode";
    public static final String HTTP_LISTENER_BACKLOG_SUFFIX = ".backlog";
    public static final String HTTP_LISTENER_WORKER_THREADS_SUFFIX = ".workerThreads";
    public static final String KAFKA_CONSUMERS = "collector.kafka.consumers";
    public static final String KAFKA_CONSUMER_PREFIX = "collector.kafka.consumer.";
    public static final String KAFKA_CONSUMER_BOOTSTRAP_SERVERS_SUFFIX = ".bootstrapServers";
    public static final String KAFKA_CONSUMER_GROUP_ID_SUFFIX = ".groupId";
    public static final String KAFKA_CONSUMER_TOPICS_SUFFIX = ".topics";
    public static final String KAFKA_CONSUMER_TOPIC_PATTERN_SUFFIX = ".topicPattern";
    public static final String KAFKA_CONSUMER_SOURCE_SUFFIX = ".source";
    public static final String KAFKA_CONSUMER_SOURCE_ID_MODE_SUFFIX = ".sourceIdMode";
    public static final String KAFKA_CONSUMER_SOURCE_ID_HEADER_SUFFIX = ".sourceIdHeader";
    public static final String KAFKA_CONSUMER_COMMIT_MODE_SUFFIX = ".commitMode";
    public static final String KAFKA_CONSUMER_AUTO_OFFSET_RESET_SUFFIX = ".autoOffsetReset";
    public static final String KAFKA_CONSUMER_MAX_POLL_RECORDS_SUFFIX = ".maxPollRecords";
    public static final String KAFKA_CONSUMER_POLL_TIMEOUT_MILLIS_SUFFIX = ".pollTimeoutMillis";
    public static final String MQTT_CLIENTS = "collector.mqtt.clients";
    public static final String MQTT_CLIENT_PREFIX = "collector.mqtt.client.";
    public static final String MQTT_CLIENT_BROKER_URI_SUFFIX = ".brokerUri";
    public static final String MQTT_CLIENT_CLIENT_ID_SUFFIX = ".clientId";
    public static final String MQTT_CLIENT_TOPIC_FILTERS_SUFFIX = ".topicFilters";
    public static final String MQTT_CLIENT_QOS_SUFFIX = ".qos";
    public static final String MQTT_CLIENT_SOURCE_SUFFIX = ".source";
    public static final String MQTT_CLIENT_SOURCE_ID_MODE_SUFFIX = ".sourceIdMode";
    public static final String MQTT_CLIENT_CLEAN_SESSION_SUFFIX = ".cleanSession";
    public static final String MQTT_CLIENT_AUTOMATIC_RECONNECT_SUFFIX = ".automaticReconnect";
    public static final String MQTT_CLIENT_CONNECTION_TIMEOUT_SECONDS_SUFFIX = ".connectionTimeoutSeconds";
    public static final String MQTT_CLIENT_KEEP_ALIVE_SECONDS_SUFFIX = ".keepAliveSeconds";
    public static final String MANAGEMENT_ENABLED = "collector.management.enabled";
    public static final String MANAGEMENT_HOST = "collector.management.host";
    public static final String MANAGEMENT_PORT = "collector.management.port";
    public static final String MANAGEMENT_HEALTH_PATH = "collector.management.healthPath";
    public static final String MANAGEMENT_READINESS_PATH = "collector.management.readinessPath";
    public static final String MANAGEMENT_STATUS_PATH = "collector.management.statusPath";
    public static final String MANAGEMENT_ACCESS = "collector.management.access";
    public static final String MANAGEMENT_TOKEN = "collector.management.token";
    public static final String MANAGEMENT_REQUEST_LOGGING_ENABLED =
            "collector.management.requestLogging.enabled";
    public static final String MANAGEMENT_HEALTH_HISTORY_MAX_ENTRIES =
            "collector.management.healthHistory.maxEntries";
    public static final String PROFILE = "collector.profile";
    public static final String RUNTIME_DIR = "collector.runtime.dir";
    public static final String RUNTIME_CONF_DIR = "collector.runtime.confDir";
    public static final String RUNTIME_LOGS_DIR = "collector.runtime.logsDir";
    public static final String RUNTIME_DATA_DIR = "collector.runtime.dataDir";
    public static final String RUNTIME_RUN_DIR = "collector.runtime.runDir";
    public static final String RUNTIME_TMP_DIR = "collector.runtime.tmpDir";
    public static final String RUNTIME_PID_FILE = "collector.runtime.pidFile";
    public static final String RUNTIME_STATUS_FILE = "collector.runtime.statusFile";
    public static final String RUNTIME_LOG_FILE = "collector.runtime.logFile";
    public static final String RUNTIME_CREATE_DIRECTORIES = "collector.runtime.createDirectories";
    public static final String SOURCES = "collector.sources";
    public static final String SOURCE_ID = "collector.source.id";
    public static final String SOURCE_PREFIX = "collector.source.";
    public static final String SOURCE_ID_SUFFIX = ".id";
    public static final String SOURCE_PROTOCOL_SUFFIX = ".protocol";
    public static final String PROTOCOL = "collector.protocol";
    public static final String BACKPRESSURE = "collector.backpressure";
    public static final String BACKPRESSURE_MAX_PAYLOAD_BYTES = "collector.backpressure.maxPayloadBytes";
    public static final String BACKPRESSURE_OVERSIZED_PAYLOAD_DECISION =
            "collector.backpressure.oversizedPayloadDecision";
    public static final String BACKPRESSURE_SINK_FAILURE_THRESHOLD =
            "collector.backpressure.sinkFailureThreshold";
    public static final String BACKPRESSURE_SINK_FAILURE_DECISION =
            "collector.backpressure.sinkFailureDecision";
    public static final String SINK_TYPE = "collector.sink.type";
    public static final String SINK_FILE = "collector.sink.file";
    public static final String SINK_FILE_MAX_BYTES = "collector.sink.file.maxBytes";
    public static final String SINK_FILE_MAX_HISTORY = "collector.sink.file.maxHistory";
    public static final String IEC104_STRICT_ASDU = "collector.iec104.strictAsduParsing";

    private static final String DEFAULT_SOURCE_NAME = "default";
    private static final String DEFAULT_LISTENER_NAME = "default";
    private static final String DEFAULT_HTTP_HOST = "127.0.0.1";
    private static final String DEFAULT_HTTP_PATH = "/ingress";

    public StandaloneCollectorConfig {
        Objects.requireNonNull(tcp, "tcp must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(backpressureDecision, "backpressureDecision must not be null");
        Objects.requireNonNull(oversizedPayloadDecision, "oversizedPayloadDecision must not be null");
        Objects.requireNonNull(sinkFailureBackpressureDecision, "sinkFailureBackpressureDecision must not be null");
        Objects.requireNonNull(sinkType, "sinkType must not be null");
        Objects.requireNonNull(fileSinkRotation, "fileSinkRotation must not be null");
        Objects.requireNonNull(protocol, "protocol must not be null");
        if (backpressureMaxPayloadBytes < 0) {
            throw new IllegalArgumentException("collector.backpressure.maxPayloadBytes must not be negative");
        }
        if (oversizedPayloadDecision == BackpressureDecision.ACCEPT) {
            throw new IllegalArgumentException(
                    "collector.backpressure.oversizedPayloadDecision must be RETRY_LATER or DROP");
        }
        if (sinkFailureBackpressureThreshold < 0) {
            throw new IllegalArgumentException(
                    "collector.backpressure.sinkFailureThreshold must not be negative");
        }
        if (sinkFailureBackpressureDecision == BackpressureDecision.ACCEPT) {
            throw new IllegalArgumentException(
                    "collector.backpressure.sinkFailureDecision must be RETRY_LATER or DROP");
        }
        if (sinkType == SinkType.FILE && sinkFile == null) {
            throw new IllegalArgumentException("collector.sink.file is required when collector.sink.type=file");
        }
    }

    public static StandaloneCollectorConfig defaults() {
        return new StandaloneCollectorConfig(
                TcpNettyServerConfig.anyAddress(2404),
                SourceId.of("iec104", "station-1"),
                BackpressureDecision.ACCEPT,
                0,
                BackpressureDecision.DROP,
                0,
                BackpressureDecision.RETRY_LATER,
                SinkType.LOGGING,
                null,
                FileSinkRotationConfig.defaults(),
                RuntimeProtocol.IEC104,
                false);
    }

    public static StandaloneCollectorConfig fromArgs(String[] args) {
        return appConfigFromProperties(propertiesFromArgs(args)).singleCollectorConfig();
    }

    public static StandaloneCollectorAppConfig appConfigFromArgs(String[] args) {
        return appConfigFromProperties(propertiesFromArgs(args));
    }

    public static Properties propertiesFromArgs(String[] args) {
        List<Path> configPaths = new ArrayList<>();
        Properties inlineProperties = new Properties();
        if (args == null) {
            return inlineProperties;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--config".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--config requires a file path");
                }
                configPaths.add(Path.of(args[++i]));
                continue;
            }
            if (arg.startsWith("--config=")) {
                configPaths.add(Path.of(arg.substring("--config=".length())));
                continue;
            }
            if ("--profile".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--profile requires a profile name");
                }
                inlineProperties.setProperty(PROFILE, args[++i]);
                continue;
            }
            if (arg.startsWith("--profile=")) {
                inlineProperties.setProperty(PROFILE, arg.substring("--profile=".length()));
                continue;
            }
            if (arg.startsWith("--") && arg.contains("=")) {
                int separator = arg.indexOf('=');
                inlineProperties.setProperty(arg.substring(2, separator), arg.substring(separator + 1));
                continue;
            }
            throw new IllegalArgumentException("Unsupported argument: " + arg);
        }
        Properties properties = new Properties();
        for (Path configPath : configPaths) {
            properties.putAll(loadProperties(configPath));
        }
        String profile = property(inlineProperties, PROFILE, property(properties, PROFILE, null));
        if (profile != null) {
            if (!CollectorDeploymentConfig.isValidProfile(profile)) {
                throw new IllegalArgumentException(
                        PROFILE + " must contain only letters, digits, dash, underscore, or dot");
            }
            properties.setProperty(PROFILE, profile);
            for (Path configPath : configPaths) {
                Path profilePath = profileConfigPath(configPath, profile);
                if (Files.exists(profilePath)) {
                    properties.putAll(loadProperties(profilePath));
                }
            }
            properties.setProperty(PROFILE, profile);
        }
        properties.putAll(inlineProperties);
        return properties;
    }

    public static StandaloneCollectorConfig fromProperties(Properties properties) {
        return appConfigFromProperties(properties).singleCollectorConfig();
    }

    public static StandaloneCollectorAppConfig appConfigFromProperties(Properties properties) {
        ConfigParseResult result = parseAppConfig(properties);
        result.validation().throwIfInvalid();
        return result.config();
    }

    public static CollectorConfigValidation validateProperties(Properties properties) {
        return parseAppConfig(properties).validation();
    }

    private static ConfigParseResult parseAppConfig(Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        List<String> errors = new ArrayList<>();
        StandaloneCollectorConfig defaults = defaults();

        BackpressureDecision backpressure = parseBackpressure(
                property(properties, BACKPRESSURE, defaults.backpressureDecision().name()), errors);
        long backpressureMaxPayloadBytes = longProperty(
                properties,
                BACKPRESSURE_MAX_PAYLOAD_BYTES,
                defaults.backpressureMaxPayloadBytes(),
                0L,
                Long.MAX_VALUE,
                errors);
        BackpressureDecision oversizedPayloadDecision = parseOversizedPayloadDecision(
                property(
                        properties,
                        BACKPRESSURE_OVERSIZED_PAYLOAD_DECISION,
                        defaults.oversizedPayloadDecision().name()),
                errors);
        long sinkFailureBackpressureThreshold = longProperty(
                properties,
                BACKPRESSURE_SINK_FAILURE_THRESHOLD,
                defaults.sinkFailureBackpressureThreshold(),
                0L,
                Long.MAX_VALUE,
                errors);
        BackpressureDecision sinkFailureBackpressureDecision = parseSinkFailureBackpressureDecision(
                property(
                        properties,
                        BACKPRESSURE_SINK_FAILURE_DECISION,
                        defaults.sinkFailureBackpressureDecision().name()),
                errors);
        SinkType sinkType = parseSinkType(property(properties, SINK_TYPE, defaults.sinkType().configValue()), errors);
        Path sinkFile = parseSinkFile(properties, sinkType, errors);
        FileSinkRotationConfig fileSinkRotation = parseFileSinkRotation(properties, defaults.fileSinkRotation(), errors);
        RuntimeProtocol protocol = parseProtocol(
                PROTOCOL,
                property(properties, PROTOCOL, defaults.protocol().configValue()),
                errors);
        boolean strictAsduParsing = parseBoolean(properties, IEC104_STRICT_ASDU, defaults.strictAsduParsing(), errors);
        ManagementServerConfig management = parseManagementConfig(properties, errors);
        CollectorDeploymentConfig deployment = parseDeploymentConfig(properties, errors);

        SourceParseResult sourceResult = parseSources(properties, defaults, protocol, errors);
        boolean httpListenersDeclared = rawProperty(properties, HTTP_LISTENERS) != null;
        boolean kafkaConsumersDeclared = rawProperty(properties, KAFKA_CONSUMERS) != null;
        boolean mqttClientsDeclared = rawProperty(properties, MQTT_CLIENTS) != null;
        List<TcpListenerConfig> tcpListeners = parseTcpListeners(
                properties,
                defaults,
                sourceResult,
                httpListenersDeclared || kafkaConsumersDeclared || mqttClientsDeclared,
                errors);
        List<HttpListenerConfig> httpListeners = parseHttpListeners(properties, sourceResult, errors);
        List<KafkaConsumerConfig> kafkaConsumers = parseKafkaConsumers(properties, sourceResult, errors);
        List<MqttClientConfig> mqttClients = parseMqttClients(properties, sourceResult, errors);
        addDuplicateSourceIdErrors(sourceResult.sources(), errors);
        addDuplicateTcpListenerEndpointErrors(tcpListeners, errors);
        addDuplicateHttpListenerEndpointErrors(httpListeners, errors);
        addDuplicateNetworkListenerEndpointErrors(tcpListeners, httpListeners, errors);
        addManagementEndpointConflictErrors(management, tcpListeners, httpListeners, errors);
        if (tcpListeners.isEmpty() && httpListeners.isEmpty() && kafkaConsumers.isEmpty() && mqttClients.isEmpty()) {
            errors.add("at least one TCP listener, HTTP listener, Kafka consumer, or MQTT client is required");
        }

        if (!errors.isEmpty()) {
            return new ConfigParseResult(null, new CollectorConfigValidation(errors));
        }

        return new ConfigParseResult(
                new StandaloneCollectorAppConfig(
                        sourceResult.sources(),
                        tcpListeners,
                        httpListeners,
                        kafkaConsumers,
                        mqttClients,
                        backpressure,
                        backpressureMaxPayloadBytes,
                        oversizedPayloadDecision,
                        sinkFailureBackpressureThreshold,
                        sinkFailureBackpressureDecision,
                        sinkType,
                        sinkFile,
                        fileSinkRotation,
                        strictAsduParsing,
                        management,
                        deployment),
                CollectorConfigValidation.valid());
    }

    private static CollectorDeploymentConfig parseDeploymentConfig(Properties properties, List<String> errors) {
        CollectorDeploymentConfig defaults = CollectorDeploymentConfig.defaults();
        String profile = property(properties, PROFILE, defaults.profile());
        if (!CollectorDeploymentConfig.isValidProfile(profile)) {
            errors.add(PROFILE + " must contain only letters, digits, dash, underscore, or dot");
            profile = defaults.profile();
        }
        Path runtimeDir = pathProperty(properties, RUNTIME_DIR, defaults.runtimeDir(), errors);
        Path confDir = pathProperty(properties, RUNTIME_CONF_DIR, runtimeDir, runtimeDir.resolve("conf"), errors);
        Path logsDir = pathProperty(properties, RUNTIME_LOGS_DIR, runtimeDir, runtimeDir.resolve("logs"), errors);
        Path dataDir = pathProperty(properties, RUNTIME_DATA_DIR, runtimeDir, runtimeDir.resolve("data"), errors);
        Path runDir = pathProperty(properties, RUNTIME_RUN_DIR, runtimeDir, runtimeDir.resolve("run"), errors);
        Path tmpDir = pathProperty(properties, RUNTIME_TMP_DIR, runtimeDir, runtimeDir.resolve("tmp"), errors);
        Path pidFile = optionalPathProperty(properties, RUNTIME_PID_FILE, runtimeDir, errors);
        Path statusFile = optionalPathProperty(properties, RUNTIME_STATUS_FILE, runtimeDir, errors);
        Path logFile = pathProperty(properties, RUNTIME_LOG_FILE, runtimeDir, logsDir.resolve("protocol-runtime.log"), errors);
        boolean createDirectories = parseBoolean(
                properties,
                RUNTIME_CREATE_DIRECTORIES,
                defaults.createDirectories(),
                errors);
        try {
            return new CollectorDeploymentConfig(
                    profile,
                    runtimeDir,
                    confDir,
                    logsDir,
                    dataDir,
                    runDir,
                    tmpDir,
                    pidFile,
                    statusFile,
                    logFile,
                    createDirectories);
        } catch (IllegalArgumentException ex) {
            errors.add("collector.runtime is invalid: " + ex.getMessage());
            return defaults;
        }
    }

    private static ManagementServerConfig parseManagementConfig(Properties properties, List<String> errors) {
        ManagementServerConfig defaults = ManagementServerConfig.defaults();
        boolean enabled = parseBoolean(properties, MANAGEMENT_ENABLED, defaults.enabled(), errors);
        String host = property(properties, MANAGEMENT_HOST, defaults.host());
        int port = intProperty(properties, MANAGEMENT_PORT, defaults.port(), 0, 65535, errors);
        String healthPath = property(properties, MANAGEMENT_HEALTH_PATH, defaults.healthPath());
        String readinessPath = property(properties, MANAGEMENT_READINESS_PATH, defaults.readinessPath());
        String statusPath = property(properties, MANAGEMENT_STATUS_PATH, defaults.statusPath());
        ManagementAccessMode accessMode = parseManagementAccessMode(
                property(properties, MANAGEMENT_ACCESS, defaults.accessMode().configValue()),
                errors);
        String token = property(properties, MANAGEMENT_TOKEN, defaults.token());
        boolean requestLoggingEnabled = parseBoolean(
                properties,
                MANAGEMENT_REQUEST_LOGGING_ENABLED,
                defaults.requestLoggingEnabled(),
                errors);
        int healthHistoryMaxEntries = intProperty(
                properties,
                MANAGEMENT_HEALTH_HISTORY_MAX_ENTRIES,
                defaults.healthHistoryMaxEntries(),
                0,
                Integer.MAX_VALUE,
                errors);
        try {
            return new ManagementServerConfig(
                    enabled,
                    host,
                    port,
                    healthPath,
                    readinessPath,
                    statusPath,
                    accessMode,
                    token,
                    requestLoggingEnabled,
                    healthHistoryMaxEntries);
        } catch (IllegalArgumentException ex) {
            errors.add("collector.management is invalid: " + ex.getMessage());
            return defaults;
        }
    }

    private static SourceParseResult parseSources(
            Properties properties,
            StandaloneCollectorConfig defaults,
            RuntimeProtocol defaultProtocol,
            List<String> errors) {
        String rawNames = rawProperty(properties, SOURCES);
        List<CollectorSourceConfig> sources = new ArrayList<>();
        Map<String, CollectorSourceConfig> sourceByName = new LinkedHashMap<>();
        if (rawNames == null) {
            SourceId sourceId = parseSourceId(
                    SOURCE_ID,
                    property(properties, SOURCE_ID, defaults.sourceId().qualifiedValue()),
                    errors);
            if (sourceId != null) {
                CollectorSourceConfig source = new CollectorSourceConfig(DEFAULT_SOURCE_NAME, sourceId, defaultProtocol);
                sources.add(source);
                sourceByName.put(DEFAULT_SOURCE_NAME, source);
            }
            return new SourceParseResult(sources, sourceByName, false);
        }
        if (rawNames.isBlank()) {
            errors.add(SOURCES + " must not be blank");
            return new SourceParseResult(sources, sourceByName, true);
        }

        List<String> names = parseNameList(SOURCES, rawNames, errors);
        addDuplicateNameErrors(SOURCES, names, errors);
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                continue;
            }
            String sourceIdKey = sourceIdKey(name);
            SourceId sourceId = parseSourceId(sourceIdKey, property(properties, sourceIdKey, null), errors);
            RuntimeProtocol protocol = parseProtocol(
                    sourceProtocolKey(name),
                    property(properties, sourceProtocolKey(name), defaultProtocol.configValue()),
                    errors);
            if (sourceId != null) {
                CollectorSourceConfig source = new CollectorSourceConfig(name, sourceId, protocol);
                sources.add(source);
                sourceByName.put(name, source);
            }
        }
        return new SourceParseResult(sources, sourceByName, true);
    }

    private static List<TcpListenerConfig> parseTcpListeners(
            Properties properties,
            StandaloneCollectorConfig defaults,
            SourceParseResult sourceResult,
            boolean httpListenersDeclared,
            List<String> errors) {
        String rawNames = rawProperty(properties, TCP_LISTENERS);
        if (rawNames == null) {
            if (httpListenersDeclared) {
                return List.of();
            }
            return parseDefaultTcpListener(properties, defaults, sourceResult, errors);
        }
        if (rawNames.isBlank()) {
            errors.add(TCP_LISTENERS + " must not be blank");
            return List.of();
        }

        List<String> names = parseNameList(TCP_LISTENERS, rawNames, errors);
        addDuplicateNameErrors(TCP_LISTENERS, names, errors);
        List<TcpListenerConfig> listeners = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                continue;
            }
            String prefix = TCP_LISTENER_PREFIX + name;
            String sourceName = property(properties, prefix + TCP_LISTENER_SOURCE_SUFFIX, null);
            if (sourceName == null) {
                errors.add(prefix + TCP_LISTENER_SOURCE_SUFFIX + " is required");
                continue;
            }
            sourceName = normalizeName(prefix + TCP_LISTENER_SOURCE_SUFFIX, sourceName, errors);
            CollectorSourceConfig source = sourceResult.sourceByName().get(sourceName);
            if (sourceName != null && source == null) {
                errors.add(prefix + TCP_LISTENER_SOURCE_SUFFIX + " references unknown source: " + sourceName);
            }

            TcpNettyServerConfig tcp = parseNamedTcpConfig(properties, defaults, prefix, errors);
            if (sourceName != null && source != null && tcp != null) {
                listeners.add(new TcpListenerConfig(name, tcp, sourceName, source.sourceId(), source.protocol()));
            }
        }
        return listeners;
    }

    private static List<HttpListenerConfig> parseHttpListeners(
            Properties properties,
            SourceParseResult sourceResult,
            List<String> errors) {
        String rawNames = rawProperty(properties, HTTP_LISTENERS);
        if (rawNames == null) {
            return List.of();
        }
        if (rawNames.isBlank()) {
            errors.add(HTTP_LISTENERS + " must not be blank");
            return List.of();
        }

        List<String> names = parseNameList(HTTP_LISTENERS, rawNames, errors);
        addDuplicateNameErrors(HTTP_LISTENERS, names, errors);
        List<HttpListenerConfig> listeners = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                continue;
            }
            String prefix = HTTP_LISTENER_PREFIX + name;
            String sourceName = property(properties, prefix + HTTP_LISTENER_SOURCE_SUFFIX, null);
            if (sourceName == null) {
                errors.add(prefix + HTTP_LISTENER_SOURCE_SUFFIX + " is required");
                continue;
            }
            sourceName = normalizeName(prefix + HTTP_LISTENER_SOURCE_SUFFIX, sourceName, errors);
            CollectorSourceConfig source = sourceResult.sourceByName().get(sourceName);
            if (sourceName != null && source == null) {
                errors.add(prefix + HTTP_LISTENER_SOURCE_SUFFIX + " references unknown source: " + sourceName);
            }

            HttpIngressServerConfig http = parseNamedHttpConfig(properties, prefix, source, name, errors);
            if (sourceName != null && source != null && http != null) {
                listeners.add(new HttpListenerConfig(name, http, sourceName, source.sourceId(), source.protocol()));
            }
        }
        return listeners;
    }

    private static List<KafkaConsumerConfig> parseKafkaConsumers(
            Properties properties,
            SourceParseResult sourceResult,
            List<String> errors) {
        String rawNames = rawProperty(properties, KAFKA_CONSUMERS);
        if (rawNames == null) {
            return List.of();
        }
        if (rawNames.isBlank()) {
            errors.add(KAFKA_CONSUMERS + " must not be blank");
            return List.of();
        }

        List<String> names = parseNameList(KAFKA_CONSUMERS, rawNames, errors);
        addDuplicateNameErrors(KAFKA_CONSUMERS, names, errors);
        List<KafkaConsumerConfig> consumers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                continue;
            }
            String prefix = KAFKA_CONSUMER_PREFIX + name;
            String sourceName = property(properties, prefix + KAFKA_CONSUMER_SOURCE_SUFFIX, null);
            if (sourceName == null) {
                errors.add(prefix + KAFKA_CONSUMER_SOURCE_SUFFIX + " is required");
                continue;
            }
            sourceName = normalizeName(prefix + KAFKA_CONSUMER_SOURCE_SUFFIX, sourceName, errors);
            CollectorSourceConfig source = sourceResult.sourceByName().get(sourceName);
            if (sourceName != null && source == null) {
                errors.add(prefix + KAFKA_CONSUMER_SOURCE_SUFFIX + " references unknown source: " + sourceName);
            }

            KafkaIngressConsumerConfig kafka = parseNamedKafkaConfig(properties, prefix, source, name, errors);
            if (sourceName != null && source != null && kafka != null) {
                consumers.add(new KafkaConsumerConfig(name, kafka, sourceName, source.sourceId(), source.protocol()));
            }
        }
        return consumers;
    }

    private static List<MqttClientConfig> parseMqttClients(
            Properties properties,
            SourceParseResult sourceResult,
            List<String> errors) {
        String rawNames = rawProperty(properties, MQTT_CLIENTS);
        if (rawNames == null) {
            return List.of();
        }
        if (rawNames.isBlank()) {
            errors.add(MQTT_CLIENTS + " must not be blank");
            return List.of();
        }

        List<String> names = parseNameList(MQTT_CLIENTS, rawNames, errors);
        addDuplicateNameErrors(MQTT_CLIENTS, names, errors);
        List<MqttClientConfig> clients = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                continue;
            }
            String prefix = MQTT_CLIENT_PREFIX + name;
            String sourceName = property(properties, prefix + MQTT_CLIENT_SOURCE_SUFFIX, null);
            if (sourceName == null) {
                errors.add(prefix + MQTT_CLIENT_SOURCE_SUFFIX + " is required");
                continue;
            }
            sourceName = normalizeName(prefix + MQTT_CLIENT_SOURCE_SUFFIX, sourceName, errors);
            CollectorSourceConfig source = sourceResult.sourceByName().get(sourceName);
            if (sourceName != null && source == null) {
                errors.add(prefix + MQTT_CLIENT_SOURCE_SUFFIX + " references unknown source: " + sourceName);
            }

            MqttIngressClientConfig mqtt = parseNamedMqttConfig(properties, prefix, source, name, errors);
            if (sourceName != null && source != null && mqtt != null) {
                clients.add(new MqttClientConfig(name, mqtt, sourceName, source.sourceId(), source.protocol()));
            }
        }
        return clients;
    }

    private static List<TcpListenerConfig> parseDefaultTcpListener(
            Properties properties,
            StandaloneCollectorConfig defaults,
            SourceParseResult sourceResult,
            List<String> errors) {
        String sourceName = DEFAULT_SOURCE_NAME;
        if (sourceResult.usesNamedSources()) {
            if (sourceResult.sources().size() == 1) {
                sourceName = sourceResult.sources().get(0).name();
            } else {
                errors.add(TCP_LISTENERS + " is required when " + SOURCES + " declares multiple sources");
                return List.of();
            }
        }
        CollectorSourceConfig source = sourceResult.sourceByName().get(sourceName);
        TcpNettyServerConfig tcp = parseLegacyTcpConfig(properties, defaults, errors);
        if (source == null || tcp == null) {
            return List.of();
        }
        return List.of(new TcpListenerConfig(
                DEFAULT_LISTENER_NAME,
                tcp,
                sourceName,
                source.sourceId(),
                source.protocol()));
    }

    private static TcpNettyServerConfig parseLegacyTcpConfig(
            Properties properties,
            StandaloneCollectorConfig defaults,
            List<String> errors) {
        String host = property(properties, TCP_HOST, defaults.tcp().host());
        int port = intProperty(properties, TCP_PORT, defaults.tcp().port(), 0, 65535, errors);
        int bossThreads = intProperty(properties, TCP_BOSS_THREADS, defaults.tcp().bossThreads(), 1, Integer.MAX_VALUE, errors);
        int workerThreads = intProperty(
                properties,
                TCP_WORKER_THREADS,
                defaults.tcp().workerThreads(),
                1,
                Integer.MAX_VALUE,
                errors);
        if (host == null || host.isBlank()) {
            errors.add(TCP_HOST + " must not be blank");
        }
        if (!errors.isEmpty()) {
            return null;
        }
        return new TcpNettyServerConfig(host, port, bossThreads, workerThreads);
    }

    private static TcpNettyServerConfig parseNamedTcpConfig(
            Properties properties,
            StandaloneCollectorConfig defaults,
            String prefix,
            List<String> errors) {
        String hostKey = prefix + TCP_LISTENER_HOST_SUFFIX;
        String portKey = prefix + TCP_LISTENER_PORT_SUFFIX;
        String bossThreadsKey = prefix + TCP_LISTENER_BOSS_THREADS_SUFFIX;
        String workerThreadsKey = prefix + TCP_LISTENER_WORKER_THREADS_SUFFIX;
        String host = property(properties, hostKey, defaults.tcp().host());
        int port = requiredIntProperty(properties, portKey, 0, 65535, errors);
        int bossThreads = intProperty(properties, bossThreadsKey, defaults.tcp().bossThreads(), 1, Integer.MAX_VALUE, errors);
        int workerThreads = intProperty(
                properties,
                workerThreadsKey,
                defaults.tcp().workerThreads(),
                1,
                Integer.MAX_VALUE,
                errors);
        if (host == null || host.isBlank()) {
            errors.add(hostKey + " must not be blank");
        }
        if (hasErrorForPrefix(errors, prefix)) {
            return null;
        }
        return new TcpNettyServerConfig(host, port, bossThreads, workerThreads);
    }

    private static HttpIngressServerConfig parseNamedHttpConfig(
            Properties properties,
            String prefix,
            CollectorSourceConfig source,
            String listenerName,
            List<String> errors) {
        String hostKey = prefix + HTTP_LISTENER_HOST_SUFFIX;
        String portKey = prefix + HTTP_LISTENER_PORT_SUFFIX;
        String pathKey = prefix + HTTP_LISTENER_PATH_SUFFIX;
        String sourceIdModeKey = prefix + HTTP_LISTENER_SOURCE_ID_MODE_SUFFIX;
        String sourceIdHeaderKey = prefix + HTTP_LISTENER_SOURCE_ID_HEADER_SUFFIX;
        String maxPayloadBytesKey = prefix + HTTP_LISTENER_MAX_PAYLOAD_BYTES_SUFFIX;
        String responseModeKey = prefix + HTTP_LISTENER_RESPONSE_MODE_SUFFIX;
        String backlogKey = prefix + HTTP_LISTENER_BACKLOG_SUFFIX;
        String workerThreadsKey = prefix + HTTP_LISTENER_WORKER_THREADS_SUFFIX;

        String host = property(properties, hostKey, DEFAULT_HTTP_HOST);
        int port = requiredIntProperty(properties, portKey, 0, 65535, errors);
        String path = property(properties, pathKey, DEFAULT_HTTP_PATH);
        HttpIngressSourceIdMode sourceIdMode = parseHttpSourceIdMode(
                sourceIdModeKey,
                property(properties, sourceIdModeKey, HttpIngressSourceIdMode.CONFIGURED.name()),
                errors);
        String sourceIdHeader = property(properties, sourceIdHeaderKey, null);
        int maxPayloadBytes = intProperty(properties, maxPayloadBytesKey, 0, 0, Integer.MAX_VALUE, errors);
        HttpIngressResponseMode responseMode = parseHttpResponseMode(
                responseModeKey,
                property(properties, responseModeKey, HttpIngressResponseMode.ACK_ON_ACCEPT.name()),
                errors);
        int backlog = intProperty(properties, backlogKey, 0, 0, Integer.MAX_VALUE, errors);
        int workerThreads = intProperty(properties, workerThreadsKey, 1, 1, Integer.MAX_VALUE, errors);

        if (host == null || host.isBlank()) {
            errors.add(hostKey + " must not be blank");
        }
        if (path == null || path.isBlank()) {
            errors.add(pathKey + " must not be blank");
        }
        if (hasErrorForPrefix(errors, prefix) || source == null) {
            return null;
        }
        SourceId configuredSourceId =
                sourceIdMode == HttpIngressSourceIdMode.CONFIGURED ? source.sourceId() : null;
        try {
            return new HttpIngressServerConfig(
                    host,
                    port,
                    path,
                    sourceIdMode,
                    sourceIdHeader,
                    configuredSourceId,
                    maxPayloadBytes,
                    responseMode,
                    backlog,
                    workerThreads,
                    listenerName);
        } catch (IllegalArgumentException ex) {
            errors.add(prefix + " is invalid: " + ex.getMessage());
            return null;
        }
    }

    private static KafkaIngressConsumerConfig parseNamedKafkaConfig(
            Properties properties,
            String prefix,
            CollectorSourceConfig source,
            String consumerName,
            List<String> errors) {
        String bootstrapServersKey = prefix + KAFKA_CONSUMER_BOOTSTRAP_SERVERS_SUFFIX;
        String groupIdKey = prefix + KAFKA_CONSUMER_GROUP_ID_SUFFIX;
        String topicsKey = prefix + KAFKA_CONSUMER_TOPICS_SUFFIX;
        String topicPatternKey = prefix + KAFKA_CONSUMER_TOPIC_PATTERN_SUFFIX;
        String sourceIdModeKey = prefix + KAFKA_CONSUMER_SOURCE_ID_MODE_SUFFIX;
        String sourceIdHeaderKey = prefix + KAFKA_CONSUMER_SOURCE_ID_HEADER_SUFFIX;
        String commitModeKey = prefix + KAFKA_CONSUMER_COMMIT_MODE_SUFFIX;
        String autoOffsetResetKey = prefix + KAFKA_CONSUMER_AUTO_OFFSET_RESET_SUFFIX;
        String maxPollRecordsKey = prefix + KAFKA_CONSUMER_MAX_POLL_RECORDS_SUFFIX;
        String pollTimeoutMillisKey = prefix + KAFKA_CONSUMER_POLL_TIMEOUT_MILLIS_SUFFIX;

        String bootstrapServers = requiredStringProperty(properties, bootstrapServersKey, errors);
        String groupId = requiredStringProperty(properties, groupIdKey, errors);
        List<String> topics = parseOptionalValueList(properties, topicsKey, errors);
        String topicPattern = property(properties, topicPatternKey, null);
        KafkaSourceIdMode sourceIdMode = parseKafkaSourceIdMode(
                sourceIdModeKey,
                property(properties, sourceIdModeKey, KafkaSourceIdMode.CONFIGURED.name()),
                errors);
        String sourceIdHeader = property(properties, sourceIdHeaderKey, null);
        KafkaCommitMode commitMode = parseKafkaCommitMode(
                commitModeKey,
                property(properties, commitModeKey, KafkaCommitMode.MANUAL.name()),
                errors);
        String autoOffsetReset = property(properties, autoOffsetResetKey, "latest");
        int maxPollRecords = intProperty(properties, maxPollRecordsKey, 100, 1, Integer.MAX_VALUE, errors);
        long pollTimeoutMillis = longProperty(properties, pollTimeoutMillisKey, 1000L, 1L, Long.MAX_VALUE, errors);

        if (hasErrorForPrefix(errors, prefix) || source == null) {
            return null;
        }
        SourceId configuredSourceId = sourceIdMode == KafkaSourceIdMode.CONFIGURED ? source.sourceId() : null;
        try {
            return new KafkaIngressConsumerConfig(
                    consumerName,
                    bootstrapServers,
                    groupId,
                    topics,
                    topicPattern,
                    sourceIdMode,
                    sourceIdHeader,
                    configuredSourceId,
                    source.protocol().configValue(),
                    commitMode,
                    autoOffsetReset,
                    maxPollRecords,
                    pollTimeoutMillis);
        } catch (IllegalArgumentException ex) {
            errors.add(prefix + " is invalid: " + ex.getMessage());
            return null;
        }
    }

    private static MqttIngressClientConfig parseNamedMqttConfig(
            Properties properties,
            String prefix,
            CollectorSourceConfig source,
            String clientName,
            List<String> errors) {
        String brokerUriKey = prefix + MQTT_CLIENT_BROKER_URI_SUFFIX;
        String clientIdKey = prefix + MQTT_CLIENT_CLIENT_ID_SUFFIX;
        String topicFiltersKey = prefix + MQTT_CLIENT_TOPIC_FILTERS_SUFFIX;
        String qosKey = prefix + MQTT_CLIENT_QOS_SUFFIX;
        String sourceIdModeKey = prefix + MQTT_CLIENT_SOURCE_ID_MODE_SUFFIX;
        String cleanSessionKey = prefix + MQTT_CLIENT_CLEAN_SESSION_SUFFIX;
        String automaticReconnectKey = prefix + MQTT_CLIENT_AUTOMATIC_RECONNECT_SUFFIX;
        String connectionTimeoutSecondsKey = prefix + MQTT_CLIENT_CONNECTION_TIMEOUT_SECONDS_SUFFIX;
        String keepAliveSecondsKey = prefix + MQTT_CLIENT_KEEP_ALIVE_SECONDS_SUFFIX;

        String brokerUri = requiredStringProperty(properties, brokerUriKey, errors);
        String clientId = requiredStringProperty(properties, clientIdKey, errors);
        List<String> topicFilters = parseOptionalValueList(properties, topicFiltersKey, errors);
        int qos = intProperty(properties, qosKey, 0, 0, 2, errors);
        MqttSourceIdMode sourceIdMode = parseMqttSourceIdMode(
                sourceIdModeKey,
                property(properties, sourceIdModeKey, MqttSourceIdMode.CONFIGURED.name()),
                errors);
        boolean cleanSession = parseBoolean(properties, cleanSessionKey, true, errors);
        boolean automaticReconnect = parseBoolean(properties, automaticReconnectKey, true, errors);
        int connectionTimeoutSeconds = intProperty(
                properties,
                connectionTimeoutSecondsKey,
                30,
                1,
                Integer.MAX_VALUE,
                errors);
        int keepAliveSeconds = intProperty(properties, keepAliveSecondsKey, 60, 1, Integer.MAX_VALUE, errors);

        if (hasErrorForPrefix(errors, prefix) || source == null) {
            return null;
        }
        SourceId configuredSourceId = sourceIdMode == MqttSourceIdMode.CONFIGURED ? source.sourceId() : null;
        try {
            return new MqttIngressClientConfig(
                    clientName,
                    brokerUri,
                    clientId,
                    topicFilters,
                    qos,
                    sourceIdMode,
                    configuredSourceId,
                    source.protocol().configValue(),
                    cleanSession,
                    automaticReconnect,
                    connectionTimeoutSeconds,
                    keepAliveSeconds);
        } catch (IllegalArgumentException ex) {
            errors.add(prefix + " is invalid: " + ex.getMessage());
            return null;
        }
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to load collector config: " + path, ex);
        }
    }

    private static Path profileConfigPath(Path configPath, String profile) {
        Path fileName = configPath.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("--config must point to a file path");
        }
        String name = fileName.toString();
        String stem = name.endsWith(".properties")
                ? name.substring(0, name.length() - ".properties".length())
                : name;
        return configPath.resolveSibling(stem + "-" + profile + ".properties");
    }

    private static SourceId parseSourceId(String key, String value, List<String> errors) {
        String trimmed = value == null ? null : value.trim();
        int separator = trimmed == null ? -1 : trimmed.indexOf(':');
        if (separator <= 0 || separator == trimmed.length() - 1) {
            errors.add(key + " must use namespace:value format");
            return null;
        }
        try {
            return SourceId.of(trimmed.substring(0, separator), trimmed.substring(separator + 1));
        } catch (RuntimeException ex) {
            errors.add(key + " must use namespace:value format");
            return null;
        }
    }

    private static BackpressureDecision parseBackpressure(String value, List<String> errors) {
        try {
            return BackpressureDecision.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            errors.add(BACKPRESSURE + " must be ACCEPT, RETRY_LATER, or DROP");
            return BackpressureDecision.ACCEPT;
        }
    }

    private static BackpressureDecision parseOversizedPayloadDecision(String value, List<String> errors) {
        BackpressureDecision decision;
        try {
            decision = BackpressureDecision.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            errors.add(BACKPRESSURE_OVERSIZED_PAYLOAD_DECISION + " must be RETRY_LATER or DROP");
            return BackpressureDecision.DROP;
        }
        if (decision == BackpressureDecision.ACCEPT) {
            errors.add(BACKPRESSURE_OVERSIZED_PAYLOAD_DECISION + " must be RETRY_LATER or DROP");
            return BackpressureDecision.DROP;
        }
        return decision;
    }

    private static BackpressureDecision parseSinkFailureBackpressureDecision(String value, List<String> errors) {
        BackpressureDecision decision;
        try {
            decision = BackpressureDecision.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            errors.add(BACKPRESSURE_SINK_FAILURE_DECISION + " must be RETRY_LATER or DROP");
            return BackpressureDecision.RETRY_LATER;
        }
        if (decision == BackpressureDecision.ACCEPT) {
            errors.add(BACKPRESSURE_SINK_FAILURE_DECISION + " must be RETRY_LATER or DROP");
            return BackpressureDecision.RETRY_LATER;
        }
        return decision;
    }

    private static SinkType parseSinkType(String value, List<String> errors) {
        try {
            return SinkType.parse(value);
        } catch (RuntimeException ex) {
            errors.add(SINK_TYPE + " must be logging, file, or in-memory");
            return SinkType.LOGGING;
        }
    }

    private static RuntimeProtocol parseProtocol(String key, String value, List<String> errors) {
        try {
            return RuntimeProtocol.parse(value);
        } catch (RuntimeException ex) {
            errors.add(key + " must be iec104, iec101, iec103, or modbus");
            return RuntimeProtocol.IEC104;
        }
    }

    private static HttpIngressSourceIdMode parseHttpSourceIdMode(String key, String value, List<String> errors) {
        try {
            return HttpIngressSourceIdMode.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            errors.add(key + " must be CONFIGURED, HEADER, or PATH");
            return HttpIngressSourceIdMode.CONFIGURED;
        }
    }

    private static ManagementAccessMode parseManagementAccessMode(String value, List<String> errors) {
        try {
            return ManagementAccessMode.parse(value);
        } catch (RuntimeException ex) {
            errors.add(MANAGEMENT_ACCESS + " must be local, open, or token");
            return ManagementServerConfig.DEFAULT_ACCESS_MODE;
        }
    }

    private static HttpIngressResponseMode parseHttpResponseMode(String key, String value, List<String> errors) {
        try {
            return HttpIngressResponseMode.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            errors.add(key + " must be ACK_ON_ACCEPT or NO_BODY");
            return HttpIngressResponseMode.ACK_ON_ACCEPT;
        }
    }

    private static KafkaSourceIdMode parseKafkaSourceIdMode(String key, String value, List<String> errors) {
        try {
            return KafkaSourceIdMode.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            errors.add(key + " must be CONFIGURED, HEADER, TOPIC, or KEY");
            return KafkaSourceIdMode.CONFIGURED;
        }
    }

    private static MqttSourceIdMode parseMqttSourceIdMode(String key, String value, List<String> errors) {
        try {
            return MqttSourceIdMode.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            errors.add(key + " must be CONFIGURED or TOPIC");
            return MqttSourceIdMode.CONFIGURED;
        }
    }

    private static KafkaCommitMode parseKafkaCommitMode(String key, String value, List<String> errors) {
        try {
            return KafkaCommitMode.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            errors.add(key + " must be MANUAL, AFTER_ACCEPT, AFTER_PARSE_SUCCESS, or NEVER");
            return KafkaCommitMode.MANUAL;
        }
    }

    private static Path parseSinkFile(Properties properties, SinkType sinkType, List<String> errors) {
        String value = rawProperty(properties, SINK_FILE);
        Path sinkFile = null;
        if (value != null && !value.isBlank()) {
            try {
                sinkFile = Path.of(value.trim());
            } catch (InvalidPathException ex) {
                errors.add(SINK_FILE + " must be a valid file path");
            }
        }
        if (sinkType == SinkType.FILE && sinkFile == null) {
            errors.add(SINK_FILE + " is required when " + SINK_TYPE + "=file");
        }
        if (sinkFile != null) {
            if (Files.isDirectory(sinkFile)) {
                errors.add(SINK_FILE + " must point to a file, not a directory");
            }
            Path parent = sinkFile.toAbsolutePath().getParent();
            if (parent != null && Files.exists(parent) && !Files.isDirectory(parent)) {
                errors.add(SINK_FILE + " parent must be a directory");
            }
        }
        return sinkFile;
    }

    private static Path pathProperty(Properties properties, String key, Path defaultValue, List<String> errors) {
        return pathProperty(properties, key, null, defaultValue, errors);
    }

    private static Path pathProperty(
            Properties properties,
            String key,
            Path baseDir,
            Path defaultValue,
            List<String> errors) {
        String value = property(properties, key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            Path path = Path.of(value);
            return baseDir != null && !path.isAbsolute() ? baseDir.resolve(path) : path;
        } catch (InvalidPathException ex) {
            errors.add(key + " must be a valid path");
            return defaultValue;
        }
    }

    private static Path optionalPathProperty(Properties properties, String key, Path baseDir, List<String> errors) {
        String value = property(properties, key, null);
        if (value == null) {
            return null;
        }
        try {
            Path path = Path.of(value);
            return baseDir != null && !path.isAbsolute() ? baseDir.resolve(path) : path;
        } catch (InvalidPathException ex) {
            errors.add(key + " must be a valid path");
            return null;
        }
    }

    private static FileSinkRotationConfig parseFileSinkRotation(
            Properties properties,
            FileSinkRotationConfig defaults,
            List<String> errors) {
        long maxBytes = longProperty(
                properties,
                SINK_FILE_MAX_BYTES,
                defaults.maxBytes(),
                1L,
                Long.MAX_VALUE,
                errors);
        int maxHistory = intProperty(
                properties,
                SINK_FILE_MAX_HISTORY,
                defaults.maxHistory(),
                1,
                Integer.MAX_VALUE,
                errors);
        return new FileSinkRotationConfig(maxBytes, maxHistory);
    }

    private static boolean parseBoolean(Properties properties, String key, boolean defaultValue, List<String> errors) {
        String value = property(properties, key, null);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        errors.add(key + " must be true or false");
        return defaultValue;
    }

    private static List<String> parseNameList(String key, String value, List<String> errors) {
        List<String> names = new ArrayList<>();
        String[] parts = value.split(",", -1);
        for (String part : parts) {
            if (part.isBlank()) {
                errors.add(key + " must not contain blank names");
                continue;
            }
            String name = normalizeName(key, part, errors);
            if (name != null) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            errors.add(key + " must not be empty");
        }
        return names;
    }

    private static List<String> parseOptionalValueList(Properties properties, String key, List<String> errors) {
        String value = rawProperty(properties, key);
        if (value == null) {
            return List.of();
        }
        return parseNameList(key, value, errors);
    }

    private static String normalizeName(String key, String value, List<String> errors) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isBlank()) {
            errors.add(key + " must not be blank");
            return null;
        }
        if (containsWhitespace(trimmed) || trimmed.contains(",")) {
            errors.add(key + " names must not contain whitespace or comma: " + value);
            return null;
        }
        return trimmed;
    }

    private static void addDuplicateNameErrors(String key, List<String> names, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                errors.add(key + " contains duplicate name: " + name);
            }
        }
    }

    private static void addDuplicateSourceIdErrors(List<CollectorSourceConfig> sources, List<String> errors) {
        Map<String, String> seen = new HashMap<>();
        for (CollectorSourceConfig source : sources) {
            String qualified = source.sourceId().qualifiedValue();
            String previous = seen.putIfAbsent(qualified, source.name());
            if (previous != null) {
                errors.add("duplicate source id " + qualified + " for sources " + previous + " and " + source.name());
            }
        }
    }

    private static void addDuplicateTcpListenerEndpointErrors(List<TcpListenerConfig> listeners, List<String> errors) {
        Map<String, String> seen = new HashMap<>();
        for (TcpListenerConfig listener : listeners) {
            if (listener.tcp().port() == 0) {
                continue;
            }
            String endpoint = listener.tcp().host() + ":" + listener.tcp().port();
            String previous = seen.putIfAbsent(endpoint, listener.name());
            if (previous != null) {
                errors.add("duplicate TCP listener endpoint " + endpoint
                        + " for listeners " + previous + " and " + listener.name());
            }
        }
    }

    private static void addDuplicateHttpListenerEndpointErrors(List<HttpListenerConfig> listeners, List<String> errors) {
        Map<String, String> seen = new HashMap<>();
        for (HttpListenerConfig listener : listeners) {
            if (listener.http().port() == 0) {
                continue;
            }
            String endpoint = listener.http().host() + ":" + listener.http().port();
            String previous = seen.putIfAbsent(endpoint, listener.name());
            if (previous != null) {
                errors.add("duplicate HTTP listener endpoint " + endpoint
                        + " for listeners " + previous + " and " + listener.name());
            }
        }
    }

    private static void addDuplicateNetworkListenerEndpointErrors(
            List<TcpListenerConfig> tcpListeners,
            List<HttpListenerConfig> httpListeners,
            List<String> errors) {
        Map<String, String> seen = new HashMap<>();
        for (TcpListenerConfig listener : tcpListeners) {
            if (listener.tcp().port() == 0) {
                continue;
            }
            seen.putIfAbsent(
                    listener.tcp().host() + ":" + listener.tcp().port(),
                    "TCP listener " + listener.name());
        }
        for (HttpListenerConfig listener : httpListeners) {
            if (listener.http().port() == 0) {
                continue;
            }
            String endpoint = listener.http().host() + ":" + listener.http().port();
            String previous = seen.putIfAbsent(endpoint, "HTTP listener " + listener.name());
            if (previous != null && !previous.equals("HTTP listener " + listener.name())) {
                errors.add("duplicate network listener endpoint " + endpoint
                        + " for " + previous + " and HTTP listener " + listener.name());
            }
        }
    }

    private static void addManagementEndpointConflictErrors(
            ManagementServerConfig management,
            List<TcpListenerConfig> tcpListeners,
            List<HttpListenerConfig> httpListeners,
            List<String> errors) {
        if (!management.enabled() || management.port() == 0) {
            return;
        }
        String endpoint = management.host() + ":" + management.port();
        for (TcpListenerConfig listener : tcpListeners) {
            if (listener.tcp().port() != 0
                    && endpoint.equals(listener.tcp().host() + ":" + listener.tcp().port())) {
                errors.add("management endpoint " + endpoint
                        + " conflicts with TCP listener " + listener.name());
            }
        }
        for (HttpListenerConfig listener : httpListeners) {
            if (listener.http().port() != 0
                    && endpoint.equals(listener.http().host() + ":" + listener.http().port())) {
                errors.add("management endpoint " + endpoint
                        + " conflicts with HTTP ingress listener " + listener.name());
            }
        }
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String property(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String rawProperty(Properties properties, String key) {
        return properties.getProperty(key);
    }

    private static String requiredStringProperty(Properties properties, String key, List<String> errors) {
        String value = property(properties, key, null);
        if (value == null) {
            errors.add(key + " is required");
        }
        return value;
    }

    private static int intProperty(
            Properties properties,
            String key,
            int defaultValue,
            int minInclusive,
            int maxInclusive,
            List<String> errors) {
        String value = property(properties, key, null);
        if (value == null) {
            return defaultValue;
        }
        return parseInt(key, value, defaultValue, minInclusive, maxInclusive, errors);
    }

    private static long longProperty(
            Properties properties,
            String key,
            long defaultValue,
            long minInclusive,
            long maxInclusive,
            List<String> errors) {
        String value = property(properties, key, null);
        if (value == null) {
            return defaultValue;
        }
        return parseLong(key, value, defaultValue, minInclusive, maxInclusive, errors);
    }

    private static int requiredIntProperty(
            Properties properties,
            String key,
            int minInclusive,
            int maxInclusive,
            List<String> errors) {
        String value = property(properties, key, null);
        if (value == null) {
            errors.add(key + " is required");
            return minInclusive;
        }
        return parseInt(key, value, minInclusive, minInclusive, maxInclusive, errors);
    }

    private static int parseInt(
            String key,
            String value,
            int fallback,
            int minInclusive,
            int maxInclusive,
            List<String> errors) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            errors.add(key + " must be an integer");
            return fallback;
        }
        if (parsed < minInclusive || parsed > maxInclusive) {
            if (minInclusive == 0 && maxInclusive == 65535) {
                errors.add(key + " must be between 0 and 65535");
            } else if (minInclusive == 1) {
                errors.add(key + " must be positive");
            } else {
                errors.add(key + " must be between " + minInclusive + " and " + maxInclusive);
            }
            return fallback;
        }
        return parsed;
    }

    private static long parseLong(
            String key,
            String value,
            long fallback,
            long minInclusive,
            long maxInclusive,
            List<String> errors) {
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            errors.add(key + " must be an integer");
            return fallback;
        }
        if (parsed < minInclusive || parsed > maxInclusive) {
            if (minInclusive == 1) {
                errors.add(key + " must be positive");
            } else {
                errors.add(key + " must be between " + minInclusive + " and " + maxInclusive);
            }
            return fallback;
        }
        return parsed;
    }

    private static boolean hasErrorForPrefix(List<String> errors, String prefix) {
        for (String error : errors) {
            if (error.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String sourceIdKey(String sourceName) {
        return SOURCE_PREFIX + sourceName + SOURCE_ID_SUFFIX;
    }

    private static String sourceProtocolKey(String sourceName) {
        return SOURCE_PREFIX + sourceName + SOURCE_PROTOCOL_SUFFIX;
    }

    private record ConfigParseResult(StandaloneCollectorAppConfig config, CollectorConfigValidation validation) {
    }

    private record SourceParseResult(
            List<CollectorSourceConfig> sources,
            Map<String, CollectorSourceConfig> sourceByName,
            boolean usesNamedSources) {
    }
}
