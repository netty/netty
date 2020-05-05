package io.netty.util.telemetry;

public final class CheckedCounterFactory implements CounterFactory {
    public static final CheckedCounterFactory INSTANCE = new CheckedCounterFactory();

    private CheckedCounterFactory() {
    }

    @Override
    public Counter newIntegerCounter() {
        return new CheckedIntegerCounter();
    }

    @Override
    public Counter newLongCounter() {
        return new CheckedLongCounter();
    }

    @Override
    public Counter newLongLongCounter() {
        return new CheckedLongLongCounter();
    }

    @Override
    public Counter newConcurrentIntegerCounter() {
        return new CheckedConcurrentIntegerCounter();
    }

    @Override
    public Counter newConcurrentLongCounter() {
        return new CheckedConcurrentLongCounter();
    }
}
