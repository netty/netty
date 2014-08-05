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
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FileRegion;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;

/**
 * Abstract base class for OIO which reads and writes bytes from/to a Socket
 */
public abstract class AbstractOioByteChannel extends AbstractOioChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(FileRegion.class) + ')';

    private RecvByteBufAllocator.Handle allocHandle;
    private volatile boolean inputShutdown;

    /**
     * @see AbstractOioByteChannel#AbstractOioByteChannel(Channel)
     */
    protected AbstractOioByteChannel(Channel parent) {
        super(parent);
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
        final ChannelConfig config = config();
        final ChannelPipeline pipeline = pipeline();

        RecvByteBufAllocator.Handle allocHandle = this.allocHandle;
        if (allocHandle == null) {
            this.allocHandle = allocHandle = config.getRecvByteBufAllocator().newHandle();
        }

        ByteBuf byteBuf = allocHandle.allocate(alloc());

        boolean closed = false;
        boolean read = false;
        Throwable exception = null;
        int localReadAmount = 0;
        try {
            int totalReadAmount = 0;

            for (;;) {
                localReadAmount = doReadBytes(byteBuf);
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

                if (totalReadAmount >= Integer.MAX_VALUE - localReadAmount) {
                    // Avoid overflow.
                    totalReadAmount = Integer.MAX_VALUE;
                    break;
                }

                totalReadAmount += localReadAmount;

                if (!config.isAutoRead()) {
                    // stop reading until next Channel.read() call
                    // See https://github.com/netty/netty/issues/1363
                    break;
                }
            }
            allocHandle.record(totalReadAmount);

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
            if (localReadAmount == 0 && isActive()) {
                // If the read amount was 0 and the channel is still active we need to trigger a new read()
                // as otherwise we will never try to read again and the user will never know.
                // Just call read() is ok here as it will be submitted to the EventLoop as a task and so we are
                // able to process the rest of the tasks in the queue first.
                //
                // See https://github.com/netty/netty/issues/2404
                read();
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
                int readableBytes = buf.readableBytes();
                while (readableBytes > 0) {
                    doWriteBytes(buf);
                    int newReadableBytes = buf.readableBytes();
                    in.progress(readableBytes - newReadableBytes);
                    readableBytes = newReadableBytes;
                }
                in.remove();
            } else if (msg instanceof FileRegion) {
                FileRegion region = (FileRegion) msg;
                long transfered = region.transfered();
                doWriteFileRegion(region);
                in.progress(region.transfered() - transfered);
                in.remove();
            } else {
                in.remove(new UnsupportedOperationException(
                        "unsupported message type: " + StringUtil.simpleClassName(msg)));
            }
        }
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) throws Exception {
        if (msg instanceof ByteBuf || msg instanceof FileRegion) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
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
