package io.netty.util.telemetry.impl;

import io.netty.util.telemetry.Counter;
import io.netty.util.telemetry.DisabledCounter;

public final class DisabledEventLoopTelemetry implements EventLoopTelemetry {
    public static final DisabledEventLoopTelemetry INSTANCE = new DisabledEventLoopTelemetry();

    private DisabledEventLoopTelemetry() {
    }

    @Override
    public Counter selectorRebuild() {
        return DisabledCounter.INSTANCE;
    }
}
