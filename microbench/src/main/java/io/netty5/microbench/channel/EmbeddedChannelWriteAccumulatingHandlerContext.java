/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.microbench.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.ByteToMessageDecoderForBuffer;
import io.netty5.util.concurrent.Future;

import static java.util.Objects.requireNonNull;

public abstract class EmbeddedChannelWriteAccumulatingHandlerContext extends EmbeddedChannelHandlerContext {
    private Buffer cumulation;
    private final ByteToMessageDecoderForBuffer.Cumulator cumulator;

    protected EmbeddedChannelWriteAccumulatingHandlerContext(BufferAllocator alloc, ChannelHandler handler,
                                                             ByteToMessageDecoderForBuffer.Cumulator writeCumulator) {
        this(alloc, handler, writeCumulator, new EmbeddedChannel());
    }

    protected EmbeddedChannelWriteAccumulatingHandlerContext(BufferAllocator alloc, ChannelHandler handler,
                                                             ByteToMessageDecoderForBuffer.Cumulator writeCumulator,
                                                             EmbeddedChannel channel) {
        super(alloc, handler, channel);
        cumulator = requireNonNull(writeCumulator, "writeCumulator");
    }

    public final Buffer cumulation() {
        return cumulation;
    }

    public final void releaseCumulation() {
        if (cumulation != null) {
            cumulation.close();
            cumulation = null;
        }
    }

    @Override
    public final Future<Void> write(Object msg) {
        try {
            if (msg instanceof Buffer) {
                if (cumulation == null) {
                    cumulation = (Buffer) msg;
                } else {
                    cumulation = cumulator.cumulate(bufferAllocator(), cumulation, (Buffer) msg);
                }
                return channel().newSucceededFuture();
            }
            return channel().write(msg);
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> writeAndFlush(Object msg) {
        try {
            if (msg instanceof Buffer) {
                Buffer buf = (Buffer) msg;
                if (cumulation == null) {
                    cumulation = buf;
                } else {
                    cumulation = cumulator.cumulate(bufferAllocator(), cumulation, buf);
                }
                return channel().newSucceededFuture();
            } else {
                return channel().writeAndFlush(msg);
            }
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }
}
