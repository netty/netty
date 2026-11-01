/*
 * Copyright 2017 The Netty Project
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
package io.netty.testsuite_jpms.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.ocsp.OcspResponse;
import io.netty.handler.ssl.ocsp.OcspServerCertificateValidator;
import io.netty.handler.ssl.ocsp.OcspValidationEvent;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.NetUtil;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OCSPTest {

    @Test
    public void testSimpleQueryTest() throws Exception {
        // Everything stays on the loopback interface so the test does not depend on external hosts.
        X509Bundle issuer = new CertificateBuilder()
                .rsa2048()
                .subject("CN=OcspIssuerCA")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        try {
            Channel ocspResponder = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(64 * 1024),
                                    new LocalOcspResponder(issuer));
                        }
                    })
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0))
                    .sync().channel();
            try {
                int ocspPort = ((InetSocketAddress) ocspResponder.localAddress()).getPort();
                // Point the server certificate at the local OCSP responder using the AIA extension.
                GeneralName ocspName = new GeneralName(GeneralName.uniformResourceIdentifier,
                        "http://" + NetUtil.LOCALHOST4.getHostAddress() + ':' + ocspPort + '/');
                AuthorityInformationAccess aia = new AuthorityInformationAccess(
                        new AccessDescription(AccessDescription.id_ad_ocsp, ocspName));
                X509Bundle serverBundle = new CertificateBuilder()
                        .rsa2048()
                        .subject("CN=localhost")
                        .addSanDnsName("localhost")
                        .addExtendedKeyUsageServerAuth()
                        .addExtensionOctetString("1.3.6.1.5.5.7.1.1", false, aia.getEncoded())
                        .buildIssuedBy(issuer);

                final SslContext serverContext = SslContextBuilder.forServer(
                        serverBundle.getKeyPair().getPrivate(), serverBundle.getCertificatePathWithRoot()).build();
                Channel tlsServer = new ServerBootstrap()
                        .group(group)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(serverContext.newHandler(ch.alloc()));
                            }
                        })
                        .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0))
                        .sync().channel();
                try {
                    checkOcspValidation(group, (InetSocketAddress) tlsServer.localAddress());
                } finally {
                    tlsServer.close().sync();
                }
            } finally {
                ocspResponder.close().sync();
            }
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    private static void checkOcspValidation(EventLoopGroup group, InetSocketAddress serverAddress)
            throws Exception {
        final AtomicReference<OcspResponse.Status> ocspStatus = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                sslContext.newHandler(ch.alloc(), "localhost", serverAddress.getPort()),
                                new OcspServerCertificateValidator(false),
                                new SimpleChannelInboundHandler<>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                        // NOOP
                                    }

                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                        if (evt instanceof OcspValidationEvent) {
                                            OcspValidationEvent event = (OcspValidationEvent) evt;
                                            ocspStatus.set(event.response().status());
                                            ctx.channel().close();
                                            latch.countDown();
                                        }
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        failure.set(cause);
                                        ctx.channel().close();
                                        latch.countDown();
                                    }
                                });
                    }
                });

        Channel channel = bootstrap.connect(serverAddress).sync().channel();

        // Wait for maximum of 1 minute for Ocsp validation to happen
        assertTrue(latch.await(1, TimeUnit.MINUTES), "No OCSP validation event received");
        assertEquals(OcspResponse.Status.VALID, ocspStatus.get(), "OCSP validation failed: " + failure.get());

        // Wait for Channel to be closed
        channel.closeFuture().sync();
    }

    /**
     * Minimal OCSP responder. It answers every request with a signed {@code good} status for the requested
     * certificates.
     */
    private static final class LocalOcspResponder extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final X509Bundle issuer;

        LocalOcspResponder(X509Bundle issuer) {
            this.issuer = issuer;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            byte[] requestBytes = new byte[request.content().readableBytes()];
            request.content().readBytes(requestBytes);
            OCSPReq ocspReq = new OCSPReq(requestBytes);

            X509CertificateHolder issuerHolder = new JcaX509CertificateHolder(issuer.getCertificate());
            BasicOCSPRespBuilder respBuilder = new BasicOCSPRespBuilder(new RespID(issuerHolder.getSubject()));
            for (Req req : ocspReq.getRequestList()) {
                respBuilder.addResponse(req.getCertID(), CertificateStatus.GOOD);
            }
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .build(issuer.getKeyPair().getPrivate());
            BasicOCSPResp basicResp = respBuilder.build(
                    signer, new X509CertificateHolder[] { issuerHolder }, new Date());
            byte[] encoded = new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basicResp).getEncoded();

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(encoded));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/ocsp-response");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, encoded.length);
            ctx.writeAndFlush(response).addListener(f -> ctx.close());
        }
    }
}
