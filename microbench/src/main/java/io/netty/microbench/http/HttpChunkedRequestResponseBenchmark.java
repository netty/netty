/*
 * Copyright 2026 The Netty Project
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
package io.netty.microbench.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;

@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
public class HttpChunkedRequestResponseBenchmark extends AbstractMicrobenchmark {
    private static final int CRLF_SHORT = (CR << 8) + LF;

    ByteBuf POST;
    int readerIndex;
    int writeIndex;
    EmbeddedChannel nettyChannel;

    @Setup
    public void setup() {
        HttpRequestDecoder httpRequestDecoder = new HttpRequestDecoder(
                HttpRequestDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH, HttpRequestDecoder.DEFAULT_MAX_HEADER_SIZE,
                HttpRequestDecoder.DEFAULT_MAX_CHUNK_SIZE, false);
        ChannelInboundHandlerAdapter inboundHandlerAdapter = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object o) {
                // this is saving a slow type check on LastHttpContent vs HttpRequest
                try {
                    if (o == LastHttpContent.EMPTY_LAST_CONTENT) {
                        writeResponse(ctx);
                    }
                } finally {
                    ReferenceCountUtil.release(o);
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                ctx.flush();
            }

            private void writeResponse(ChannelHandlerContext ctx) {
                ByteBuf buffer = ctx.alloc().buffer();
                // Build the response object.
                ByteBufUtil.writeAscii(buffer, "HTTP/1.1 200 OK\r\n");
                ByteBufUtil.writeAscii(buffer, "Content-Length: 0\r\n\r\n");
                ctx.write(buffer, ctx.voidPromise());
            }
        };
        nettyChannel = new EmbeddedChannel(httpRequestDecoder, inboundHandlerAdapter);

        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtil.writeAscii(buffer, "POST / HTTP/1.1\r\n");
        ByteBufUtil.writeAscii(buffer, "Content-Type: text/plain\r\n");
        ByteBufUtil.writeAscii(buffer, "Transfer-Encoding: chunked\r\n\r\n");
        ByteBufUtil.writeAscii(buffer, Integer.toHexString(43) + "\r\n");
        buffer.writeZero(43);
        buffer.writeShort(CRLF_SHORT);
        ByteBufUtil.writeAscii(buffer, Integer.toHexString(18) +
                ";extension=kjhkasdhfiushdksjfnskdjfbskdjfbskjdfb\r\n");
        buffer.writeZero(18);
        buffer.writeShort(CRLF_SHORT);
        ByteBufUtil.writeAscii(buffer, Integer.toHexString(29) +
                ";a=12938746238;b=\"lkjkjhskdfhsdkjh\\\"kjshdflkjhdskjhifuwehwi\";c=lkjdshfkjshdiufh\r\n");
        buffer.writeZero(29);
        buffer.writeShort(CRLF_SHORT);
        ByteBufUtil.writeAscii(buffer, Integer.toHexString(9) +
                ";A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A;A\r\n");
        buffer.writeZero(9);
        buffer.writeShort(CRLF_SHORT);
        ByteBufUtil.writeAscii(buffer, "0\r\n\r\n"); // Last empty chunk
        POST = Unpooled.unreleasableBuffer(buffer);
        readerIndex = POST.readerIndex();
        writeIndex = POST.writerIndex();
    }

    @Benchmark
    public Object netty() {
        POST.setIndex(readerIndex, writeIndex);
        ByteBuf byteBuf = POST.retainedDuplicate();
        nettyChannel.writeInbound(byteBuf);
        return nettyChannel.outboundMessages().poll();
    }
}
