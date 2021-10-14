package io.netty.buffer.api.internal;

import io.netty.buffer.api.AllocationType;
import io.netty.util.internal.UnstableApi;

/**
 * An {@link AllocationType} for on-heap buffer allocations that wrap an existing byte array.
 */
@SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType") // This is the express purpose of this class.
@UnstableApi
public final class WrappingAllocation implements AllocationType {
    private final byte[] array;

    public WrappingAllocation(byte[] array) {
        this.array = array;
    }

    public byte[] getArray() {
        return array;
    }
}
