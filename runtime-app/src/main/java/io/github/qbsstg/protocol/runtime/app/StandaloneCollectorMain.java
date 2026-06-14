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
        if (!validation.isValid()) {
            err.println("Protocol Runtime collector config validation status=INVALID");
            validation.errors().forEach(error -> err.println(" - " + error));
            return 2;
        }

        StandaloneCollectorAppConfig config = StandaloneCollectorConfig.appConfigFromProperties(properties);
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

        try {
            config.deployment().prepareDirectories();
        } catch (IOException ex) {
            err.println("Unable to prepare runtime directories: " + ex.getMessage());
            return 1;
        }

        StandaloneCollector collector = StandaloneCollector.create(config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopCollector(collector, config, out, err);
        }, "protocol-runtime-shutdown"));
        try {
            collector.start();
            StandaloneCollectorProcessControl.writePid(config.deployment().pidFile());
        } catch (IOException ex) {
            err.println("Unable to write collector pid file: " + ex.getMessage());
            collector.stop();
            return 1;
        } catch (RuntimeException ex) {
            err.println("Unable to start Protocol Runtime collector: " + ex.getMessage());
            exportStatus(config.deployment().statusFile(), collector.statusSnapshot(), err);
            return 1;
        }
        int listenerCount = config.tcpListeners().size() + config.httpListeners().size();
        if (config.tcpListeners().size() == 1 && config.httpListeners().isEmpty()) {
            TcpListenerConfig listener = config.tcpListeners().get(0);
            out.printf(
                    "Protocol Runtime collector started transport=tcp protocol=%s host=%s port=%d sourceId=%s sink=%s%n",
                    listener.protocol().configValue(),
                    listener.tcp().host(),
                    collector.port(),
                    listener.sourceId().qualifiedValue(),
                    config.sinkType().configValue());
        } else if (config.httpListeners().size() == 1 && config.tcpListeners().isEmpty()) {
            HttpListenerConfig listener = config.httpListeners().get(0);
            out.printf(
                    "Protocol Runtime collector started transport=http protocol=%s host=%s port=%d path=%s sourceId=%s sink=%s%n",
                    listener.protocol().configValue(),
                    listener.http().host(),
                    collector.httpPorts().get(0),
                    listener.http().path(),
                    listener.sourceId().qualifiedValue(),
                    config.sinkType().configValue());
        } else {
            out.printf(
                    "Protocol Runtime collector started listeners=%d sources=%d tcpPorts=%s httpPorts=%s sink=%s%n",
                    listenerCount,
                    config.sources().size(),
                    collector.ports(),
                    collector.httpPorts(),
                    config.sinkType().configValue());
        }
        if (config.management().enabled()) {
            out.printf(
                    "Protocol Runtime management started host=%s port=%d health=%s readiness=%s status=%s access=%s requestLogging=%s%n",
                    config.management().host(),
                    collector.managementPort(),
                    config.management().healthPath(),
                    config.management().readinessPath(),
                    config.management().statusPath(),
                    config.management().accessMode().configValue(),
                    config.management().requestLoggingEnabled());
        }
        exportStatus(config.deployment().statusFile(), collector.statusSnapshot(), err);
        out.println(CollectorStatusFormatter.format(collector.statusSnapshot()));
        collector.awaitShutdown();
        StandaloneCollectorProcessControl.clearPid(config.deployment().pidFile());
        exportStatus(config.deployment().statusFile(), collector.statusSnapshot(), err);
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
            Path pidFile,
            String[] configArgs) {

        static CliCommand parse(String[] args) {
            boolean validate = false;
            boolean dryRun = false;
            boolean stop = false;
            Path pidFile = null;
            List<String> configArgs = new ArrayList<>();
            if (args == null) {
                return new CliCommand(false, false, false, null, new String[0]);
            }
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
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
            if (stop && (validate || dryRun)) {
                throw new IllegalArgumentException("--stop cannot be combined with --validate or --dry-run");
            }
            return new CliCommand(validate, dryRun, stop, pidFile, configArgs.toArray(String[]::new));
        }
    }
}
