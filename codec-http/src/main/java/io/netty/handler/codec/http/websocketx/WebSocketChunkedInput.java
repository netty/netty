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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.internal.ObjectUtil;

/**
 * A {@link ChunkedInput} that fetches data chunk by chunk for use with WebSocket chunked transfers.
 * <p>
 * Each chunk from the input data will be wrapped within a {@link ContinuationWebSocketFrame}.
 * At the end of the input data, {@link ContinuationWebSocketFrame} with finalFragment will be written.
 * <p>
 */
public final class WebSocketChunkedInput implements ChunkedInput<WebSocketFrame> {
    private final ChunkedInput<ByteBuf> input;
    private final int rsv;

    /**
     * Creates a new instance using the specified input.
     * @param input {@link ChunkedInput} containing data to write
     */
    public WebSocketChunkedInput(ChunkedInput<ByteBuf> input) {
        this(input, 0);
    }

    /**
     * Creates a new instance using the specified input.
     * @param input {@link ChunkedInput} containing data to write
     * @param rsv RSV1, RSV2, RSV3 used for extensions
     *
     * @throws  NullPointerException if {@code input} is null
     */
    public WebSocketChunkedInput(ChunkedInput<ByteBuf> input, int rsv) {
        this.input = ObjectUtil.checkNotNull(input, "input");
        this.rsv = rsv;
    }

    /**
     * @return {@code true} if and only if there is no data left in the stream
     * and the stream has reached at its end.
     */
    @Override
    public boolean isEndOfInput() throws Exception {
        return input.isEndOfInput();
    }

    /**
     * Releases the resources associated with the input.
     */
    @Override
    public void close() throws Exception {
        input.close();
    }

    /**
     * @deprecated Use {@link #readChunk(ByteBufAllocator)}.
     *
     * Fetches a chunked data from the stream. Once this method returns the last chunk
     * and thus the stream has reached at its end, any subsequent {@link #isEndOfInput()}
     * call must return {@code true}.
     *
     * @param ctx {@link ChannelHandlerContext} context of channelHandler
     * @return {@link WebSocketFrame} contain chunk of data
     */
    @Deprecated
    @Override
    public WebSocketFrame readChunk(ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }

    /**
     * Fetches a chunked data from the stream. Once this method returns the last chunk
     * and thus the stream has reached at its end, any subsequent {@link #isEndOfInput()}
     * call must return {@code true}.
     *
     * @param allocator {@link ByteBufAllocator}
     * @return {@link WebSocketFrame} contain chunk of data
     */
    @Override
    public WebSocketFrame readChunk(ByteBufAllocator allocator) throws Exception {
        ByteBuf buf = input.readChunk(allocator);
        if (buf == null) {
            return null;
        }
        return new ContinuationWebSocketFrame(input.isEndOfInput(), rsv, buf);
    }

    @Override
    public long length() {
        return input.length();
    }

    @Override
    public long progress() {
        return input.progress();
    }
}
