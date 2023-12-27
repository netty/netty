package io.netty.buffer;

public interface MemoryStateListener {
    void memoryReleased(int released, boolean direct);

    void memoryAllocated(int allocated, boolean direct);
}
