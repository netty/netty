/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.RecyclableArrayList;

public final class BackpressureDecompressionHandler extends ChannelDuplexHandler {
    private final Decompressor decompressor;
    private RecyclableArrayList inputBuffer;
    private boolean closed;
    private boolean downstreamWantsMore;
    private boolean reading;

    public BackpressureDecompressionHandler(Decompressor decompressor) {
        this.decompressor = decompressor;
    }

    private void handleException(Exception e) {
        try {
            decompressor.close();
        } catch (Exception s) {
            e.addSuppressed(s);
        }
        closed = true;
    }

    private Decompressor.Status status() {
        try {
            return decompressor.status();
        } catch (Exception e) {
            handleException(e);
            throw e;
        }
    }

    private boolean downstreamWantsMore(ChannelHandlerContext ctx) {
        return downstreamWantsMore || ctx.channel().config().isAutoRead();
    }

    private void processSome(ChannelHandlerContext ctx) {
        while (!closed && downstreamWantsMore(ctx)) {
            Decompressor.Status status = status();
            switch (status) {
                case NEED_OUTPUT:
                    ByteBuf buf;
                    try {
                        buf = decompressor.takeOutput();
                    } catch (Exception e) {
                        handleException(e);
                        throw e;
                    }
                    if (!buf.isReadable()) {
                        // try again.
                        buf.release();
                        break;
                    }
                    downstreamWantsMore = false;
                    ctx.fireChannelRead(buf);
                    ctx.fireChannelReadComplete();
                    break;
                case NEED_INPUT:
                    if (inputBuffer == null) {
                        return;
                    }
                    Object item = inputBuffer.remove(0);
                    if (inputBuffer.isEmpty()) {
                        inputBuffer.recycle();
                        inputBuffer = null;
                    }
                    try {
                        decompressor.addInput((ByteBuf) item);
                    } catch (Exception e) {
                        handleException(e);
                        throw e;
                    }
                    break;
                case COMPLETE:
                    decompressor.close();
                    closed = true;
                    break;
                default:
                    throw new AssertionError("Unknown status: " + status);
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        reading = true;
        if (closed) {
            ReferenceCountUtil.release(msg);
            return;
        }
        if (status() == Decompressor.Status.NEED_INPUT) {
            try {
                decompressor.addInput((ByteBuf) msg);
            } catch (Exception e) {
                handleException(e);
                throw e;
            }
        } else {
            if (inputBuffer == null) {
                inputBuffer = RecyclableArrayList.newInstance();
            }
            inputBuffer.add(msg);
        }
        processSome(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        reading = false;
        if (!ctx.channel().config().isAutoRead() && downstreamWantsMore) {
            ctx.read();
        }
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (closed) {
            return;
        }
        downstreamWantsMore = true;
        processSome(ctx);
        if (downstreamWantsMore && !reading) {
           ctx.read();
        }
    }
}
