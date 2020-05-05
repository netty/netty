package io.netty.util.telemetry;

import java.math.BigInteger;

public final class DisabledCounter implements Counter {
    public static final DisabledCounter INSTANCE = new DisabledCounter();

    private DisabledCounter() {
    }

    @Override
    public void increment() {
    }

    @Override
    public void incrementTimes(int value) {
    }

    @Override
    public void incrementTimes(long value) {
    }

    @Override
    public int integerResultUnsigned() {
        throw new DisabledException();
    }

    @Override
    public long longResultUnsigned() {
        throw new DisabledException();
    }

    @Override
    public BigInteger bigIntegerResult() {
        throw new DisabledException();
    }

    @Override
    public String print() {
        throw new DisabledException();
    }
}
