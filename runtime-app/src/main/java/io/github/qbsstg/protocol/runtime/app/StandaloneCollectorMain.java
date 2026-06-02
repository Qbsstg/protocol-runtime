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
        if (config.tcpListeners().size() == 1) {
            TcpListenerConfig listener = config.tcpListeners().get(0);
            System.out.printf(
                    "Protocol Runtime collector started protocol=iec104 host=%s port=%d sourceId=%s sink=%s%n",
                    listener.tcp().host(),
                    collector.port(),
                    listener.sourceId().qualifiedValue(),
                    config.sinkType().configValue());
        } else {
            System.out.printf(
                    "Protocol Runtime collector started protocol=iec104 listeners=%d sources=%d ports=%s sink=%s%n",
                    config.tcpListeners().size(),
                    config.sources().size(),
                    collector.ports(),
                    config.sinkType().configValue());
        }
        System.out.println(CollectorStatusFormatter.format(collector.statusSnapshot()));
        collector.awaitShutdown();
    }
}
