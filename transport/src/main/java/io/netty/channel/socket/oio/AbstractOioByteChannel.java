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
package io.netty.channel.socket.oio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInputShutdownEvent;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;

import java.io.IOException;

abstract class AbstractOioByteChannel extends AbstractOioChannel {

    private volatile boolean inputShutdown;

    protected AbstractOioByteChannel(Channel parent, Integer id) {
        super(parent, id);
    }

    boolean isInputShutdown() {
        return inputShutdown;
    }

    @Override
    protected OioByteUnsafe newUnsafe() {
        return new OioByteUnsafe();
    }

    private final class OioByteUnsafe extends AbstractOioUnsafe {
        @Override
        public void read() {
            assert eventLoop().inEventLoop();

            if (inputShutdown) {
                try {
                    Thread.sleep(SO_TIMEOUT);
                } catch (InterruptedException e) {
                    // ignore
                }
                return;
            }

            final ChannelPipeline pipeline = pipeline();
            final ByteBuf byteBuf = pipeline.inboundByteBuffer();
            boolean closed = false;
            boolean read = false;
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

                    if (byteBuf.writable()) {
                        continue;
                    }

                    final int capacity = byteBuf.capacity();
                    final int maxCapacity = byteBuf.maxCapacity();
                    if (capacity == maxCapacity) {
                        if (read) {
                            read = false;
                            pipeline.fireInboundBufferUpdated();
                            if (!byteBuf.writable()) {
                                throw new IllegalStateException(
                                        "an inbound handler whose buffer is full must consume at " +
                                        "least one byte.");
                            }
                        }
                    } else {
                        final int writerIndex = byteBuf.writerIndex();
                        if (writerIndex + available > maxCapacity) {
                            byteBuf.capacity(maxCapacity);
                        } else {
                            byteBuf.ensureWritableBytes(available);
                        }
                    }
                }
            } catch (Throwable t) {
                if (read) {
                    read = false;
                    pipeline.fireInboundBufferUpdated();
                }
                pipeline().fireExceptionCaught(t);
                if (t instanceof IOException) {
                    close(voidFuture());
                }
            } finally {
                if (read) {
                    pipeline.fireInboundBufferUpdated();
                }
                if (closed) {
                    inputShutdown = true;
                    if (isOpen()) {
                        if (Boolean.TRUE.equals(config().getOption(ChannelOption.ALLOW_HALF_CLOSURE))) {
                            pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                        } else {
                            close(voidFuture());
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void doFlushByteBuffer(ByteBuf buf) throws Exception {
        while (buf.readable()) {
            doWriteBytes(buf);
        }
        buf.clear();
    }

    protected abstract int available();
    protected abstract int doReadBytes(ByteBuf buf) throws Exception;
    protected abstract void doWriteBytes(ByteBuf buf) throws Exception;
}
