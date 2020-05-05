package io.netty.util.telemetry;

import java.math.BigInteger;

final class CheckedLongLongCounter implements Counter {
    private final int[] values = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
    private int index;

    @Override
    public void increment() {
        if (++values[index] == Integer.MAX_VALUE) {
            index++;
        }
    }

    @Override
    public void incrementTimes(int value) {
        int oldValue = values[index];
        int newValue = oldValue + value;
        if (newValue < oldValue) {
            values[index] = Integer.MAX_VALUE;
            values[++index] = oldValue - newValue;
        } else {
            values[index] = newValue;
        }
    }

    @Override
    public void incrementTimes(long value) {
        incrementTimes(Math.toIntExact(value));
    }

    @Override
    public int integerResultUnsigned() {
        return bigIntegerResult().intValueExact();
    }

    @Override
    public long longResultUnsigned() {
        return bigIntegerResult().longValueExact();
    }

    @Override
    public BigInteger bigIntegerResult() {
        BigInteger sum = BigInteger.ZERO;
        for (int i = 0; i <= index; i++) {
            sum = sum.add(BigInteger.valueOf(Integer.toUnsignedLong(values[i])));
        }
        return sum;
    }

    @Override
    public String print() {
        return bigIntegerResult().toString();
    }
}
