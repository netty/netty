/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.internal.ObjectUtil;

/**
 * A {@link ChunkedInput} that fetches data chunk by chunk for use with HTTP/2 Data Frames.
 * <p>
 * Each chunk from the input data will be wrapped within a {@link Http2DataFrame}. At the end of the input data,
 * {@link Http2DataFrame#isEndStream()} will be set to true and will be written.
 * <p>
 * <p>
 * <pre>
 *
 *     public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
 *         if (msg instanceof Http2HeadersFrame) {
 *             Http2HeadersFrame http2HeadersFrame = (Http2HeadersFrame) msg;
 *
 *             Http2HeadersFrame response = new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status("200"));
 *             response.stream(http2HeadersFrame.stream());
 *             ctx.write(response);
 *
 *             ChannelFuture sendFileFuture = ctx.writeAndFlush(new Http2DataChunkedInput(
 *                     new ChunkedFile(new File(("/home/meow/cats.mp4"))), http2HeadersFrame.stream()));
 *         }
 *     }
 * </pre>
 */
public final class Http2DataChunkedInput implements ChunkedInput<Http2DataFrame> {

    private final ChunkedInput<ByteBuf> input;
    private final Http2FrameStream stream;
    private boolean endStreamSent;

    /**
     * Creates a new instance using the specified input.
     *
     * @param input  {@link ChunkedInput} containing data to write
     * @param stream {@link Http2FrameStream} holding stream info
     */
    public Http2DataChunkedInput(ChunkedInput<ByteBuf> input, Http2FrameStream stream) {
        this.input = ObjectUtil.checkNotNull(input, "input");
        this.stream = ObjectUtil.checkNotNull(stream, "stream");
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        if (input.isEndOfInput()) {
            // Only end of input after last HTTP chunk has been sent
            return endStreamSent;
        }
        return false;
    }

    @Override
    public void close() throws Exception {
        input.close();
    }

    @Deprecated
    @Override
    public Http2DataFrame readChunk(ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }

    @Override
    public Http2DataFrame readChunk(ByteBufAllocator allocator) throws Exception {
        if (endStreamSent) {
            return null;
        }

        if (input.isEndOfInput()) {
            endStreamSent = true;
            return new DefaultHttp2DataFrame(true).stream(stream);
        }

        ByteBuf buf = input.readChunk(allocator);
        if (buf == null) {
            return null;
        }

        final Http2DataFrame dataFrame = new DefaultHttp2DataFrame(buf, input.isEndOfInput()).stream(stream);
        if (dataFrame.isEndStream()) {
            endStreamSent = true;
        }

        return dataFrame;
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
