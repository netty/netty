package io.netty.util.telemetry.impl;

import io.netty.util.telemetry.Counter;

public interface EventLoopTelemetry {
    Counter selectorRebuild();
}
