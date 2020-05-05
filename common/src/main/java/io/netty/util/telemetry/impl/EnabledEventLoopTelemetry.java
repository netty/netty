package io.netty.util.telemetry.impl;

import io.netty.util.telemetry.Counter;
import io.netty.util.telemetry.CounterFactory;

public final class EnabledEventLoopTelemetry implements EventLoopTelemetry {
    private final Counter selectorRebuild;

    public EnabledEventLoopTelemetry(CounterFactory counterFactory) {
        selectorRebuild = counterFactory.newIntegerCounter();
    }

    @Override
    public Counter selectorRebuild() {
        return selectorRebuild;
    }
}
