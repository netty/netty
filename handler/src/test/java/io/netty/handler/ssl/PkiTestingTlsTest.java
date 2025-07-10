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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SNIHostName;
import java.util.ArrayList;
import java.util.List;

public class PkiTestingTlsTest {
    static List<Arguments> algorithms() {
        List<Arguments> args = new ArrayList<>();
        for (boolean openssl : new  boolean[]{true, false}) {
            if (openssl) {
                if (!OpenSsl.isAvailable() || !OpenSsl.isTlsv13Supported()) {
                    continue;
                }
            } else {
                if (!SslProvider.isTlsv13Supported(SslProvider.JDK)) {
                    continue;
                }
            }

            List<CertificateBuilder.Algorithm> algs =  new ArrayList<>();
            algs.add(CertificateBuilder.Algorithm.rsa2048);
            algs.add(CertificateBuilder.Algorithm.ecp256);
            if (PlatformDependent.javaVersion() >= 15) {
                algs.add(CertificateBuilder.Algorithm.ed25519);
            }
            if (PlatformDependent.javaVersion() >= 24 && openssl && OpenSsl.versionString().startsWith("BoringSSL")) {
                algs.add(CertificateBuilder.Algorithm.mlDsa44);
                //algs.add(CertificateBuilder.Algorithm.mlKem512); needs a cert chain
            }

            for (CertificateBuilder.Algorithm alg : algs) {
                args.add(Arguments.of(openssl, alg));
            }
        }
        return args;
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    public void test(boolean openssl, CertificateBuilder.Algorithm algorithm) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            X509Bundle cert = new CertificateBuilder()
                    .algorithm(algorithm)
                    .setIsCertificateAuthority(true)
                    .subject("CN=localhost")
                    .buildSelfSigned();
            ServerSocketChannel ssc = (ServerSocketChannel) new ServerBootstrap()
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        final SslContext context = SslContextBuilder.forServer(cert.getKeyPair().getPrivate(), cert.getCertificatePath())
                                .sslProvider(openssl ? SslProvider.OPENSSL : SslProvider.JDK)
                                .build();

                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(context.newHandler(ch.alloc()));
                        }
                    })
                    .group(group)
                    .bind(8443).sync().channel();

            Promise<SslHandshakeCompletionEvent> promise = group.next().newPromise();

            new Bootstrap()
                    .channel(NioSocketChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<Channel>() {
                        final SslContext context = SslContextBuilder.forClient()
                                .trustManager(cert.toTrustManagerFactory())
                                .sslProvider(openssl ? SslProvider.OPENSSL : SslProvider.JDK)
                                .serverName(new SNIHostName("localhost"))
                                .protocols("TLSv1.3")
                                .build();

                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(context.newHandler(ch.alloc()))
                                    .addLast(new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                            if (evt instanceof SslHandshakeCompletionEvent) {
                                                SslHandshakeCompletionEvent shce = (SslHandshakeCompletionEvent) evt;
                                                if (shce.isSuccess()) {
                                                    promise.setSuccess(shce);
                                                } else {
                                                    promise.setFailure(shce.cause());
                                                }
                                                return;
                                            }
                                            super.userEventTriggered(ctx, evt);
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                            if (!promise.tryFailure(cause)) {
                                                ctx.fireExceptionCaught(cause);
                                            }
                                        }
                                    });
                        }
                    })
                    .connect("localhost", ssc.localAddress().getPort()).sync();

            promise.sync();
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }
}
