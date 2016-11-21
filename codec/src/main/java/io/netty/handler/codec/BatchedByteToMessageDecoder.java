/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public abstract class BatchedByteToMessageDecoder extends ByteToMessageDecoder {

    private boolean readInProgress;

    protected BatchedByteToMessageDecoder() {
        setDiscardAfterReads(1);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        readInProgress = true;
        super.channelRead(ctx, msg);
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!readInProgress) {
            decodeBatched(ctx, in, out);
        }
    }

    /**
     * Decodes from one {@link ByteBuf} to another. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}. Compared to {@link ByteToMessageDecoder#decode(ChannelHandlerContext, ByteBuf, List)}, this
     * method decodes messages by applying the batching strategy. Instead of having one {@link ByteBuf} which
     * would always contain only one message, this method decodes a {@link ByteBuf} so that the resulting
     * {@link ByteBuf} will contain N messages at once (where N can be greater or equal to 1).
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link BatchedByteToMessageDecoder} belongs to
     * @param in the {@link ByteBuf} from which to read data
     * @param out the {@link List} to which decoded messages should be added
     */
    protected abstract void decodeBatched(ChannelHandlerContext ctx, ByteBuf in, List<Object> out);

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        resetReadAndFlushIfNeeded(ctx);
        super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        resetReadAndFlushIfNeeded(ctx);
        super.exceptionCaught(ctx, cause);
    }

    private void resetReadAndFlushIfNeeded(ChannelHandlerContext ctx) {
        readInProgress = false;
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            decodeBatched(ctx, internalBuffer(), out);
        } finally {
            afterDecode(ctx, out);
        }
    }
}
