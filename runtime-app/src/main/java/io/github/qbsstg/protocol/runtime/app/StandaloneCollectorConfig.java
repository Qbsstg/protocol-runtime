package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.SourceId;
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
        SinkType sinkType,
        Path sinkFile,
        FileSinkRotationConfig fileSinkRotation,
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
    public static final String SOURCES = "collector.sources";
    public static final String SOURCE_ID = "collector.source.id";
    public static final String SOURCE_PREFIX = "collector.source.";
    public static final String SOURCE_ID_SUFFIX = ".id";
    public static final String BACKPRESSURE = "collector.backpressure";
    public static final String SINK_TYPE = "collector.sink.type";
    public static final String SINK_FILE = "collector.sink.file";
    public static final String SINK_FILE_MAX_BYTES = "collector.sink.file.maxBytes";
    public static final String SINK_FILE_MAX_HISTORY = "collector.sink.file.maxHistory";
    public static final String IEC104_STRICT_ASDU = "collector.iec104.strictAsduParsing";

    private static final String DEFAULT_SOURCE_NAME = "default";
    private static final String DEFAULT_LISTENER_NAME = "default";

    public StandaloneCollectorConfig {
        Objects.requireNonNull(tcp, "tcp must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(backpressureDecision, "backpressureDecision must not be null");
        Objects.requireNonNull(sinkType, "sinkType must not be null");
        Objects.requireNonNull(fileSinkRotation, "fileSinkRotation must not be null");
        if (sinkType == SinkType.FILE && sinkFile == null) {
            throw new IllegalArgumentException("collector.sink.file is required when collector.sink.type=file");
        }
    }

    public static StandaloneCollectorConfig defaults() {
        return new StandaloneCollectorConfig(
                TcpNettyServerConfig.anyAddress(2404),
                SourceId.of("iec104", "station-1"),
                BackpressureDecision.ACCEPT,
                SinkType.LOGGING,
                null,
                FileSinkRotationConfig.defaults(),
                false);
    }

    public static StandaloneCollectorConfig fromArgs(String[] args) {
        return appConfigFromProperties(propertiesFromArgs(args)).singleCollectorConfig();
    }

    public static StandaloneCollectorAppConfig appConfigFromArgs(String[] args) {
        return appConfigFromProperties(propertiesFromArgs(args));
    }

    public static Properties propertiesFromArgs(String[] args) {
        Properties properties = new Properties();
        if (args == null) {
            return properties;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--config".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--config requires a file path");
                }
                properties.putAll(loadProperties(Path.of(args[++i])));
                continue;
            }
            if (arg.startsWith("--config=")) {
                properties.putAll(loadProperties(Path.of(arg.substring("--config=".length()))));
                continue;
            }
            if (arg.startsWith("--") && arg.contains("=")) {
                int separator = arg.indexOf('=');
                properties.setProperty(arg.substring(2, separator), arg.substring(separator + 1));
                continue;
            }
            throw new IllegalArgumentException("Unsupported argument: " + arg);
        }
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
        SinkType sinkType = parseSinkType(property(properties, SINK_TYPE, defaults.sinkType().configValue()), errors);
        Path sinkFile = parseSinkFile(properties, sinkType, errors);
        FileSinkRotationConfig fileSinkRotation = parseFileSinkRotation(properties, defaults.fileSinkRotation(), errors);
        boolean strictAsduParsing = parseBoolean(properties, IEC104_STRICT_ASDU, defaults.strictAsduParsing(), errors);

        SourceParseResult sourceResult = parseSources(properties, defaults, errors);
        List<TcpListenerConfig> listeners = parseTcpListeners(properties, defaults, sourceResult, errors);
        addDuplicateSourceIdErrors(sourceResult.sources(), errors);
        addDuplicateListenerEndpointErrors(listeners, errors);

        if (!errors.isEmpty()) {
            return new ConfigParseResult(null, new CollectorConfigValidation(errors));
        }

        return new ConfigParseResult(
                new StandaloneCollectorAppConfig(
                        sourceResult.sources(),
                        listeners,
                        backpressure,
                        sinkType,
                        sinkFile,
                        fileSinkRotation,
                        strictAsduParsing),
                CollectorConfigValidation.valid());
    }

    private static SourceParseResult parseSources(
            Properties properties,
            StandaloneCollectorConfig defaults,
            List<String> errors) {
        String rawNames = rawProperty(properties, SOURCES);
        List<CollectorSourceConfig> sources = new ArrayList<>();
        Map<String, SourceId> sourceByName = new LinkedHashMap<>();
        if (rawNames == null) {
            SourceId sourceId = parseSourceId(
                    SOURCE_ID,
                    property(properties, SOURCE_ID, defaults.sourceId().qualifiedValue()),
                    errors);
            if (sourceId != null) {
                sources.add(new CollectorSourceConfig(DEFAULT_SOURCE_NAME, sourceId));
                sourceByName.put(DEFAULT_SOURCE_NAME, sourceId);
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
            if (sourceId != null) {
                sources.add(new CollectorSourceConfig(name, sourceId));
                sourceByName.put(name, sourceId);
            }
        }
        return new SourceParseResult(sources, sourceByName, true);
    }

    private static List<TcpListenerConfig> parseTcpListeners(
            Properties properties,
            StandaloneCollectorConfig defaults,
            SourceParseResult sourceResult,
            List<String> errors) {
        String rawNames = rawProperty(properties, TCP_LISTENERS);
        if (rawNames == null) {
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
            SourceId sourceId = sourceResult.sourceByName().get(sourceName);
            if (sourceName != null && sourceId == null) {
                errors.add(prefix + TCP_LISTENER_SOURCE_SUFFIX + " references unknown source: " + sourceName);
            }

            TcpNettyServerConfig tcp = parseNamedTcpConfig(properties, defaults, prefix, errors);
            if (sourceName != null && sourceId != null && tcp != null) {
                listeners.add(new TcpListenerConfig(name, tcp, sourceName, sourceId));
            }
        }
        return listeners;
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
        SourceId sourceId = sourceResult.sourceByName().get(sourceName);
        TcpNettyServerConfig tcp = parseLegacyTcpConfig(properties, defaults, errors);
        if (sourceId == null || tcp == null) {
            return List.of();
        }
        return List.of(new TcpListenerConfig(DEFAULT_LISTENER_NAME, tcp, sourceName, sourceId));
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

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to load collector config: " + path, ex);
        }
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

    private static SinkType parseSinkType(String value, List<String> errors) {
        try {
            return SinkType.parse(value);
        } catch (RuntimeException ex) {
            errors.add(SINK_TYPE + " must be logging, file, or in-memory");
            return SinkType.LOGGING;
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

    private static void addDuplicateListenerEndpointErrors(List<TcpListenerConfig> listeners, List<String> errors) {
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

    private record ConfigParseResult(StandaloneCollectorAppConfig config, CollectorConfigValidation validation) {
    }

    private record SourceParseResult(
            List<CollectorSourceConfig> sources,
            Map<String, SourceId> sourceByName,
            boolean usesNamedSources) {
    }
}
