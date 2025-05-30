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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.ssl.ClientAuth;
import io.netty5.handler.ssl.OpenSsl;
import io.netty5.handler.ssl.OpenSslContextOption;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslHandshakeCompletionEvent;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
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
    private MultithreadEventLoopGroup group;

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
        group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
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

        Future<Channel> bindFuture = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(serverSsl.newHandler(ch.bufferAllocator()));
                        ch.pipeline().addLast(new ChannelHandlerAdapter() {
                            @Override
                            public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof SslHandshakeCompletionEvent completionEvent) {
                                    if (completionEvent.isSuccess()) {
                                        ctx.writeAndFlush(ctx.bufferAllocator().allocate(0));
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
        Channel serverChannel = bindFuture.asStage().get();
        InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();
        Future<Channel> connectFuture = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(clientSsl.newHandler(
                                ch.bufferAllocator(), "localhost", serverAddress.getPort()));
                        ch.pipeline().addLast(new ChannelHandlerAdapter() {
                            @Override
                            public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof SslHandshakeCompletionEvent completionEvent) {
                                    if (completionEvent.isSuccess()) {
                                        ctx.writeAndFlush(
                                                ctx.bufferAllocator().copyOf("hello", StandardCharsets.UTF_8));
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
                                Resource.dispose(msg);
                            }

                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                ctx.fireChannelReadComplete();
                                if (receivedRead) {
                                    receivedRead = false;
                                    ctx.writeAndFlush(ctx.bufferAllocator().allocate(0))
                                            .addListener(ctx, ChannelFutureListeners.CLOSE);
                                    ctx.channel().closeFuture().addListener(new FutureListener<Void>() {
                                        @Override
                                        public void operationComplete(Future<? extends Void> future) throws Exception {
                                            completion.setSuccess(null);
                                        }
                                    });
                                }
                            }

                            @Override
                            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                    throws Exception {
                                completion.tryFailure(cause);
                                super.channelExceptionCaught(ctx, cause);
                            }
                        });
                    }
                })
                .connect(serverAddress);
        Channel clientChannel = connectFuture.asStage().get();
        completion.asFuture().asStage().sync();
        clientChannel.close().asStage().sync();
        serverChannel.close().asStage().sync();
    }
}
