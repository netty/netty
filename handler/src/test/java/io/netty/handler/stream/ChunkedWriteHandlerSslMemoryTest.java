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
package io.netty.handler.stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChunkedWriteHandlerSslMemoryTest {

    @Test
    public void testUnflushedSslWritesDoNotAffectChannelWritability() throws Exception {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true);
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(true);

        // startTls avoids handshake output changing the channel's writability before the test writes application data.
        EmbeddedChannel channel = new EmbeddedChannel(false, false,
                new SslHandler(engine, true), new HttpResponseEncoder(), new ChunkedWriteHandler());
        channel.config().setAllocator(allocator);
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(32 * 1024, 64 * 1024));
        channel.register();

        long directMemoryBeforeWrites = allocator.metric().usedDirectMemory();
        int bytesWritten = 0;
        try {
            for (int i = 0; i < 128; ++i) {
                ByteBuf content = allocator.directBuffer(16 * 1024).writeZero(16 * 1024);
                bytesWritten += content.readableBytes();
                channel.write(new DefaultHttpContent(content));
            }

            long directMemoryAfterWrites = allocator.metric().usedDirectMemory();
            assertTrue(directMemoryAfterWrites > directMemoryBeforeWrites,
                    "unflushed writes should be retained as pooled direct buffers");
            assertTrue(bytesWritten > channel.config().getWriteBufferHighWaterMark());
            assertTrue(channel.isWritable(),
                    "bytes queued before SslHandler.flush() are not reflected in channel writability");
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
