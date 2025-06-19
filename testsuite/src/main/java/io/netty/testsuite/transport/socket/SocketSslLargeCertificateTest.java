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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslContextOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslProvider;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.TrustManagerFactory;

@EnabledIf("supportKeyManagerAndTLS13")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
public class SocketSslLargeCertificateTest {

    private CertificateBuilder base;
    private X509Bundle rootCert;
    private MultiThreadIoEventLoopGroup group;

    @BeforeAll
    public void setUp() throws Exception {
        base = new CertificateBuilder()
                .ecp256()
                .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature,
                        CertificateBuilder.KeyUsage.keyCertSign);
        rootCert = base.copy()
                .subject("cn=root.netty.io")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    }

    @AfterAll
    public void tearDown() {
        group.shutdownGracefully(100, 1000, TimeUnit.MILLISECONDS);
    }

    public static boolean supportKeyManagerAndTLS13() {
        return OpenSsl.isAvailable() &&
                OpenSsl.supportsKeyManagerFactory() &&
                SslProvider.isTlsv13Supported(SslProvider.OPENSSL);
    }

    public static Stream<Arguments> certExtensionSizes() {
        int defaultMaxHandshakeMessageLength = 16384;
        return IntStream.rangeClosed(defaultMaxHandshakeMessageLength - 768, defaultMaxHandshakeMessageLength)
                .mapToObj(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("certExtensionSizes")
    void resumptionWithLargeCertificates(int certExtensionSize) throws Exception {
        X509Bundle serverCert = base.copy()
                .subject("cn=localhost")
                .addExtendedKeyUsageServerAuth()
                .buildIssuedBy(rootCert);
        byte[] extension = new byte[certExtensionSize];
        ThreadLocalRandom.current().nextBytes(extension);
        X509Bundle clientCert = base.copy()
                .subject("cn=client")
                .addExtendedKeyUsageClientAuth()
                .addExtensionOctetString("1.2.840.113635.100.6.2.1", false, extension)
                .buildIssuedBy(rootCert);

        TrustManagerFactory tmf = rootCert.toTrustManagerFactory();
        KeyManagerFactory serverKmf = serverCert.toKeyManagerFactory();
        KeyManagerFactory clientKmf = clientCert.toKeyManagerFactory();

        SslContext serverSsl = SslContextBuilder.forServer(serverKmf)
                .sslProvider(SslProvider.OPENSSL)
                .trustManager(tmf)
                .protocols("TLSv1.3")
                .clientAuth(ClientAuth.REQUIRE)
                .option(OpenSslContextOption.MAX_CERTIFICATE_LIST_BYTES, 32768)
                .build();
        SslContext clientSsl = SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL)
                .keyManager(clientKmf)
                .trustManager(tmf)
                .protocols("TLSv1.3")
                .option(OpenSslContextOption.MAX_CERTIFICATE_LIST_BYTES, 32768)
                .serverName(new SNIHostName("localhost"))
                .endpointIdentificationAlgorithm(null)
                .build();

        final Promise<Void> completion = ImmediateEventExecutor.INSTANCE.newPromise();

        ChannelFuture bindFuture = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(serverSsl.newHandler(ch.alloc()));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof SslHandshakeCompletionEvent) {
                                    SslHandshakeCompletionEvent completionEvent = (SslHandshakeCompletionEvent) evt;
                                    if (completionEvent.isSuccess()) {
                                        ctx.writeAndFlush(Unpooled.buffer());
                                    } else {
                                        completion.tryFailure(new ExecutionException(completionEvent.cause()));
                                        ctx.close();
                                    }
                                }
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                ctx.write(msg);
                            }

                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                ctx.flush();
                            }
                        });
                    }
                })
                .bind(InetAddress.getLoopbackAddress(), 0);
        Channel serverChannel = bindFuture.sync().channel();
        InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();
        ChannelFuture connectFuture = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(clientSsl.newHandler(ch.alloc(), "localhost", serverAddress.getPort()));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof SslHandshakeCompletionEvent) {
                                    SslHandshakeCompletionEvent completionEvent = (SslHandshakeCompletionEvent) evt;
                                    if (completionEvent.isSuccess()) {
                                        ctx.writeAndFlush(Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8));
                                    } else {
                                        completion.tryFailure(new ExecutionException(completionEvent.cause()));
                                        ctx.close();
                                    }
                                }
                            }

                            private boolean receivedRead;
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                receivedRead = true;
                                ReferenceCountUtil.release(msg);
                            }

                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                ctx.fireChannelReadComplete();
                                if (receivedRead) {
                                    receivedRead = false;
                                    ctx.writeAndFlush(Unpooled.buffer()).addListener(ChannelFutureListener.CLOSE);
                                    ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
                                        @Override
                                        public void operationComplete(ChannelFuture future) throws Exception {
                                            completion.setSuccess(null);
                                        }
                                    });
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                completion.tryFailure(cause);
                                super.exceptionCaught(ctx, cause);
                            }
                        });
                    }
                })
                .connect(serverAddress);
        Channel clientChannel = connectFuture.sync().channel();
        completion.sync();
        clientChannel.close().sync();
        serverChannel.close().sync();
    }
}
