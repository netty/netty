/*
 * Copyright 2025 The Netty Project
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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.ssl.MockAlternativeKeyProvider.wrapPrivateKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration tests for alternative key providers using actual socket connections.
 */
@EnabledIf("io.netty.handler.ssl.OpenSsl#isBoringSSL")
public class AlternativeKeyEndToEndTest {

    private static MockAlternativeKeyProvider mockProvider;
    private static EventLoopGroup GROUP;
    private static SelfSignedCertificate SERVER_SSC;
    private static SelfSignedCertificate CLIENT_SSC;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(OpenSsl.isAvailable(), "OpenSSL is not available");

        mockProvider = new MockAlternativeKeyProvider();
        Security.addProvider(mockProvider);
        GROUP = new NioEventLoopGroup();
        SERVER_SSC = new SelfSignedCertificate();
        CLIENT_SSC = new SelfSignedCertificate();
    }

    @AfterAll
    static void tearDown() {
        if (GROUP != null) {
            GROUP.shutdownGracefully();
        }
        if (mockProvider != null) {
            Security.removeProvider(mockProvider.getName());
        }
        if (SERVER_SSC != null) {
            SERVER_SSC.delete();
        }
        if (CLIENT_SSC != null) {
            CLIENT_SSC.delete();
        }
    }

    /**
     * Test scenarios for client/server alternative key combinations
     */
    public enum TestScenario {
        SERVER_ALTERNATIVE_CLIENT_STANDARD("Server Alternative + Client Standard", true, false),
        SERVER_STANDARD_CLIENT_ALTERNATIVE("Server Standard + Client Alternative", false, true),
        BOTH_ALTERNATIVE("Both Alternative", true, true);

        final String description;
        final boolean serverUsesAlternativeKey;
        final boolean clientUsesAlternativeKey;

        TestScenario(String description, boolean serverUsesAlternativeKey, boolean clientUsesAlternativeKey) {
            this.description = description;
            this.serverUsesAlternativeKey = serverUsesAlternativeKey;
            this.clientUsesAlternativeKey = clientUsesAlternativeKey;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{index}: scenario = {0}")
    @EnumSource(TestScenario.class)
    @Timeout(30)
    void testJavaSecurityProviderSignatures(TestScenario scenario) throws Exception {
        // Reset signature operation counter for test isolation
        MockAlternativeKeyProvider.resetSignatureOperationCount();

        // Create certificate and keys for both server and client

        // Prepare server key (alternative or standard based on scenario)
        PrivateKey serverPrivateKey = scenario.serverUsesAlternativeKey
            ? wrapPrivateKey(SERVER_SSC.key())
            : SERVER_SSC.key();
        X509Certificate serverCertificate = SERVER_SSC.cert();

        // Prepare client key (alternative or standard based on scenario)
        PrivateKey clientPrivateKey = scenario.clientUsesAlternativeKey
            ? wrapPrivateKey(CLIENT_SSC.key())
            : CLIENT_SSC.key();
        X509Certificate clientCertificate = CLIENT_SSC.cert();

        // Verify alternative keys behave correctly
        if (scenario.serverUsesAlternativeKey) {
            assertNull(serverPrivateKey.getEncoded(),
                "Server alternative key should return null from getEncoded()");
        }
        if (scenario.clientUsesAlternativeKey) {
            assertNull(clientPrivateKey.getEncoded(),
                "Client alternative key should return null from getEncoded()");
        }

        // Set up server context
        SslContext serverSslContext = SslContextBuilder.forServer(serverPrivateKey, serverCertificate)
                .sslProvider(SslProvider.OPENSSL)
                .trustManager(clientCertificate)
                .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true)
                .clientAuth(ClientAuth.REQUIRE)
                .build();

        // Set up client context
        SslContext clientSslContext = SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL)
                .trustManager(serverCertificate)
                .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true)
                .keyManager(clientPrivateKey, clientCertificate)
                .build();

        // Set up server
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(GROUP)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        SslHandler serverSslHandler = serverSslContext.newHandler(ch.alloc());
                        pipeline.addLast(serverSslHandler);
                        pipeline.addLast(new ServerHandler());
                    }
                });

        ChannelFuture serverChannelFuture = serverBootstrap.bind(0).sync();
        Channel serverChannel = serverChannelFuture.channel();
        int serverPort = ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
        try {
            // Verify the message was received correctly
            assertEquals("R", runClient(serverPort, clientSslContext).get(10, TimeUnit.SECONDS),
                       "Received message should match sent message");

            // Verify that alternative key provider was actually used for signature operations
            int expectedSignatureOperations = 0;
            if (scenario.serverUsesAlternativeKey) {
                expectedSignatureOperations++;
            }
            if (scenario.clientUsesAlternativeKey) {
                expectedSignatureOperations++;
            }

            int actualSignatureOperations = MockAlternativeKeyProvider.getSignatureOperationCount();
            assertEquals(expectedSignatureOperations, actualSignatureOperations,
                "Unexpected number of signature operations");
        } finally {
            serverChannel.close().sync();
        }
    }

    @Test
    @Timeout(30)
    void testEndToEndTlsConnectionWithMultipleHandshakes() throws Exception {
        // Create certificate and wrap the private key
        PrivateKey wrappedPrivateKey = wrapPrivateKey(SERVER_SSC.key());
        X509Certificate certificate = SERVER_SSC.cert();

        // Set up contexts
        SslContext serverSslContext = SslContextBuilder.forServer(wrappedPrivateKey, certificate)
                .sslProvider(SslProvider.OPENSSL)
                .option(OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES, true)
                .build();

        SslContext clientSslContext = SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL)
                .trustManager(certificate)
                .build();

        // Test multiple connections to ensure provider caching works correctly
        int numberOfConnections = 3;
        List<Future<String>> results = new ArrayList<>();

        // Set up server
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(GROUP)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        SslHandler sslHandler = serverSslContext.newHandler(ch.alloc());
                        pipeline.addLast(sslHandler);
                        pipeline.addLast(new ServerHandler());
                    }
                });

        ChannelFuture serverChannelFuture = serverBootstrap.bind(0).sync();
        Channel serverChannel = serverChannelFuture.channel();
        int serverPort = ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();

        try {
            // Create multiple client connections
            for (int i = 0; i < numberOfConnections; i++) {
                results.add(runClient(serverPort, clientSslContext));
            }

            // Wait for all connections to complete
            for (Future<String> result : results) {
                assertEquals("R", result.get(20, TimeUnit.SECONDS),
                          "All connections should complete within timeout");
            }
        } finally {
            serverChannel.close().sync();
        }
    }

    private Future<String> runClient(int serverPort, SslContext clientSslContext) {
        Promise<String> resultPromise = GROUP.next().newPromise();
        Bootstrap clientBootstrap = new Bootstrap()
                .group(GROUP)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        SslHandler sslHandler = clientSslContext.newHandler(ch.alloc(),
                                "localhost", serverPort);
                        pipeline.addLast(sslHandler);
                        pipeline.addLast(new ClientHandler(resultPromise));
                    }
                });

        ChannelFuture clientChannel = clientBootstrap.connect("localhost", serverPort);
        return resultPromise;
    }

    private static final class ClientHandler extends ChannelInboundHandlerAdapter {
        Promise<String> resultPromise;

        ClientHandler(Promise<String> resultPromise) {
            this.resultPromise = resultPromise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // Send message once connection is established
            ByteBuf message = Unpooled.copiedBuffer("R", CharsetUtil.UTF_8);
            ctx.writeAndFlush(message);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // Close connection after receiving echo
            if (msg instanceof ByteBuf) {
                resultPromise.setSuccess(((ByteBuf) msg).toString(CharsetUtil.UTF_8));
                ctx.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            resultPromise.tryFailure(cause);
            ctx.close();
        }
    }

    private static final class ServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // Echo the message back and close
            ctx.writeAndFlush(msg).addListener(future -> {
                ctx.close();
            });
        }
    }
}
