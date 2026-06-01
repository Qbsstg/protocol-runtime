package io.github.qbsstg.protocol.runtime.app;

import io.github.qbsstg.protocol.runtime.core.BackpressureDecision;
import io.github.qbsstg.protocol.runtime.core.SourceId;
import io.github.qbsstg.protocol.runtime.ingress.tcp.netty.TcpNettyServerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

public record StandaloneCollectorConfig(
        TcpNettyServerConfig tcp,
        SourceId sourceId,
        BackpressureDecision backpressureDecision,
        SinkType sinkType,
        Path sinkFile,
        boolean strictAsduParsing) {

    public static final String TCP_HOST = "collector.tcp.host";
    public static final String TCP_PORT = "collector.tcp.port";
    public static final String TCP_BOSS_THREADS = "collector.tcp.bossThreads";
    public static final String TCP_WORKER_THREADS = "collector.tcp.workerThreads";
    public static final String SOURCE_ID = "collector.source.id";
    public static final String BACKPRESSURE = "collector.backpressure";
    public static final String SINK_TYPE = "collector.sink.type";
    public static final String SINK_FILE = "collector.sink.file";
    public static final String IEC104_STRICT_ASDU = "collector.iec104.strictAsduParsing";

    public StandaloneCollectorConfig {
        Objects.requireNonNull(tcp, "tcp must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(backpressureDecision, "backpressureDecision must not be null");
        Objects.requireNonNull(sinkType, "sinkType must not be null");
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
                false);
    }

    public static StandaloneCollectorConfig fromArgs(String[] args) {
        Properties properties = new Properties();
        if (args == null) {
            return fromProperties(properties);
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
        return fromProperties(properties);
    }

    public static StandaloneCollectorConfig fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        StandaloneCollectorConfig defaults = defaults();
        String host = property(properties, TCP_HOST, defaults.tcp().host());
        int port = intProperty(properties, TCP_PORT, defaults.tcp().port());
        int bossThreads = intProperty(properties, TCP_BOSS_THREADS, defaults.tcp().bossThreads());
        int workerThreads = intProperty(properties, TCP_WORKER_THREADS, defaults.tcp().workerThreads());
        SinkType sinkType = SinkType.parse(property(properties, SINK_TYPE, defaults.sinkType().configValue()));
        String sinkFileValue = property(properties, SINK_FILE, null);
        return new StandaloneCollectorConfig(
                new TcpNettyServerConfig(host, port, bossThreads, workerThreads),
                parseSourceId(property(properties, SOURCE_ID, defaults.sourceId().qualifiedValue())),
                parseBackpressure(property(properties, BACKPRESSURE, defaults.backpressureDecision().name())),
                sinkType,
                sinkFileValue == null || sinkFileValue.isBlank() ? null : Path.of(sinkFileValue),
                booleanProperty(properties, IEC104_STRICT_ASDU, defaults.strictAsduParsing()));
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

    private static SourceId parseSourceId(String value) {
        int separator = value == null ? -1 : value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("collector.source.id must use namespace:value format");
        }
        return SourceId.of(value.substring(0, separator), value.substring(separator + 1));
    }

    private static BackpressureDecision parseBackpressure(String value) {
        try {
            return BackpressureDecision.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "collector.backpressure must be ACCEPT, RETRY_LATER, or DROP", ex);
        }
    }

    private static String property(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int intProperty(Properties properties, String key, int defaultValue) {
        String value = property(properties, key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer", ex);
        }
    }

    private static boolean booleanProperty(Properties properties, String key, boolean defaultValue) {
        String value = property(properties, key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }
}
