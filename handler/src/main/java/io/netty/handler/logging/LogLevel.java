package io.netty.handler.logging;

import io.netty.logging.InternalLogLevel;

public enum LogLevel {
    DEBUG(InternalLogLevel.DEBUG),
    INFO(InternalLogLevel.INFO),
    WARN(InternalLogLevel.WARN),
    ERROR(InternalLogLevel.ERROR);

    private final InternalLogLevel internalLevel;

    LogLevel(InternalLogLevel internalLevel) {
        this.internalLevel = internalLevel;
    }

    InternalLogLevel toInternalLevel() {
        return internalLevel;
    }
}
