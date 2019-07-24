package io.netty.buffer;

import io.netty.util.ResourceLeakDetector;
import org.junit.Test;

import static io.netty.buffer.Unpooled.*;
import static org.junit.Assert.*;

public class CompositeByteBufTest {

    @Test
    public void testAddComponentWithLeakAwareByteBuf() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        ByteBuf buffer = wrappedBuffer("hello world".getBytes()).slice(6, 5);
        ByteBuf leakAwareBuffer = UnpooledByteBufAllocator.toLeakAwareBuffer(buffer);

        CompositeByteBuf composite = compositeBuffer();
        composite.addComponents(true, leakAwareBuffer);
        byte[] result = new byte[5];

        composite.component(0).readBytes(result);
        assertArrayEquals("world".getBytes(), result);
    }
}
