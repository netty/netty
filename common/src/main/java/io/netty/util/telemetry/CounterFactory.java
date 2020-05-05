package io.netty.util.telemetry;

public interface CounterFactory {
    Counter newIntegerCounter();

    Counter newLongCounter();

    Counter newLongLongCounter();

    Counter newConcurrentIntegerCounter();

    Counter newConcurrentLongCounter();
}
