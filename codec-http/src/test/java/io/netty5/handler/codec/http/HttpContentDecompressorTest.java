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
package io.netty5.handler.codec.http;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpContentDecompressorTest {

    // See https://github.com/netty/netty/issues/8915.
    @Test
    public void testInvokeReadWhenNotProduceMessage() {
        final AtomicInteger readCalled = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
                readCalled.incrementAndGet();
                ctx.read(readBufferAllocator);
            }
        }, new HttpContentDecompressor(), new ChannelHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ctx.fireChannelRead(msg);
                ctx.read();
            }
        });

        channel.setOption(ChannelOption.AUTO_READ, false);

        readCalled.set(0);
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_ENCODING, "gzip");
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json;charset=UTF-8");
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        assertTrue(channel.writeInbound(response));

        // we triggered read explicitly
        assertEquals(1, readCalled.get());

        assertTrue(channel.readInbound() instanceof HttpResponse);

        assertFalse(channel.writeInbound(new DefaultHttpContent(channel.bufferAllocator().allocate(0))));

        // read was triggered by the HttpContentDecompressor itself as it did not produce any message to the next
        // inbound handler.
        assertEquals(2, readCalled.get());
        assertFalse(channel.finishAndReleaseAll());
    }
}
