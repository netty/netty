package io.netty.util.telemetry.impl;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.telemetry.Counter;
import io.netty.util.telemetry.DisabledCounter;
import io.netty.util.telemetry.Toggleable;

public final class ToggleableEventLoopTelemetry implements EventLoopTelemetry, Toggleable {
    private final EnabledEventLoopTelemetry template;

    private Counter selectorRebuild;

    public ToggleableEventLoopTelemetry(EnabledEventLoopTelemetry template) {
        this.template = ObjectUtil.checkNotNull(template, "template");
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled) {
            selectorRebuild = template.selectorRebuild();
        } else {
            selectorRebuild = DisabledCounter.INSTANCE;
        }
    }

    @Override
    public Counter selectorRebuild() {
        return selectorRebuild;
    }
}
