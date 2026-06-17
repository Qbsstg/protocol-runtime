package io.github.qbsstg.protocol.runtime.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

final class RuntimeOperationsChecks {
    private static final List<String> PACKAGE_LAYOUT_PATHS = List.of(
            "bin",
            "conf",
            "lib",
            "logs",
            "data",
            "run",
            "tmp",
            "docs",
            "examples");

    private RuntimeOperationsChecks() {
    }

    static String selfCheck(
            RuntimeOperationsContext context,
            Properties properties,
            CollectorConfigValidation validation,
            StandaloneCollectorAppConfig config) {
        OperationsJsonWriter json = baseReport("self-check", validation);
        javaRuntime(json);
        runtimePackage(json, context);
        config(json, context, properties, validation);
        if (config == null) {
            json.name("deployment").nullValue();
            json.name("sources").beginArray().endArray();
            json.name("listeners").beginObject().endObject();
            json.name("sink").nullValue();
            json.name("backpressure").nullValue();
            json.name("management").nullValue();
        } else {
            deployment(json, config.deployment());
            sources(json, config.sources());
            listeners(json, config);
            sink(json, config);
            backpressure(json, config);
            management(json, config.management());
        }
        json.endObject();
        return json.toString();
    }

    static HotCheckResult hotCheck(
            RuntimeOperationsContext context,
            Properties properties,
            CollectorConfigValidation validation,
            StandaloneCollectorAppConfig config) {
        ConfigFingerprint current = ConfigFingerprint.from(context.configFile());
        HotCheckBaseline baseline = HotCheckBaseline.read(context.hotCheckBaselineFile());
        boolean changed = baseline.present()
                && current.sha256() != null
                && !current.sha256().equals(baseline.sha256());
        boolean restartRequired = changed && validation.isValid();

        OperationsJsonWriter json = baseReport("hot-check", validation);
        config(json, context, properties, validation);
        json.name("hotCheck").beginObject();
        json.name("configFile").value(path(context.configFile()));
        json.name("baselineFile").value(path(context.hotCheckBaselineFile()));
        json.name("baselinePresent").value(baseline.present());
        json.name("currentSha256").value(current.sha256());
        json.name("baselineSha256").value(baseline.sha256());
        json.name("changed").value(changed);
        json.name("restartRequired").value(restartRequired);
        json.name("hotReloaded").value(false);
        json.name("validationRequiredBeforeRestart").value(!validation.isValid());
        json.name("status").value(hotCheckStatus(validation, baseline, current, changed));
        json.name("lastModifiedMillis").value(current.lastModifiedMillis());
        json.endObject();
        if (config == null) {
            json.name("deployment").nullValue();
            json.name("sink").nullValue();
            json.name("backpressure").nullValue();
            json.name("management").nullValue();
        } else {
            deployment(json, config.deployment());
            sink(json, config);
            backpressure(json, config);
            management(json, config.management());
        }
        json.endObject();
        return new HotCheckResult(json.toString(), validation.isValid(), context.hotCheckBaselineFile(), current);
    }

    private static OperationsJsonWriter baseReport(String command, CollectorConfigValidation validation) {
        OperationsJsonWriter json = new OperationsJsonWriter();
        json.beginObject();
        json.name("command").value(command);
        json.name("generatedAt").value(Instant.now());
        json.name("status").value(validation.isValid() ? "PASS" : "FAIL");
        return json;
    }

    private static String hotCheckStatus(
            CollectorConfigValidation validation,
            HotCheckBaseline baseline,
            ConfigFingerprint current,
            boolean changed) {
        if (current.sha256() == null) {
            return "CONFIG_UNAVAILABLE";
        }
        if (!validation.isValid()) {
            return "INVALID_CONFIG";
        }
        if (!baseline.present()) {
            return "BASELINE_CREATED";
        }
        return changed ? "CHANGED_RESTART_REQUIRED" : "UNCHANGED";
    }

    private static void javaRuntime(OperationsJsonWriter json) {
        json.name("java").beginObject();
        json.name("version").value(System.getProperty("java.version"));
        json.name("vendor").value(System.getProperty("java.vendor"));
        json.name("home").value(System.getProperty("java.home"));
        json.name("runtimeName").value(System.getProperty("java.runtime.name"));
        json.name("vmName").value(System.getProperty("java.vm.name"));
        json.endObject();
    }

    private static void runtimePackage(OperationsJsonWriter json, RuntimeOperationsContext context) {
        Properties metadata = loadPropertiesIfExists(context.packageMetadataFile());
        json.name("package").beginObject();
        json.name("appHome").value(path(context.appHome()));
        json.name("metadataFile").value(path(context.packageMetadataFile()));
        json.name("metadataPresent").value(context.packageMetadataFile() != null && Files.isRegularFile(context.packageMetadataFile()));
        json.name("runtimeVersion").value(metadata.getProperty("runtime.version"));
        json.name("artifact").value(metadata.getProperty("artifact.name"));
        json.name("layout").value(metadata.getProperty("package.layout"));
        json.name("layoutVersion").value(metadata.getProperty("package.layout.version"));
        json.name("integrity").value(context.packageIntegrityStatus() == null ? "unknown" : context.packageIntegrityStatus());
        json.name("layoutPaths").beginArray();
        for (String relative : PACKAGE_LAYOUT_PATHS) {
            Path path = context.appHome() == null ? null : context.appHome().resolve(relative);
            json.beginObject();
            json.name("path").value(relative);
            pathStatus(json, path);
            json.endObject();
        }
        json.endArray();
        json.endObject();
    }

    private static void config(
            OperationsJsonWriter json,
            RuntimeOperationsContext context,
            Properties properties,
            CollectorConfigValidation validation) {
        ConfigFingerprint fingerprint = ConfigFingerprint.from(context.configFile());
        json.name("config").beginObject();
        json.name("file").value(path(context.configFile()));
        json.name("profile").value(properties.getProperty(StandaloneCollectorConfig.PROFILE, CollectorDeploymentConfig.DEFAULT_PROFILE));
        json.name("propertyCount").value(properties.size());
        json.name("valid").value(validation.isValid());
        json.name("errors").strings(validation.errors());
        json.name("sha256").value(fingerprint.sha256());
        json.name("lastModifiedMillis").value(fingerprint.lastModifiedMillis());
        pathStatus(json, context.configFile());
        json.endObject();
    }

    private static void deployment(OperationsJsonWriter json, CollectorDeploymentConfig deployment) {
        json.name("deployment").beginObject();
        json.name("profile").value(deployment.profile());
        pathEntry(json, "runtimeDir", deployment.runtimeDir());
        pathEntry(json, "confDir", deployment.confDir());
        pathEntry(json, "logsDir", deployment.logsDir());
        pathEntry(json, "dataDir", deployment.dataDir());
        pathEntry(json, "runDir", deployment.runDir());
        pathEntry(json, "tmpDir", deployment.tmpDir());
        pathEntry(json, "pidFile", deployment.pidFile());
        pathEntry(json, "statusFile", deployment.statusFile());
        pathEntry(json, "logFile", deployment.logFile());
        json.name("createDirectories").value(deployment.createDirectories());
        json.endObject();
    }

    private static void sources(OperationsJsonWriter json, List<CollectorSourceConfig> sources) {
        json.name("sources").beginArray();
        for (CollectorSourceConfig source : sources) {
            json.beginObject();
            json.name("name").value(source.name());
            json.name("sourceId").value(source.sourceId().qualifiedValue());
            json.name("protocol").value(source.protocol().configValue());
            json.endObject();
        }
        json.endArray();
    }

    private static void listeners(OperationsJsonWriter json, StandaloneCollectorAppConfig config) {
        json.name("listeners").beginObject();
        json.name("tcp").beginArray();
        for (TcpListenerConfig listener : config.tcpListeners()) {
            json.beginObject();
            json.name("name").value(listener.name());
            json.name("sourceName").value(listener.sourceName());
            json.name("sourceId").value(listener.sourceId().qualifiedValue());
            json.name("protocol").value(listener.protocol().configValue());
            json.name("host").value(listener.tcp().host());
            json.name("port").value(listener.tcp().port());
            json.name("bossThreads").value(listener.tcp().bossThreads());
            json.name("workerThreads").value(listener.tcp().workerThreads());
            json.name("bindReadiness").beginObject();
            json.name("status").value(listener.tcp().port() == 0 ? "EPHEMERAL_PORT" : "CONFIGURED");
            json.name("bindAttempted").value(false);
            json.name("reason").value("self-check and hot-check do not bind listener sockets");
            json.endObject();
            json.endObject();
        }
        json.endArray();
        json.name("http").beginArray();
        for (HttpListenerConfig listener : config.httpListeners()) {
            json.beginObject();
            json.name("name").value(listener.name());
            json.name("sourceName").value(listener.sourceName());
            json.name("sourceId").value(listener.sourceId().qualifiedValue());
            json.name("protocol").value(listener.protocol().configValue());
            json.name("host").value(listener.http().host());
            json.name("port").value(listener.http().port());
            json.name("path").value(listener.http().path());
            json.name("bindReadiness").beginObject();
            json.name("status").value(listener.http().port() == 0 ? "EPHEMERAL_PORT" : "CONFIGURED");
            json.name("bindAttempted").value(false);
            json.name("reason").value("self-check and hot-check do not bind listener sockets");
            json.endObject();
            json.endObject();
        }
        json.endArray();
        json.name("kafkaConsumerCount").value(config.kafkaConsumers().size());
        json.name("mqttClientCount").value(config.mqttClients().size());
        json.endObject();
    }

    private static void sink(OperationsJsonWriter json, StandaloneCollectorAppConfig config) {
        json.name("sink").beginObject();
        json.name("type").value(config.sinkType().configValue());
        json.name("schemaVersion").value(RuntimeRecordFormat.RECORD_SCHEMA_VERSION);
        json.name("format").value("jsonl");
        sinkAdapter(json, config.sinkAdapter());
        json.name("file").value(path(config.sinkFile()));
        if (config.sinkFile() == null) {
            json.name("filePath").nullValue();
        } else {
            json.name("filePath").beginObject();
            pathStatus(json, config.sinkFile());
            json.endObject();
        }
        json.name("rotation").beginObject();
        json.name("maxBytes").value(config.fileSinkRotation().maxBytes());
        json.name("maxHistory").value(config.fileSinkRotation().maxHistory());
        json.endObject();
        failedRecords(json, config.failedRecords());
        json.endObject();
    }

    private static void sinkAdapter(OperationsJsonWriter json, DownstreamSinkAdapterConfig adapter) {
        json.name("adapter").beginObject();
        json.name("type").value(adapter.type());
        json.name("endpointConfigured").value(adapter.endpoint() != null);
        json.name("topicConfigured").value(adapter.topic() != null);
        json.name("authRefConfigured").value(adapter.authenticationReferenceConfigured());
        json.name("timeoutMillis").value(adapter.timeoutMillis());
        json.name("batchingPosture").value(adapter.batchingPosture());
        json.name("retryPosture").value(adapter.retryPosture());
        json.name("deadLetterOutput").value(adapter.deadLetterOutput());
        json.name("secretSafeDiagnostics").value(true);
        json.endObject();
    }

    private static void failedRecords(OperationsJsonWriter json, SinkFailureIsolationConfig failedRecords) {
        json.name("failedRecords").beginObject();
        json.name("enabled").value(failedRecords.enabled());
        json.name("directory").value(path(failedRecords.directory()));
        json.name("maxSamples").value(failedRecords.maxSamples());
        pathStatus(json, failedRecords.directory());
        json.endObject();
    }

    private static void backpressure(OperationsJsonWriter json, StandaloneCollectorAppConfig config) {
        json.name("backpressure").beginObject();
        json.name("decision").value(config.backpressureDecision().name());
        json.name("maxPayloadBytes").value(config.backpressureMaxPayloadBytes());
        json.name("oversizedPayloadDecision").value(config.oversizedPayloadDecision().name());
        json.name("sinkFailureThreshold").value(config.sinkFailureBackpressureThreshold());
        json.name("sinkFailureDecision").value(config.sinkFailureBackpressureDecision().name());
        json.endObject();
    }

    private static void management(OperationsJsonWriter json, ManagementServerConfig management) {
        json.name("management").beginObject();
        json.name("enabled").value(management.enabled());
        json.name("host").value(management.host());
        json.name("port").value(management.port());
        json.name("healthPath").value(management.healthPath());
        json.name("readinessPath").value(management.readinessPath());
        json.name("statusPath").value(management.statusPath());
        json.name("access").value(management.accessMode().configValue());
        json.name("tokenConfigured").value(management.token() != null);
        json.name("requestLoggingEnabled").value(management.requestLoggingEnabled());
        json.name("healthHistoryMaxEntries").value(management.healthHistoryMaxEntries());
        json.endObject();
    }

    private static void pathEntry(OperationsJsonWriter json, String name, Path path) {
        json.name(name).beginObject();
        json.name("path").value(path(path));
        pathStatus(json, path);
        json.endObject();
    }

    private static void pathStatus(OperationsJsonWriter json, Path path) {
        if (path == null) {
            json.name("exists").value(false);
            json.name("directory").value(false);
            json.name("regularFile").value(false);
            json.name("readable").value(false);
            json.name("writable").value(false);
            json.name("parentWritable").value(false);
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        json.name("absolutePath").value(normalized.toString());
        json.name("exists").value(Files.exists(normalized));
        json.name("directory").value(Files.isDirectory(normalized));
        json.name("regularFile").value(Files.isRegularFile(normalized));
        json.name("readable").value(Files.isReadable(normalized));
        json.name("writable").value(Files.isWritable(normalized));
        json.name("parentWritable").value(parent != null && Files.isWritable(parent));
    }

    private static Properties loadPropertiesIfExists(Path path) {
        Properties properties = new Properties();
        if (path == null || !Files.isRegularFile(path)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ignored) {
            return new Properties();
        }
        return properties;
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
        byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static String path(Path path) {
        return path == null ? null : path.toString();
    }

    record RuntimeOperationsContext(
            Path configFile,
            Path appHome,
            Path packageMetadataFile,
            String packageIntegrityStatus,
            Path hotCheckBaselineFile) {
    }

    record HotCheckResult(
            String json,
            boolean valid,
            Path baselineFile,
            ConfigFingerprint current) {

        void writeBaselineIfPossible() throws IOException {
            if (!valid || baselineFile == null || current.sha256() == null) {
                return;
            }
            Path parent = baselineFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String content = "sha256=" + current.sha256() + System.lineSeparator()
                    + "lastModifiedMillis=" + current.lastModifiedMillis() + System.lineSeparator();
            Files.writeString(baselineFile, content, StandardCharsets.UTF_8);
        }
    }

    private record ConfigFingerprint(String sha256, long lastModifiedMillis) {
        private static ConfigFingerprint from(Path path) {
            if (path == null || !Files.isRegularFile(path)) {
                return new ConfigFingerprint(null, -1);
            }
            try {
                return new ConfigFingerprint(
                        RuntimeOperationsChecks.sha256(path),
                        Files.getLastModifiedTime(path).toMillis());
            } catch (IOException ex) {
                return new ConfigFingerprint(null, -1);
            }
        }
    }

    private record HotCheckBaseline(boolean present, String sha256) {
        private static HotCheckBaseline read(Path path) {
            if (path == null || !Files.isRegularFile(path)) {
                return new HotCheckBaseline(false, null);
            }
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
                return new HotCheckBaseline(true, properties.getProperty("sha256"));
            } catch (IOException ex) {
                return new HotCheckBaseline(false, null);
            }
        }
    }
}
