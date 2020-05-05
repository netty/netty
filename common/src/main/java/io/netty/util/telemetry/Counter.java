package io.netty.util.telemetry;

import java.math.BigInteger;

public interface Counter extends Statistic {
    void increment();

    void incrementTimes(int value);

    void incrementTimes(long value);

    int integerResultUnsigned();

    long longResultUnsigned();

    BigInteger bigIntegerResult();
}
