/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.ssl;

import io.netty.internal.tcnative.CertificateCompressionAlgo;
import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalIoHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class OpenSslCertificateCompressionTest {

    private static X509Bundle cert;
    private TestCertCompressionAlgo testZLibAlgoServer;
    private TestCertCompressionAlgo testBrotliAlgoServer;
    private TestCertCompressionAlgo testZstdAlgoServer;
    private TestCertCompressionAlgo testZlibAlgoClient;
    private TestCertCompressionAlgo testBrotliAlgoClient;

    @BeforeAll
    public static void init() throws Exception {
        assumeTrue(OpenSsl.isTlsv13Supported());
        cert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
    }

    @BeforeEach
    public void refreshAlgos() {
        testZLibAlgoServer = new TestCertCompressionAlgo(CertificateCompressionAlgo.TLS_EXT_CERT_COMPRESSION_ZLIB);
        testBrotliAlgoServer = new TestCertCompressionAlgo(CertificateCompressionAlgo.TLS_EXT_CERT_COMPRESSION_BROTLI);
        testZstdAlgoServer = new TestCertCompressionAlgo(CertificateCompressionAlgo.TLS_EXT_CERT_COMPRESSION_ZSTD);
        testZlibAlgoClient = new TestCertCompressionAlgo(CertificateCompressionAlgo.TLS_EXT_CERT_COMPRESSION_ZLIB);
        testBrotliAlgoClient = new TestCertCompressionAlgo(CertificateCompressionAlgo.TLS_EXT_CERT_COMPRESSION_BROTLI);
    }

    @Test
    public void testSimple() throws Throwable {
        assumeTrue(OpenSsl.isBoringSSL());
        final SslContext clientSslContext = buildClientContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testBrotliAlgoClient,
                                OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .build()
        );
        final SslContext serverSslContext = buildServerContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testBrotliAlgoServer, OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                        .build()
        );

        runCertCompressionTest(clientSslContext, serverSslContext);

        assertCompress(testBrotliAlgoServer);
        assertDecompress(testBrotliAlgoClient);
    }

    @Test
    public void testServerPriority() throws Throwable {
        assumeTrue(OpenSsl.isBoringSSL());
        final SslContext clientSslContext = buildClientContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testBrotliAlgoClient,
                                OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .addAlgorithm(testZlibAlgoClient, OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .build()
        );
        final SslContext serverSslContext = buildServerContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testZLibAlgoServer, OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                        .addAlgorithm(testBrotliAlgoServer, OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                        .build()
        );

        runCertCompressionTest(clientSslContext, serverSslContext);

        assertCompress(testZLibAlgoServer);
        assertDecompress(testZlibAlgoClient);
        assertNone(testBrotliAlgoClient, testBrotliAlgoServer);
    }

    @Test
    public void testServerPriorityReverse() throws Throwable {
        assumeTrue(OpenSsl.isBoringSSL());
        final SslContext clientSslContext = buildClientContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testBrotliAlgoClient,
                                OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .addAlgorithm(testZlibAlgoClient, OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .build()
        );
        final SslContext serverSslContext = buildServerContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testBrotliAlgoServer,
                                OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                        .addAlgorithm(testZLibAlgoServer, OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                        .build()
        );

        runCertCompressionTest(clientSslContext, serverSslContext);

        assertCompress(testBrotliAlgoServer);
        assertDecompress(testBrotliAlgoClient);
        assertNone(testZLibAlgoServer, testZlibAlgoClient);
    }

    @Test
    public void testFailedNegotiation() throws Throwable {
        assumeTrue(OpenSsl.isBoringSSL());
        final SslContext clientSslContext = buildClientContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testBrotliAlgoClient,
                                OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .addAlgorithm(testZlibAlgoClient, OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .build()
        );
        final SslContext serverSslContext = buildServerContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testZstdAlgoServer, OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                        .build()
        );

        runCertCompressionTest(clientSslContext, serverSslContext);

        assertNone(testBrotliAlgoClient, testZlibAlgoClient, testZstdAlgoServer);
    }

    @Test
    public void testAlgoFailure() throws Throwable {
        assumeTrue(OpenSsl.isBoringSSL());
        TestCertCompressionAlgo badZlibAlgoClient =
                new TestCertCompressionAlgo(CertificateCompressionAlgo.TLS_EXT_CERT_COMPRESSION_ZLIB) {
            @Override
            public byte[] decompress(SSLEngine engine, int uncompressed_len, byte[] input) {
                return input;
            }
        };
        final SslContext clientSslContext = buildClientContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(badZlibAlgoClient, OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .build()
        );
        final SslContext serverSslContext = buildServerContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testZLibAlgoServer, OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                        .build()
        );

        CompletionException e = assertThrows(CompletionException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                runCertCompressionTest(clientSslContext, serverSslContext);
            }
        });
        assertThat(e).hasCauseInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void testAlgoException() throws Throwable {
        assumeTrue(OpenSsl.isBoringSSL());
        TestCertCompressionAlgo badZlibAlgoClient =
                new TestCertCompressionAlgo(CertificateCompressionAlgo.TLS_EXT_CERT_COMPRESSION_ZLIB) {
                    @Override
                    public byte[] decompress(SSLEngine engine, int uncompressed_len, byte[] input) {
                        throw new RuntimeException("broken");
                    }
                };
        final SslContext clientSslContext = buildClientContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(badZlibAlgoClient, OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                        .build()
        );
        final SslContext serverSslContext = buildServerContext(
                OpenSslCertificateCompressionConfig.newBuilder()
                        .addAlgorithm(testZLibAlgoServer, OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                        .build()
        );

        CompletionException e = assertThrows(CompletionException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                runCertCompressionTest(clientSslContext, serverSslContext);
            }
        });
        assertThat(e).hasCauseInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void testTlsLessThan13() throws Throwable {
        assumeTrue(OpenSsl.isBoringSSL());
        final SslContext clientSslContext = SslContextBuilder.forClient()
             .sslProvider(SslProvider.OPENSSL)
             .protocols(SslProtocols.TLS_v1_2)
             .trustManager(InsecureTrustManagerFactory.INSTANCE)
             .option(OpenSslContextOption.CERTIFICATE_COMPRESSION_ALGORITHMS,
                     OpenSslCertificateCompressionConfig.newBuilder()
                             .addAlgorithm(testBrotliAlgoClient,
                                     OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                             .build())
             .build();
        final SslContext serverSslContext = SslContextBuilder.forServer(cert.getKeyPair().getPrivate(),
                        cert.getCertificatePath())
               .sslProvider(SslProvider.OPENSSL)
               .protocols(SslProtocols.TLS_v1_2)
               .option(OpenSslContextOption.CERTIFICATE_COMPRESSION_ALGORITHMS,
                       OpenSslCertificateCompressionConfig.newBuilder()
                               .addAlgorithm(testBrotliAlgoServer,
                                       OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                               .build())
               .build();

        runCertCompressionTest(clientSslContext, serverSslContext);

        // BoringSSL returns success when calling SSL_CTX_add_cert_compression_alg
        // but only applies compression for TLSv1.3
        assertNone(testBrotliAlgoClient, testBrotliAlgoServer);
    }

    @Test
    public void testDuplicateAdd() throws Throwable {
        // Fails with "Failed trying to add certificate compression algorithm"
        assumeTrue(OpenSsl.isBoringSSL());
        assertThrows(Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                buildClientContext(
                        OpenSslCertificateCompressionConfig.newBuilder()
                                .addAlgorithm(testBrotliAlgoClient,
                                        OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                                .addAlgorithm(testBrotliAlgoClient,
                                        OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                                .build()
                );
            }
        });

        assertThrows(Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                buildServerContext(
                        OpenSslCertificateCompressionConfig.newBuilder()
                                .addAlgorithm(testBrotliAlgoServer,
                                        OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                                .addAlgorithm(testBrotliAlgoServer,
                                        OpenSslCertificateCompressionConfig.AlgorithmMode.Both).build()
                );
            }
        });
    }

    @Test
    public void testNotBoringAdd() throws Throwable {
        // Fails with "TLS Cert Compression only supported by BoringSSL"
        assumeTrue(!OpenSsl.isBoringSSL());
        assertThrows(Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                buildClientContext(
                        OpenSslCertificateCompressionConfig.newBuilder()
                                .addAlgorithm(testBrotliAlgoClient,
                                        OpenSslCertificateCompressionConfig.AlgorithmMode.Decompress)
                                .build()
                );
            }
        });

        assertThrows(Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                buildServerContext(
                        OpenSslCertificateCompressionConfig.newBuilder()
                                .addAlgorithm(testBrotliAlgoServer,
                                        OpenSslCertificateCompressionConfig.AlgorithmMode.Compress)
                                .build()
                );
            }
        });
    }

    public void runCertCompressionTest(SslContext clientSslContext, SslContext serverSslContext) throws Throwable {
        EventLoopGroup group = new MultithreadEventLoopGroup(LocalIoHandler.newFactory());
        Promise<Object> clientPromise = group.next().newPromise();
        Promise<Object> serverPromise = group.next().newPromise();
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(group).channel(LocalServerChannel.class)
                    .childHandler(new CertCompressionTestChannelInitializer(serverPromise, serverSslContext));
            Channel serverChannel = sb.bind(new LocalAddress(getClass())).asStage().get();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(LocalChannel.class)
                    .handler(new CertCompressionTestChannelInitializer(clientPromise, clientSslContext));

            Channel clientChannel = bootstrap.connect(serverChannel.localAddress()).asStage().get();

            assertTrue(clientPromise.asFuture().asStage().await(5L, TimeUnit.SECONDS), "client timeout");
            assertTrue(serverPromise.asFuture().asStage().await(5L, TimeUnit.SECONDS), "server timeout");
            clientPromise.asFuture().asStage().sync();
            serverPromise.asFuture().asStage().sync();
            clientChannel.close().asStage().sync();
            serverChannel.close().asStage().sync();
        } finally  {
            group.shutdownGracefully();
        }
    }

    private SslContext buildServerContext(OpenSslCertificateCompressionConfig compressionConfig) throws Exception {
        return SslContextBuilder.forServer(cert.getKeyPair().getPrivate(), cert.getCertificatePath())
                .sslProvider(SslProvider.OPENSSL)
                .protocols(SslProtocols.TLS_v1_3)
            .option(OpenSslContextOption.CERTIFICATE_COMPRESSION_ALGORITHMS,
                    compressionConfig)
                .build();
    }

    private SslContext buildClientContext(OpenSslCertificateCompressionConfig compressionConfig) throws SSLException {
        return SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL)
                .protocols(SslProtocols.TLS_v1_3)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .option(OpenSslContextOption.CERTIFICATE_COMPRESSION_ALGORITHMS,
                    compressionConfig)
                .build();
    }

    private void assertCompress(TestCertCompressionAlgo algo) {
        assertTrue(algo.compressCalled && !algo.decompressCalled);
    }

    private void assertDecompress(TestCertCompressionAlgo algo) {
        assertTrue(!algo.compressCalled && algo.decompressCalled);
    }

    private void assertNone(TestCertCompressionAlgo... algos) {
        for (TestCertCompressionAlgo algo : algos) {
            assertTrue(!algo.compressCalled && !algo.decompressCalled);
        }
    }

    private static class CertCompressionTestChannelInitializer extends ChannelInitializer<Channel> {

        private final Promise<Object> channelPromise;
        private final SslContext sslContext;

        CertCompressionTestChannelInitializer(Promise<Object> channelPromise, SslContext sslContext) {
            this.channelPromise = channelPromise;
            this.sslContext = sslContext;
        }

        @Override
        protected void initChannel(Channel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(sslContext.newHandler(ch.bufferAllocator()));
            pipeline.addLast(new SimpleChannelInboundHandler<>() {

                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msg) {
                    // Do nothing
                }

                @Override
                public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
                    if (evt instanceof SslHandshakeCompletionEvent) {
                        if (((SslHandshakeCompletionEvent) evt).isSuccess()) {
                            channelPromise.trySuccess(evt);
                        } else {
                            channelPromise.tryFailure(((SslHandshakeCompletionEvent) evt).cause());
                        }
                    }
                    ctx.fireChannelInboundEvent(evt);
                }
            });
        }
    }

    private static class TestCertCompressionAlgo implements OpenSslCertificateCompressionAlgorithm {

        private static final int BASE_PADDING_SIZE = 10;
        public boolean compressCalled;
        public boolean decompressCalled;
        private final int algorithmId;

        TestCertCompressionAlgo(int algorithmId) {
            this.algorithmId = algorithmId;
        }

        @Override
        public byte[] compress(SSLEngine engine, byte[] input) throws Exception {
            compressCalled = true;
            byte[] output = new byte[input.length + BASE_PADDING_SIZE + algorithmId];
            System.arraycopy(input, 0, output, BASE_PADDING_SIZE + algorithmId, input.length);
            return output;
        }

        @Override
        public byte[] decompress(SSLEngine engine, int uncompressed_len, byte[] input) {
            decompressCalled = true;
            byte[] output = new byte[input.length - (BASE_PADDING_SIZE + algorithmId)];
            System.arraycopy(input, BASE_PADDING_SIZE + algorithmId, output, 0, output.length);
            return output;
        }

        @Override
        public int algorithmId() {
            return algorithmId;
        }
    }
}
