package io.netty.buffer;

import org.junit.jupiter.api.Test;

public class PoolSubpageTest {

    @Test
    public void testBitMapLen() {
        PoolSubpage head = new PoolSubpage();
        head.prev = head;
        head.next = head;
        new PoolSubpage(head, null, 13, 0, 8192, 32);
    }
}
