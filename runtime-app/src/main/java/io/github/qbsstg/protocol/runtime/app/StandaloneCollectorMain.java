package io.github.qbsstg.protocol.runtime.app;

import java.util.Properties;

public final class StandaloneCollectorMain {

    private StandaloneCollectorMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        Properties properties = StandaloneCollectorConfig.propertiesFromArgs(args);
        StandaloneCollectorConfig.validateProperties(properties).throwIfInvalid();
        StandaloneCollectorAppConfig config = StandaloneCollectorConfig.appConfigFromProperties(properties);
        StandaloneCollector collector = StandaloneCollector.create(config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            collector.stop();
            System.out.println(CollectorStatusFormatter.format(collector.statusSnapshot()));
        }, "protocol-runtime-shutdown"));
        collector.start();
        int listenerCount = config.tcpListeners().size() + config.httpListeners().size();
        if (config.tcpListeners().size() == 1 && config.httpListeners().isEmpty()) {
            TcpListenerConfig listener = config.tcpListeners().get(0);
            System.out.printf(
                    "Protocol Runtime collector started transport=tcp protocol=%s host=%s port=%d sourceId=%s sink=%s%n",
                    listener.protocol().configValue(),
                    listener.tcp().host(),
                    collector.port(),
                    listener.sourceId().qualifiedValue(),
                    config.sinkType().configValue());
        } else if (config.httpListeners().size() == 1 && config.tcpListeners().isEmpty()) {
            HttpListenerConfig listener = config.httpListeners().get(0);
            System.out.printf(
                    "Protocol Runtime collector started transport=http protocol=%s host=%s port=%d path=%s sourceId=%s sink=%s%n",
                    listener.protocol().configValue(),
                    listener.http().host(),
                    collector.httpPorts().get(0),
                    listener.http().path(),
                    listener.sourceId().qualifiedValue(),
                    config.sinkType().configValue());
        } else {
            System.out.printf(
                    "Protocol Runtime collector started listeners=%d sources=%d tcpPorts=%s httpPorts=%s sink=%s%n",
                    listenerCount,
                    config.sources().size(),
                    collector.ports(),
                    collector.httpPorts(),
                    config.sinkType().configValue());
        }
        System.out.println(CollectorStatusFormatter.format(collector.statusSnapshot()));
        collector.awaitShutdown();
    }
}
