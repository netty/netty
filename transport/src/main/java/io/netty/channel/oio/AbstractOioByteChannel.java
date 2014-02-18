/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.oio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;

/**
 * Abstract base class for OIO which reads and writes bytes from/to a Socket
 */
public abstract class AbstractOioByteChannel extends AbstractOioChannel {

    private volatile boolean inputShutdown;
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    protected AbstractOioByteChannel(Channel parent, EventLoop eventLoop) {
        super(parent, eventLoop);
    }

    protected boolean isInputShutdown() {
        return inputShutdown;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    /**
     * Check if the input was shutdown and if so return {@code true}. The default implementation sleeps also for
     * {@link #SO_TIMEOUT} milliseconds to simulate some blocking.
     */
    protected boolean checkInputShutdown() {
        if (inputShutdown) {
            try {
                Thread.sleep(SO_TIMEOUT);
            } catch (InterruptedException e) {
                // ignore
            }
            return true;
        }
        return false;
    }

    @Override
    protected void doRead() {
        if (checkInputShutdown()) {
            return;
        }

        final ChannelPipeline pipeline = pipeline();

        // TODO: calculate size as in 3.x
        ByteBuf byteBuf = alloc().buffer();
        boolean closed = false;
        boolean read = false;
        Throwable exception = null;
        try {
            for (;;) {
                int localReadAmount = doReadBytes(byteBuf);
                if (localReadAmount > 0) {
                    read = true;
                } else if (localReadAmount < 0) {
                    closed = true;
                }

                final int available = available();
                if (available <= 0) {
                    break;
                }

                if (!byteBuf.isWritable()) {
                    final int capacity = byteBuf.capacity();
                    final int maxCapacity = byteBuf.maxCapacity();
                    if (capacity == maxCapacity) {
                        if (read) {
                            read = false;
                            pipeline.fireChannelRead(byteBuf);
                            byteBuf = alloc().buffer();
                        }
                    } else {
                        final int writerIndex = byteBuf.writerIndex();
                        if (writerIndex + available > maxCapacity) {
                            byteBuf.capacity(maxCapacity);
                        } else {
                            byteBuf.ensureWritable(available);
                        }
                    }
                }
                if (!config().isAutoRead()) {
                    // stop reading until next Channel.read() call
                    // See https://github.com/netty/netty/issues/1363
                    break;
                }
            }
        } catch (Throwable t) {
            exception = t;
        } finally {
            if (read) {
                pipeline.fireChannelRead(byteBuf);
            } else {
                // nothing read into the buffer so release it
                byteBuf.release();
            }

            pipeline.fireChannelReadComplete();
            if (exception != null) {
                if (exception instanceof IOException) {
                    closed = true;
                    pipeline().fireExceptionCaught(exception);
                } else {
                    pipeline.fireExceptionCaught(exception);
                    unsafe().close(voidPromise());
                }
            }

            if (closed) {
                inputShutdown = true;
                if (isOpen()) {
                    if (Boolean.TRUE.equals(config().getOption(ChannelOption.ALLOW_HALF_CLOSURE))) {
                        pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                    } else {
                        unsafe().close(unsafe().voidPromise());
                    }
                }
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (;;) {
            Object msg = in.current();
            if (msg == null) {
                // nothing left to write
                break;
            }
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                while (buf.isReadable()) {
                    doWriteBytes(buf);
                }
                in.remove();
            } else if (msg instanceof FileRegion) {
                doWriteFileRegion((FileRegion) msg);
                in.remove();
            } else {
                in.remove(new UnsupportedOperationException(
                        "unsupported message type: " + StringUtil.simpleClassName(msg)));
            }
        }
    }

    /**
     * Return the number of bytes ready to read from the underlying Socket.
     */
    protected abstract int available();

    /**
     * Read bytes from the underlying Socket.
     *
     * @param buf           the {@link ByteBuf} into which the read bytes will be written
     * @return amount       the number of bytes read. This may return a negative amount if the underlying
     *                      Socket was closed
     * @throws Exception    is thrown if an error occurred
     */
    protected abstract int doReadBytes(ByteBuf buf) throws Exception;

    /**
     * Write the data which is hold by the {@link ByteBuf} to the underlying Socket.
     *
     * @param buf           the {@link ByteBuf} which holds the data to transfer
     * @throws Exception    is thrown if an error occurred
     */
    protected abstract void doWriteBytes(ByteBuf buf) throws Exception;

    /**
     * Write the data which is hold by the {@link FileRegion} to the underlying Socket.
     *
     * @param region        the {@link FileRegion} which holds the data to transfer
     * @throws Exception    is thrown if an error occurred
     */
    protected abstract void doWriteFileRegion(FileRegion region) throws Exception;
}
