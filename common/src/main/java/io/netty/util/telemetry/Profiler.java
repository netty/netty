package io.netty.util.telemetry;

public interface Profiler extends Statistic {
    void start();

    void stop();
}
