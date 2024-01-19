package io.netty.buffer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class AdaptivePooledByteBufTest extends AbstractPooledByteBufTest {
    private final AdaptiveByteBufAllocator allocator = new AdaptiveByteBufAllocator();

    @Override
    protected ByteBuf alloc(int length, int maxCapacity) {
        return allocator.buffer(length, maxCapacity);
    }

    @Disabled("Assumes the ByteBuf can be cast to PooledByteBuf")
    @Test
    @Override
    public void testMaxFastWritableBytes() {
    }

    @Disabled("Assumes the ByteBuf can be cast to PooledByteBuf")
    @Test
    @Override
    public void testEnsureWritableDoesntGrowTooMuch() {
    }
}
