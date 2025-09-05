/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.Zstd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpContentDecompressorTest {

    // See https://github.com/netty/netty/issues/8915.
    @Test
    public void testInvokeReadWhenNotProduceMessage() {
        final AtomicInteger readCalled = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void read(ChannelHandlerContext ctx) {
                readCalled.incrementAndGet();
                ctx.read();
            }
        }, new HttpContentDecompressor(0), new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ctx.fireChannelRead(msg);
                ctx.read();
            }
        });

        channel.config().setAutoRead(false);

        readCalled.set(0);
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_ENCODING, "gzip");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json;charset=UTF-8");
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        assertTrue(channel.writeInbound(response));

        // we triggered read explicitly
        assertEquals(1, readCalled.get());

        assertTrue(channel.readInbound() instanceof HttpResponse);

        assertFalse(channel.writeInbound(new DefaultHttpContent(Unpooled.EMPTY_BUFFER)));

        // read was triggered by the HttpContentDecompressor itself as it did not produce any message to the next
        // inbound handler.
        assertEquals(2, readCalled.get());
        assertFalse(channel.finishAndReleaseAll());
    }

    static String[] encodings() {
        List<String> encodings = new ArrayList<>();
        encodings.add("gzip");
        encodings.add("deflate");
        if (Brotli.isAvailable()) {
            encodings.add("br");
        }
        if (Zstd.isAvailable()) {
            encodings.add("zstd");
        }
        encodings.add("snappy");
        return encodings.toArray(new String[0]);
    }

    @ParameterizedTest
    @MethodSource("encodings")
    public void testZipBomb(String encoding) {
        int chunkSize = 1024 * 1024;
        int numberOfChunks = 256;
        int memoryLimit = chunkSize * 128;

        EmbeddedChannel compressionChannel = new EmbeddedChannel(new HttpContentCompressor());
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        req.headers().set(HttpHeaderNames.ACCEPT_ENCODING, encoding);
        compressionChannel.writeInbound(req);

        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        compressionChannel.writeOutbound(response);

        for (int i = 0; i < numberOfChunks; i++) {
            ByteBuf buffer = compressionChannel.alloc().buffer(chunkSize);
            buffer.writeZero(chunkSize);
            compressionChannel.writeOutbound(new DefaultHttpContent(buffer));
        }
        compressionChannel.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);
        compressionChannel.finish();
        compressionChannel.releaseInbound();

        ByteBuf compressed = compressionChannel.alloc().buffer();
        HttpMessage message = null;
        while (true) {
            HttpObject obj = compressionChannel.readOutbound();
            if (obj == null) {
                break;
            }
            if (obj instanceof HttpMessage) {
                message = (HttpMessage) obj;
            }
            if (obj instanceof HttpContent) {
                HttpContent content = (HttpContent) obj;
                compressed.writeBytes(content.content());
                content.release();
            }
        }

        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false);

        ZipBombIncomingHandler incomingHandler = new ZipBombIncomingHandler(memoryLimit);
        EmbeddedChannel decompressChannel = new EmbeddedChannel(new HttpContentDecompressor(0), incomingHandler);
        decompressChannel.config().setAllocator(allocator);
        decompressChannel.writeInbound(message);
        decompressChannel.writeInbound(new DefaultLastHttpContent(compressed));

        assertEquals((long) chunkSize * numberOfChunks, incomingHandler.total);
    }

    private static final class ZipBombIncomingHandler extends ChannelInboundHandlerAdapter {
        final int memoryLimit;
        long total;

        ZipBombIncomingHandler(int memoryLimit) {
            this.memoryLimit = memoryLimit;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            PooledByteBufAllocator allocator = (PooledByteBufAllocator) ctx.alloc();
            assertTrue(allocator.metric().usedHeapMemory() < memoryLimit);
            assertTrue(allocator.metric().usedDirectMemory() < memoryLimit);

            if (msg instanceof HttpContent) {
                HttpContent buf = (HttpContent) msg;
                total += buf.content().readableBytes();
                buf.release();
            }
        }
    }
}
