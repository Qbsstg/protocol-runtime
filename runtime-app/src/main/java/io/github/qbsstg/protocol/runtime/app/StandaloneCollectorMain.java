package io.github.qbsstg.protocol.runtime.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class StandaloneCollectorMain {

    private StandaloneCollectorMain() {
    }

    public static void main(String[] args) throws Exception {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) throws InterruptedException {
        CliCommand command;
        try {
            command = CliCommand.parse(args);
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 2;
        }

        Properties properties;
        try {
            properties = StandaloneCollectorConfig.propertiesFromArgs(command.configArgs());
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 2;
        }

        if (command.stop()) {
            return stop(command, properties, out, err);
        }

        CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);
        StandaloneCollectorAppConfig config = null;
        if (validation.isValid()) {
            config = StandaloneCollectorConfig.appConfigFromProperties(properties);
        }

        if (command.selfCheck()) {
            out.println(RuntimeOperationsChecks.selfCheck(
                    command.operationsContext(),
                    properties,
                    validation,
                    config));
            return validation.isValid() ? 0 : 2;
        }
        if (command.hotCheck()) {
            RuntimeOperationsChecks.HotCheckResult result = RuntimeOperationsChecks.hotCheck(
                    command.operationsContext(),
                    properties,
                    validation,
                    config);
            out.println(result.json());
            try {
                result.writeBaselineIfPossible();
            } catch (IOException ex) {
                err.println("Unable to update hot-check baseline: " + ex.getMessage());
                return 1;
            }
            return validation.isValid() ? 0 : 2;
        }

        if (!validation.isValid()) {
            err.println("Protocol Runtime collector config validation status=INVALID");
            validation.errors().forEach(error -> err.println(" - " + error));
            return 2;
        }

        if (command.validate()) {
            out.printf(
                    "Protocol Runtime collector config validation status=VALID profile=%s%n",
                    config.deployment().profile());
            return 0;
        }
        if (command.dryRun()) {
            StandaloneCollector collector = StandaloneCollector.create(config);
            exportStatus(config.deployment().statusFile(), collector.statusSnapshot(), err);
            out.printf(
                    "Protocol Runtime collector dry-run status=VALID profile=%s listeners=%d sources=%d sink=%s%n",
                    config.deployment().profile(),
                    config.tcpListeners().size()
                            + config.httpListeners().size()
                            + config.kafkaConsumers().size()
                            + config.mqttClients().size(),
                    config.sources().size(),
                    config.sinkType().configValue());
            return 0;
        }

        final StandaloneCollectorAppConfig runtimeConfig = config;
        try {
            runtimeConfig.deployment().prepareDirectories();
        } catch (IOException ex) {
            err.println("Unable to prepare runtime directories: " + ex.getMessage());
            return 1;
        }

        StandaloneCollector collector = StandaloneCollector.create(runtimeConfig);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopCollector(collector, runtimeConfig, out, err);
        }, "protocol-runtime-shutdown"));
        try {
            collector.start();
            StandaloneCollectorProcessControl.writePid(runtimeConfig.deployment().pidFile());
        } catch (IOException ex) {
            err.println("Unable to write collector pid file: " + ex.getMessage());
            collector.stop();
            return 1;
        } catch (RuntimeException ex) {
            err.println("Unable to start Protocol Runtime collector: " + ex.getMessage());
            exportStatus(runtimeConfig.deployment().statusFile(), collector.statusSnapshot(), err);
            return 1;
        }
        int listenerCount = runtimeConfig.tcpListeners().size() + runtimeConfig.httpListeners().size();
        if (runtimeConfig.tcpListeners().size() == 1 && runtimeConfig.httpListeners().isEmpty()) {
            TcpListenerConfig listener = runtimeConfig.tcpListeners().get(0);
            out.printf(
                    "Protocol Runtime collector started transport=tcp protocol=%s host=%s port=%d sourceId=%s sink=%s%n",
                    listener.protocol().configValue(),
                    listener.tcp().host(),
                    collector.port(),
                    listener.sourceId().qualifiedValue(),
                    runtimeConfig.sinkType().configValue());
        } else if (runtimeConfig.httpListeners().size() == 1 && runtimeConfig.tcpListeners().isEmpty()) {
            HttpListenerConfig listener = runtimeConfig.httpListeners().get(0);
            out.printf(
                    "Protocol Runtime collector started transport=http protocol=%s host=%s port=%d path=%s sourceId=%s sink=%s%n",
                    listener.protocol().configValue(),
                    listener.http().host(),
                    collector.httpPorts().get(0),
                    listener.http().path(),
                    listener.sourceId().qualifiedValue(),
                    runtimeConfig.sinkType().configValue());
        } else {
            out.printf(
                    "Protocol Runtime collector started listeners=%d sources=%d tcpPorts=%s httpPorts=%s sink=%s%n",
                    listenerCount,
                    runtimeConfig.sources().size(),
                    collector.ports(),
                    collector.httpPorts(),
                    runtimeConfig.sinkType().configValue());
        }
        if (runtimeConfig.management().enabled()) {
            out.printf(
                    "Protocol Runtime management started host=%s port=%d health=%s readiness=%s status=%s access=%s requestLogging=%s%n",
                    runtimeConfig.management().host(),
                    collector.managementPort(),
                    runtimeConfig.management().healthPath(),
                    runtimeConfig.management().readinessPath(),
                    runtimeConfig.management().statusPath(),
                    runtimeConfig.management().accessMode().configValue(),
                    runtimeConfig.management().requestLoggingEnabled());
        }
        exportStatus(runtimeConfig.deployment().statusFile(), collector.statusSnapshot(), err);
        out.println(CollectorStatusFormatter.format(collector.statusSnapshot()));
        collector.awaitShutdown();
        StandaloneCollectorProcessControl.clearPid(runtimeConfig.deployment().pidFile());
        exportStatus(runtimeConfig.deployment().statusFile(), collector.statusSnapshot(), err);
        return 0;
    }

    private static int stop(CliCommand command, Properties properties, PrintStream out, PrintStream err) {
        Path pidFile = command.pidFile();
        if (pidFile == null) {
            CollectorConfigValidation validation = StandaloneCollectorConfig.validateProperties(properties);
            if (!validation.isValid()) {
                err.println("Protocol Runtime collector stop status=INVALID_CONFIG");
                validation.errors().forEach(error -> err.println(" - " + error));
                return 2;
            }
            pidFile = StandaloneCollectorConfig.appConfigFromProperties(properties).deployment().pidFile();
        }
        if (pidFile == null) {
            err.println("Protocol Runtime collector stop status=INVALID pidFile is required");
            return 2;
        }
        StandaloneCollectorProcessControl.StopResult result = StandaloneCollectorProcessControl.stop(pidFile);
        out.printf("Protocol Runtime collector stop status=%s message=%s%n", result.status(), result.message());
        return result.success() ? 0 : 2;
    }

    private static void stopCollector(
            StandaloneCollector collector,
            StandaloneCollectorAppConfig config,
            PrintStream out,
            PrintStream err) {
        try {
            collector.stop();
        } finally {
            StandaloneCollectorProcessControl.clearPid(config.deployment().pidFile());
            exportStatus(config.deployment().statusFile(), collector.statusSnapshot(), err);
            out.println(CollectorStatusFormatter.format(collector.statusSnapshot()));
        }
    }

    private static void exportStatus(Path statusFile, CollectorStatusSnapshot snapshot, PrintStream err) {
        if (statusFile == null) {
            return;
        }
        try {
            Path parent = statusFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(statusFile, CollectorStatusJson.status(snapshot) + System.lineSeparator());
        } catch (IOException ex) {
            err.println("Unable to export collector status: " + ex.getMessage());
        }
    }

    private record CliCommand(
            boolean validate,
            boolean dryRun,
            boolean stop,
            boolean selfCheck,
            boolean hotCheck,
            Path pidFile,
            Path configFile,
            Path appHome,
            Path packageMetadataFile,
            String packageIntegrityStatus,
            Path hotCheckBaselineFile,
            String[] configArgs) {

        RuntimeOperationsChecks.RuntimeOperationsContext operationsContext() {
            return new RuntimeOperationsChecks.RuntimeOperationsContext(
                    configFile,
                    appHome,
                    packageMetadataFile,
                    packageIntegrityStatus,
                    hotCheckBaselineFile);
        }

        static CliCommand parse(String[] args) {
            boolean validate = false;
            boolean dryRun = false;
            boolean stop = false;
            boolean selfCheck = false;
            boolean hotCheck = false;
            Path pidFile = null;
            Path configFile = null;
            Path appHome = null;
            Path packageMetadataFile = null;
            String packageIntegrityStatus = null;
            Path hotCheckBaselineFile = null;
            List<String> configArgs = new ArrayList<>();
            if (args == null) {
                return new CliCommand(
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new String[0]);
            }
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--config".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--config requires a file path");
                    }
                    configFile = Path.of(args[++i]);
                    configArgs.add("--config");
                    configArgs.add(configFile.toString());
                    continue;
                }
                if (arg.startsWith("--config=")) {
                    configFile = Path.of(arg.substring("--config=".length()));
                    configArgs.add(arg);
                    continue;
                }
                if ("--validate".equals(arg)) {
                    validate = true;
                    continue;
                }
                if ("--dry-run".equals(arg)) {
                    dryRun = true;
                    continue;
                }
                if ("--stop".equals(arg)) {
                    stop = true;
                    continue;
                }
                if ("--self-check".equals(arg)) {
                    selfCheck = true;
                    continue;
                }
                if ("--hot-check".equals(arg)) {
                    hotCheck = true;
                    continue;
                }
                if ("--pid-file".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--pid-file requires a file path");
                    }
                    pidFile = Path.of(args[++i]);
                    continue;
                }
                if (arg.startsWith("--pid-file=")) {
                    pidFile = Path.of(arg.substring("--pid-file=".length()));
                    continue;
                }
                if ("--operation-app-home".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--operation-app-home requires a directory path");
                    }
                    appHome = Path.of(args[++i]);
                    continue;
                }
                if (arg.startsWith("--operation-app-home=")) {
                    appHome = Path.of(arg.substring("--operation-app-home=".length()));
                    continue;
                }
                if ("--operation-package-metadata".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--operation-package-metadata requires a file path");
                    }
                    packageMetadataFile = Path.of(args[++i]);
                    continue;
                }
                if (arg.startsWith("--operation-package-metadata=")) {
                    packageMetadataFile = Path.of(arg.substring("--operation-package-metadata=".length()));
                    continue;
                }
                if ("--operation-package-integrity".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--operation-package-integrity requires a status value");
                    }
                    packageIntegrityStatus = args[++i];
                    continue;
                }
                if (arg.startsWith("--operation-package-integrity=")) {
                    packageIntegrityStatus = arg.substring("--operation-package-integrity=".length());
                    continue;
                }
                if ("--hot-check-baseline".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--hot-check-baseline requires a file path");
                    }
                    hotCheckBaselineFile = Path.of(args[++i]);
                    continue;
                }
                if (arg.startsWith("--hot-check-baseline=")) {
                    hotCheckBaselineFile = Path.of(arg.substring("--hot-check-baseline=".length()));
                    continue;
                }
                if ("--status-export".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--status-export requires a file path");
                    }
                    configArgs.add("--" + StandaloneCollectorConfig.RUNTIME_STATUS_FILE + "=" + args[++i]);
                    continue;
                }
                if (arg.startsWith("--status-export=")) {
                    configArgs.add("--" + StandaloneCollectorConfig.RUNTIME_STATUS_FILE
                            + "="
                            + arg.substring("--status-export=".length()));
                    continue;
                }
                configArgs.add(arg);
            }
            int commandCount = 0;
            commandCount += validate ? 1 : 0;
            commandCount += dryRun ? 1 : 0;
            commandCount += stop ? 1 : 0;
            commandCount += selfCheck ? 1 : 0;
            commandCount += hotCheck ? 1 : 0;
            if (commandCount > 1) {
                throw new IllegalArgumentException(
                        "--validate, --dry-run, --stop, --self-check, and --hot-check cannot be combined");
            }
            return new CliCommand(
                    validate,
                    dryRun,
                    stop,
                    selfCheck,
                    hotCheck,
                    pidFile,
                    configFile,
                    appHome,
                    packageMetadataFile,
                    packageIntegrityStatus,
                    hotCheckBaselineFile,
                    configArgs.toArray(String[]::new));
        }
    }
}
