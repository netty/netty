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

import io.netty5.buffer.Buffer;
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;
import io.netty5.util.Resource;
import io.netty5.channel.Channel;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FileRegion;
import io.netty5.util.internal.StringUtil;

import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on bytes.
 */
public abstract class AbstractNioByteChannel<P extends Channel, L extends SocketAddress, R extends SocketAddress>
        extends AbstractNioChannel<P, L, R> {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(Buffer.class) + ", " +
            StringUtil.simpleClassName(FileRegion.class) + ')';

    /**
     * Create a new instance
     *
     * @param parent                        the parent {@link Channel} by which this instance was created.
     *                                      May be {@code null}
     * @param eventLoop                     the {@link EventLoop} to use for IO.
     * @param defaultWriteHandleFactory     the {@link WriteHandleFactory} that is used by default.
     * @param ch                            the underlying {@link SelectableChannel} on which it operates
     */
    protected AbstractNioByteChannel(P parent, EventLoop eventLoop, WriteHandleFactory defaultWriteHandleFactory,
                                     SelectableChannel ch) {
        super(parent, eventLoop, false, new AdaptiveReadHandleFactory(), defaultWriteHandleFactory,
                ch, NioIoOps.READ);
    }

    @Override
    protected final boolean doReadNow(ReadSink readSink) throws Exception {
        Buffer buffer = null;
        try {
            buffer = readSink.allocateBuffer();
            if (buffer == null) {
                readSink.processRead(0, 0, null);
                return false;
            }
            int attemptedBytesRead = buffer.writableBytes();
            int actualBytesRead = doReadBytes(buffer);
            if (actualBytesRead <= 0) {
                // nothing was read. release the buffer.
                Resource.dispose(buffer);
                buffer = null;
                readSink.processRead(attemptedBytesRead, actualBytesRead, null);
                return actualBytesRead < 0;
            }

            readSink.processRead(attemptedBytesRead, actualBytesRead, buffer);
            buffer = null;
            return false;
        } catch (Throwable t) {
            if (buffer != null) {
                buffer.close();
            }
            throw t;
        }
    }

    @Override
    protected void doWriteNow(WriteSink writeSink) throws Exception {
        Object msg = writeSink.currentFlushedMessage();

        final long attemptedBytesWrite;
        final long actualBytesWrite;
        final int messages;
        final boolean continueWriting;
        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            if (buf.readableBytes() == 0) {
                attemptedBytesWrite = 0;
                actualBytesWrite = 0;
                messages = 1;
                continueWriting = true;
            } else {
                attemptedBytesWrite = buf.readableBytes();
                actualBytesWrite = doWriteBytes(buf);
                messages = actualBytesWrite == attemptedBytesWrite ? 1 : 0;
                continueWriting = actualBytesWrite > 0;
            }
        } else if (msg instanceof FileRegion) {
            FileRegion region = (FileRegion) msg;
            if (region.transferred() >= region.count()) {
                attemptedBytesWrite = 0;
                actualBytesWrite = 0;
                messages = 1;
                continueWriting = true;
            } else {
                attemptedBytesWrite = region.count();
                actualBytesWrite = doWriteFileRegion(region);
                messages = actualBytesWrite == attemptedBytesWrite ? 1 : 0;
                continueWriting = actualBytesWrite > 0;
            }
        } else {
            // Should not reach here.
            throw new Error();
        }
        writeSink.complete(attemptedBytesWrite, actualBytesWrite, messages, continueWriting);
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
}
