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
package io.netty.handler.ssl;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SslClientHelloHandlerTest {

    // ClientHello carrying SNI hostname "chat4.leancloud.cn", borrowed from SniHandlerTest.
    private static final String TLS_CLIENT_HELLO_HEX_PART1 = "16030100";
    private static final String TLS_CLIENT_HELLO_HEX_PART2 =
            "c6010000c20303bb0855d66532c05a0ef784f7c384feeafa68b3" +
            "b655ac7288650d5eed4aa3fb52000038c02cc030009fcca9cca8ccaac02b" +
            "c02f009ec024c028006bc023c0270067c00ac0140039c009c0130033009d" +
            "009c003d003c0035002f00ff010000610000001700150000124348415434" +
            "2e4c45414e434c4f55442e434e000b000403000102000a000a0008001d00" +
            "170019001800230000000d0020001e060106020603050105020503040104" +
            "0204030301030203030201020202030016000000170000";

    @Test
    public void testSyncLookupCallbackExceptionFiredOnPipeline() {
        final AtomicBoolean nullRetryOccurred = new AtomicBoolean();

        AbstractSniHandler<Object> handler = new AbstractSniHandler<Object>() {
            @Override
            protected Future<Object> lookup(ChannelHandlerContext ctx, String hostname) {
                if (hostname == null) {
                    nullRetryOccurred.set(true);
                }
                Promise<Object> promise = ImmediateEventExecutor.INSTANCE.newPromise();
                promise.setSuccess(new Object());
                return promise;
            }

            @Override
            protected void onLookupComplete(ChannelHandlerContext ctx, String hostname,
                                            Future<Object> future) {
                throw new RuntimeException("simulated user callback failure");
            }
        };

        final AtomicReference<Throwable> exceptionRef = new AtomicReference<Throwable>();
        EmbeddedChannel ch = new EmbeddedChannel(handler, new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                exceptionRef.compareAndSet(null, cause);
            }
        });

        try {
            ch.writeInbound(Unpooled.wrappedBuffer(StringUtil.decodeHexDump(TLS_CLIENT_HELLO_HEX_PART1)));
            ch.writeInbound(Unpooled.wrappedBuffer(StringUtil.decodeHexDump(TLS_CLIENT_HELLO_HEX_PART2)));
        } finally {
            ch.finishAndReleaseAll();
        }

        Throwable cause = exceptionRef.get();
        assertNotNull(cause);
        assertInstanceOf(DecoderException.class, cause);
        assertFalse(nullRetryOccurred.get(), "Expected no select(ctx, null) retry");
    }
}
