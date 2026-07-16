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
package io.netty.handler.codec.quic;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.KeyMaterialExporter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuicSslEngineExportKeyingMaterialTest extends AbstractQuicTest {

    private static final String LABEL = "EXPORTER-netty-quic-test";

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(30)
    public void testExportKeyingMaterialMatchesOnBothPeers(Executor executor) throws Throwable {
        CountDownLatch serverActiveLatch = new CountDownLatch(1);
        AtomicReference<QuicChannel> serverChannelRef = new AtomicReference<>();

        Channel server = QuicTestUtils.newServer(executor,
                new CaptureChannelHandler(serverChannelRef, serverActiveLatch),
                new ChannelInboundHandlerAdapter());
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(executor);
        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect()
                    .get();

            assertTrue(serverActiveLatch.await(30, TimeUnit.SECONDS));

            QuicChannel serverChannel = serverChannelRef.get();
            assertNotNull(serverChannel);
            KeyMaterialExporter clientExporter =
                    assertInstanceOf(KeyMaterialExporter.class, quicChannel.sslEngine());
            KeyMaterialExporter serverExporter =
                    assertInstanceOf(KeyMaterialExporter.class, serverChannel.sslEngine());

            // The SSL object is driven by the event loop, so calls from other threads must be rejected.
            assertThrows(IllegalStateException.class,
                    () -> clientExporter.exportKeyingMaterial(LABEL, null, new byte[32]));
            assertThrows(IllegalStateException.class,
                    () -> serverExporter.exportKeyingMaterial(LABEL, null, new byte[32]));

            // Same label + context (null) must produce identical keying material on both peers.
            byte[] clientMaterial = export(quicChannel, clientExporter, LABEL, null, 32);
            byte[] serverMaterial = export(serverChannel, serverExporter, LABEL, null, 32);
            assertArrayEquals(clientMaterial, serverMaterial);

            // A context value is also mixed in identically on both peers.
            byte[] context = "some-context".getBytes(StandardCharsets.US_ASCII);
            byte[] clientWithContext = export(quicChannel, clientExporter, LABEL, context, 32);
            byte[] serverWithContext = export(serverChannel, serverExporter, LABEL, context, 32);
            assertArrayEquals(clientWithContext, serverWithContext);

            // As QUIC always uses TLS 1.3, an empty context produces the same keying material as no context
            // (in TLS 1.3 the exporter does not distinguish between the two, unlike TLS 1.2 / RFC 5705).
            byte[] clientWithEmptyContext = export(quicChannel, clientExporter, LABEL, new byte[0], 32);
            assertArrayEquals(clientMaterial, clientWithEmptyContext);

            // Different context => different keying material.
            assertFalse(Arrays.equals(clientMaterial, clientWithContext));

            // Different label => different keying material.
            byte[] clientOtherLabel = export(quicChannel, clientExporter, "EXPORTER-netty-quic-other", null, 32);
            assertFalse(Arrays.equals(clientMaterial, clientOtherLabel));

            // An empty output array is allowed.
            assertEquals(0, export(quicChannel, clientExporter, LABEL, null, 0).length);

            quicChannel.close().sync();
            quicChannel.closeFuture().sync();
            serverChannel.close().sync();
        } finally {
            server.close().sync();
            channel.close().sync();
            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(30)
    public void testExportKeyingMaterialBeforeHandshakeThrows(Executor executor) throws Throwable {
        QuicSslContext context = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(QuicTestUtils.PROTOS)
                .build();
        try {
            KeyMaterialExporter exporter = assertInstanceOf(KeyMaterialExporter.class,
                    context.newEngine(UnpooledByteBufAllocator.DEFAULT));
            assertThrows(SSLException.class, () -> exporter.exportKeyingMaterial(LABEL, null, new byte[32]));
        } finally {
            shutdown(executor);
        }
    }

    private static byte[] export(Channel channel, KeyMaterialExporter exporter, String label,
                                 byte @Nullable [] context, int length) throws Exception {
        return channel.eventLoop().submit(() -> {
            byte[] out = new byte[length];
            exporter.exportKeyingMaterial(label, context, out);
            return out;
        }).get();
    }

    private static final class CaptureChannelHandler extends ChannelInboundHandlerAdapter {
        private final AtomicReference<QuicChannel> channelRef;
        private final CountDownLatch activeLatch;

        CaptureChannelHandler(AtomicReference<QuicChannel> channelRef, CountDownLatch activeLatch) {
            this.channelRef = channelRef;
            this.activeLatch = activeLatch;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channelRef.set((QuicChannel) ctx.channel());
            activeLatch.countDown();
            ctx.fireChannelActive();
        }
    }
}
