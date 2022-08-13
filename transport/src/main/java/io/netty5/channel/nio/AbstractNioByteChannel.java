/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel.nio;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.util.Resource;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FileRegion;
import io.netty5.channel.internal.ChannelUtils;
import io.netty5.util.internal.StringUtil;

import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import static io.netty5.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on bytes.
 */
public abstract class AbstractNioByteChannel<P extends Channel, L extends SocketAddress, R extends SocketAddress>
        extends AbstractNioChannel<P, L, R> {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(Buffer.class) + ", " +
            StringUtil.simpleClassName(FileRegion.class) + ')';

    // Calling flush0 directly to ensure we not try to flush messages that were added via write(...) in the
    // meantime.
    private final Runnable flushTask = this::writeFlushed;

    /**
     * Create a new instance
     *
     * @param parent            the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param eventLoop         the {@link EventLoop} to use for IO.
     * @param ch                the underlying {@link SelectableChannel} on which it operates
     */
    protected AbstractNioByteChannel(P parent, EventLoop eventLoop, SelectableChannel ch) {
        super(parent, eventLoop, false, new AdaptiveReadHandleFactory(), ch, SelectionKey.OP_READ);
    }

    @Override
    protected final boolean doReadNow(ReadBufferAllocator readBufferAllocator, ReadSink readSink) throws Exception {
        final BufferAllocator bufferAllocator = bufferAllocator();

        Buffer buffer = null;
        boolean close = false;
        try {
            boolean continueReading;
            do {
                buffer = readBufferAllocator.allocate(bufferAllocator, readSink.estimatedBufferCapacity());
                if (buffer == null) {
                    readSink.processRead(0, 0, null);
                    break;
                }
                int attemptedBytesRead = buffer.writableBytes();
                int actualBytesRead = doReadBytes(buffer);
                if (actualBytesRead <= 0) {
                    // nothing was read. release the buffer.
                    Resource.dispose(buffer);
                    buffer = null;
                    readSink.processRead(attemptedBytesRead, actualBytesRead, null);
                    close = actualBytesRead < 0;
                    break;
                }

                continueReading = readSink.processRead(attemptedBytesRead, actualBytesRead, buffer);
                buffer = null;
            } while (continueReading && !isShutdown(ChannelShutdownDirection.Inbound));

            return close;
        } catch (Throwable t) {
            if (buffer != null) {
                buffer.close();
            }
            throw t;
        }
    }

    /**
     * Write objects to the OS.
     * @param in the collection which contains objects to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link #getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     * @throws Exception if an I/O exception occurs during write.
     */
    protected final int doWrite0(ChannelOutboundBuffer in) throws Exception {
        Object msg = in.current();
        if (msg == null) {
            // Directly return here so incompleteWrite(...) is not called.
            return 0;
        }
        return doWriteInternal(in, in.current());
    }

    private int doWriteInternal(ChannelOutboundBuffer in, Object msg) throws Exception {
        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            if (buf.readableBytes() == 0) {
                in.remove();
                return 0;
            }

            final int localFlushAmount = doWriteBytes(buf);
            if (localFlushAmount > 0) {
                in.progress(localFlushAmount);
                if (buf.readableBytes() == 0) {
                    in.remove();
                }
                return 1;
            }
        } else if (msg instanceof FileRegion) {
            FileRegion region = (FileRegion) msg;
            if (region.transferred() >= region.count()) {
                in.remove();
                return 0;
            }

            long localFlushedAmount = doWriteFileRegion(region);
            if (localFlushedAmount > 0) {
                in.progress(localFlushedAmount);
                if (region.transferred() >= region.count()) {
                    in.remove();
                }
                return 1;
            }
        } else {
            // Should not reach here.
            throw new Error();
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = getWriteSpinCount();
        do {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }
            writeSpinCount -= doWriteInternal(in, msg);
        } while (writeSpinCount > 0);

        incompleteWrite(writeSpinCount < 0);
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) {
        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            if (buf.isDirect()) {
                return msg;
            }

            return newDirectBuffer(buf);
        }

        if (msg instanceof FileRegion) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    protected final void incompleteWrite(boolean setOpWrite) {
        // Did not write completely.
        if (setOpWrite) {
            setOpWrite();
        } else {
            // It is possible that we have set the write OP, woken up by NIO because the socket is writable, and then
            // use our write quantum. In this case we no longer want to set the write OP because the socket is still
            // writable (as far as we know). We will find out next time we attempt to write if the socket is writable
            // and set the write OP if necessary.
            clearOpWrite();

            // Schedule flush again later so other tasks can be picked up in the meantime
            executor().execute(flushTask);
        }
    }

    /**
     * Write a {@link FileRegion}
     *
     * @param region        the {@link FileRegion} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract long doWriteFileRegion(FileRegion region) throws Exception;

    /**
     * Read bytes into the given {@link Buffer} and return the amount.
     */
    protected abstract int doReadBytes(Buffer buf) throws Exception;

    /**
     * Write bytes form the given {@link Buffer} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link Buffer} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract int doWriteBytes(Buffer buf) throws Exception;

    protected final void setOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (key == null || !key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }

    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (key == null || !key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }
}
