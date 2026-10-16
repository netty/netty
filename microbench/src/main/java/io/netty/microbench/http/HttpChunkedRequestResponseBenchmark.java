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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;

import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;

@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
public class HttpChunkedRequestResponseBenchmark extends AbstractMicrobenchmark {
    private static final int CRLF_SHORT = (CR << 8) + LF;
    private static final long SEED = 0xDEADBEEFL;

    // Token chars valid in chunk-ext-name and chunk-ext-val (token)
    private static final String TOKEN_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&'*+-.^_`|~";
    // Chars valid inside quoted strings (qdtext excluding \ and ")
    private static final String QDTEXT_CHARS =
            "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&'()*+,-./:;<=>?@[]^_`{|}~\t";

    @Param({ "16" })
    int chunks;

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
                ByteBufUtil.writeAscii(buffer, "HTTP/1.1 200 OK\r\n");
                ByteBufUtil.writeAscii(buffer, "Content-Length: 0\r\n\r\n");
                ctx.write(buffer, ctx.voidPromise());
            }
        };
        nettyChannel = new EmbeddedChannel(httpRequestDecoder, inboundHandlerAdapter);

        Random rng = new Random(SEED);
        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtil.writeAscii(buffer, "POST / HTTP/1.1\r\n");
        ByteBufUtil.writeAscii(buffer, "Content-Type: text/plain\r\n");
        ByteBufUtil.writeAscii(buffer, "Transfer-Encoding: chunked\r\n\r\n");

        for (int c = 0; c < chunks; c++) {
            int dataLen = 1 + rng.nextInt(64);
            StringBuilder chunkLine = new StringBuilder();
            chunkLine.append(Integer.toHexString(dataLen));

            // Generate a random extension pattern for this chunk
            int extType = rng.nextInt(5);
            switch (extType) {
                case 0:
                    // No extension
                    break;
                case 1:
                    // Simple name-only extensions: ;A;B;C...
                    appendNameOnlyExtensions(chunkLine, rng);
                    break;
                case 2:
                    // Token value extension: ;name=tokenvalue
                    appendTokenValueExtensions(chunkLine, rng);
                    break;
                case 3:
                    // Quoted value extension: ;name="quoted value with \" escapes"
                    appendQuotedValueExtensions(chunkLine, rng);
                    break;
                case 4:
                    // Mixed: combination of all extension types
                    appendMixedExtensions(chunkLine, rng);
                    break;
                default:
                    break;
            }

            ByteBufUtil.writeAscii(buffer, chunkLine.toString() + "\r\n");
            buffer.writeZero(dataLen);
            buffer.writeShort(CRLF_SHORT);
        }
        ByteBufUtil.writeAscii(buffer, "0\r\n\r\n");
        POST = Unpooled.unreleasableBuffer(buffer);
        readerIndex = POST.readerIndex();
        writeIndex = POST.writerIndex();
    }

    private static void appendNameOnlyExtensions(StringBuilder sb, Random rng) {
        int count = 2 + rng.nextInt(30);
        for (int i = 0; i < count; i++) {
            sb.append(';');
            appendRandomToken(sb, rng, 1 + rng.nextInt(8));
        }
    }

    private static void appendTokenValueExtensions(StringBuilder sb, Random rng) {
        int count = 1 + rng.nextInt(5);
        for (int i = 0; i < count; i++) {
            sb.append(';');
            appendRandomToken(sb, rng, 1 + rng.nextInt(12));
            sb.append('=');
            appendRandomToken(sb, rng, 1 + rng.nextInt(30));
        }
    }

    private static void appendQuotedValueExtensions(StringBuilder sb, Random rng) {
        int count = 1 + rng.nextInt(4);
        for (int i = 0; i < count; i++) {
            sb.append(';');
            appendRandomToken(sb, rng, 1 + rng.nextInt(10));
            sb.append("=\"");
            int qlen = 3 + rng.nextInt(40);
            for (int j = 0; j < qlen; j++) {
                if (rng.nextInt(10) == 0) {
                    // Insert a quoted-pair escape
                    sb.append('\\');
                    sb.append(QDTEXT_CHARS.charAt(rng.nextInt(QDTEXT_CHARS.length())));
                } else {
                    sb.append(QDTEXT_CHARS.charAt(rng.nextInt(QDTEXT_CHARS.length())));
                }
            }
            sb.append('"');
        }
    }

    private static void appendMixedExtensions(StringBuilder sb, Random rng) {
        int count = 2 + rng.nextInt(6);
        for (int i = 0; i < count; i++) {
            sb.append(';');
            appendRandomToken(sb, rng, 1 + rng.nextInt(8));
            int valType = rng.nextInt(3);
            if (valType == 1) {
                sb.append('=');
                appendRandomToken(sb, rng, 1 + rng.nextInt(20));
            } else if (valType == 2) {
                sb.append("=\"");
                int qlen = 2 + rng.nextInt(20);
                for (int j = 0; j < qlen; j++) {
                    if (rng.nextInt(8) == 0) {
                        sb.append('\\');
                        sb.append(QDTEXT_CHARS.charAt(rng.nextInt(QDTEXT_CHARS.length())));
                    } else {
                        sb.append(QDTEXT_CHARS.charAt(rng.nextInt(QDTEXT_CHARS.length())));
                    }
                }
                sb.append('"');
            }
        }
    }

    private static void appendRandomToken(StringBuilder sb, Random rng, int len) {
        for (int i = 0; i < len; i++) {
            sb.append(TOKEN_CHARS.charAt(rng.nextInt(TOKEN_CHARS.length())));
        }
    }

    @Benchmark
    public Object netty() {
        POST.setIndex(readerIndex, writeIndex);
        ByteBuf byteBuf = POST.retainedDuplicate();
        nettyChannel.writeInbound(byteBuf);
        return nettyChannel.outboundMessages().poll();
    }
}
