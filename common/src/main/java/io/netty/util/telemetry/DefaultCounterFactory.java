package io.netty.util.telemetry;

public final class DefaultCounterFactory implements CounterFactory {
    public static final DefaultCounterFactory INSTANCE = new DefaultCounterFactory();

    private DefaultCounterFactory() {
    }

    @Override
    public Counter newIntegerCounter() {
        return new IntegerCounter();
    }

    @Override
    public Counter newLongCounter() {
        return new LongCounter();
    }

    @Override
    public Counter newLongLongCounter() {
        return new CheckedLongLongCounter();
    }

    @Override
    public Counter newConcurrentIntegerCounter() {
        return newConcurrentLongCounter();
    }

    @Override
    public Counter newConcurrentLongCounter() {
        return new ConcurrentLongCounter();
    }
}
