package io.github.qbsstg.protocol.runtime.app;

public final class StandaloneCollectorMain {

    private StandaloneCollectorMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        StandaloneCollectorConfig config = StandaloneCollectorConfig.fromArgs(args);
        StandaloneCollector collector = StandaloneCollector.create(config);
        Runtime.getRuntime().addShutdownHook(new Thread(collector::stop, "protocol-runtime-shutdown"));
        collector.start();
        System.out.printf(
                "Protocol Runtime collector started protocol=iec104 host=%s port=%d sourceId=%s sink=%s%n",
                config.tcp().host(),
                collector.port(),
                config.sourceId().qualifiedValue(),
                config.sinkType().configValue());
        collector.awaitShutdown();
    }
}
