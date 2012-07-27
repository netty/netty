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
import io.netty.channel.ChannelPipeline;

import java.io.IOException;

abstract class AbstractOioByteChannel extends AbstractOioChannel {

    protected AbstractOioByteChannel(Channel parent, Integer id) {
        super(parent, id);
    }

    @Override
    protected abstract OioByteUnsafe newUnsafe();

    abstract class OioByteUnsafe extends AbstractOioUnsafe {
        @Override
        public void read() {
            assert eventLoop().inEventLoop();

            final ChannelPipeline pipeline = pipeline();
            final ByteBuf byteBuf = pipeline.inboundByteBuffer();
            boolean closed = false;
            boolean read = false;
            try {
                expandReadBuffer(byteBuf);
                int localReadAmount = doReadBytes(byteBuf);
                if (localReadAmount > 0) {
                    read = true;
                } else if (localReadAmount < 0) {
                    closed = true;
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
                if (closed && isOpen()) {
                    close(voidFuture());
                }
            }
        }

        private void expandReadBuffer(ByteBuf byteBuf) {
            int available = available();
            if (available > 0) {
                byteBuf.ensureWritableBytes(available);
            } else if (!byteBuf.writable()) {
                // FIXME: Magic number
                byteBuf.ensureWritableBytes(4096);
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
