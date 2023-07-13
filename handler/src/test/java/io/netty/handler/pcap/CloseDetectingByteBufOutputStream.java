package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

import java.io.IOException;

/**
 * A {@link ByteBufOutputStream} which detects if {@link #close()} was called.
 */
final class CloseDetectingByteBufOutputStream extends ByteBufOutputStream {

    private boolean isCloseCalled;

    /**
     * Creates a new stream which writes data to the specified {@code buffer}.
     */
    CloseDetectingByteBufOutputStream(ByteBuf buffer) {
        super(buffer);
    }

    public boolean closeCalled() {
        return isCloseCalled;
    }

    @Override
    public void close() throws IOException {
        super.close();
        isCloseCalled = true;
    }
}
