/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.ssl.ocsp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.netty.handler.ssl.ocsp.OcspServerCertificateValidator.createDefaultResolver;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcspServerCertificateValidatorTest extends AbstractOcspTest {

    private static final String OID_AUTHORITY_INFORMATION_ACCESS = "1.3.6.1.5.5.7.1.1";

    /**
     * Well within the 15 minutes of clock skew that the validator tolerates by default.
     */
    private static final long FRESH_THIS_UPDATE_AGE_MILLIS = TimeUnit.MINUTES.toMillis(1);

    /**
     * Far outside the 15 minutes of clock skew that the validator tolerates by default.
     */
    private static final long STALE_THIS_UPDATE_AGE_MILLIS = TimeUnit.HOURS.toMillis(2);

    /**
     * A response age that is unremarkable for a real CA, but far outside the clock skew tolerance.
     */
    private static final long OLD_THIS_UPDATE_AGE_MILLIS = TimeUnit.DAYS.toMillis(7);

    private static final long NEXT_UPDATE_AHEAD_MILLIS = TimeUnit.HOURS.toMillis(1);

    @Test
    void connectUsingHttpAndValidateCertificateUsingOcspTest() throws Exception {
        final AtomicBoolean ocspStatus = new AtomicBoolean();
        EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(sslContext.newHandler(ch.alloc(), "apple.com", 443));
                            pipeline.addLast(new OcspServerCertificateValidator(false, createDefaultTransport()));
                            pipeline.addLast(new SimpleChannelInboundHandler<Object>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    // NOOP
                                }

                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof OcspValidationEvent) {
                                        OcspValidationEvent event = (OcspValidationEvent) evt;

                                        ocspStatus.set(event.response().status() == OcspResponse.Status.VALID);
                                        ctx.channel().close();
                                        latch.countDown();
                                    }
                                }
                            });
                        }
                    });

            ChannelFuture channelFuture = bootstrap.connect("apple.com", 443);
            channelFuture.sync();

            // Wait for maximum of 1 minute for Ocsp validation to happen
            latch.await(1, TimeUnit.MINUTES);
            assertTrue(ocspStatus.get());

            // Wait for Channel to be closed
            channelFuture.channel().closeFuture().sync();
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }

    /**
     * A revoked certificate is revoked regardless of its nextUpdate field.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void revokedCertificateIsReportedWhenResponseHasNextUpdate() throws Exception {
        ResponseSpec responseSpec = new ResponseSpec(
            true /*revoked*/, FRESH_THIS_UPDATE_AGE_MILLIS, NEXT_UPDATE_AHEAD_MILLIS);
        validateCertificate(responseSpec, outcome -> {
            assertEquals(1, outcome.ocspRequests, "OCSP responder must be queried");
            assertTrue(outcome.verdictReceived, "Must deliver verdict to the application");
            assertEquals(OcspResponse.Status.REVOKED, outcome.status);
            assertTrue(outcome.channelClosedFuture.awaitUninterruptibly(10, TimeUnit.SECONDS),
                "Channel must be closed for a REVOKED certificate");
            assertInstanceOf(OCSPException.class, outcome.failure);
        });
    }

    /**
     * The nextUpdate field is OPTIONAL (RFC 6960 section 4.2.1).
     * When it is omitted the validator must still tell the application what happened, either by firing an
     * {@link OcspValidationEvent} or by firing an exception.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void revokedCertificateIsReportedWhenResponseHasNoNextUpdate() throws Exception {
        validateCertificate(new ResponseSpec(true, FRESH_THIS_UPDATE_AGE_MILLIS, null), outcome -> {
            assertEquals(1, outcome.ocspRequests, "OCSP responder must be queried");
            assertTrue(outcome.verdictReceived,
                "OCSP validation result was silently dropped: neither an OcspValidationEvent nor an exception " +
                    "was delivered for an OCSP response without 'nextUpdate'");
            // Whatever the verdict is, the revoked certificate must never be reported as valid.
            assertNotEquals(OcspResponse.Status.VALID, outcome.status);
        });
    }

    /**
     * RFC 6960 section 4.2.2.1: an absent {@code nextUpdate} means newer information is available all the time,
     * so such a response is current rather than unusable.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void recentResponseWithoutNextUpdateIsAccepted() throws Exception {
        validateCertificate(new ResponseSpec(false, FRESH_THIS_UPDATE_AGE_MILLIS, null), outcome -> {
            assertEquals(1, outcome.ocspRequests, "OCSP responder must be queried");
            assertTrue(outcome.verdictReceived, "Must deliver verdict to the application");
            assertEquals(OcspResponse.Status.VALID, outcome.status);
            assertNull(outcome.failure);
        });
    }

    /**
     * RFC 6960 section 3.2 (5): {@code thisUpdate} must be "sufficiently recent". With {@code nextUpdate} absent
     * it is the only bound on the age of the response, and so the only thing preventing replay of an old but
     * genuinely signed response.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void staleResponseWithoutNextUpdateIsRejected() throws Exception {
        validateCertificate(new ResponseSpec(false, STALE_THIS_UPDATE_AGE_MILLIS, null), outcome -> {
            assertEquals(1, outcome.ocspRequests, "OCSP responder must be queried");
            assertNotEquals(OcspResponse.Status.VALID, outcome.status,
                "Must not accept an out-of-date OCSP response without 'nextUpdate' as valid");
            assertNotNull(outcome.failure, "No exception was delivered for an out-of-date OCSP response");
            assertTrue(outcome.channelClosedFuture.awaitUninterruptibly(10, TimeUnit.SECONDS),
                "Channel was not closed for an out-of-date OCSP response");
        });
    }

    /**
     * A present {@code nextUpdate} extends the validity interval past {@code thisUpdate}, which is what keeps the
     * age bound from rejecting the long-lived responses that real CAs serve.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void oldResponseWithValidNextUpdateIsAccepted() throws Exception {
        ResponseSpec responseSpec = new ResponseSpec(
            false, OLD_THIS_UPDATE_AGE_MILLIS, NEXT_UPDATE_AHEAD_MILLIS);
        validateCertificate(responseSpec, outcome -> {
            assertEquals(1, outcome.ocspRequests, "OCSP responder must be queried");
            assertTrue(outcome.verdictReceived, "Must deliver verdict to the application");
            assertEquals(OcspResponse.Status.VALID, outcome.status,
                "A response that is still within its nextUpdate window was not accepted");
            assertNull(outcome.failure);
        });
    }

    /**
     * Performs a TLS handshake against a local server whose certificate status is served by a local OCSP
     * responder, using {@link OcspServerCertificateValidator} with {@code closeAndThrowIfNotValid} enabled,
     * and collects what the application was told.
     *
     * @param spec describes the OCSP response the responder will serve
     * @param assertions the assertions to verify on the {@link ValidationOutcome}
     */
    private void validateCertificate(ResponseSpec spec, Consumer<ValidationOutcome> assertions) throws Exception {
        final X509Bundle issuer = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.ecp256)
                .subject("CN=OcspIssuerCA")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        final IoTransport ioTransport = createDefaultTransport();
        final DnsNameResolver dnsNameResolver = createDefaultResolver(ioTransport);
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        SslContext serverSslContext = null;
        SslContext clientSslContext = null;
        Channel ocspResponder = null;
        Channel tlsServer = null;
        Channel client = null;
        try {
            final AtomicReference<byte[]> ocspResponseBytes = new AtomicReference<byte[]>();
            final AtomicInteger ocspRequests = new AtomicInteger();

            // Local OCSP responder. It always answers with the response bytes prepared below.
            ocspResponder = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                            pipeline.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
                                    ocspRequests.incrementAndGet();
                                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.OK,
                                            Unpooled.wrappedBuffer(ocspResponseBytes.get()));
                                    response.headers()
                                            .set(HttpHeaderNames.CONTENT_TYPE, "application/ocsp-response")
                                            .set(HttpHeaderNames.CONTENT_LENGTH,
                                                    response.content().readableBytes());
                                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                                }
                            });
                        }
                    })
                    .bind(NetUtil.LOCALHOST4, 0)
                    .sync()
                    .channel();
            int ocspPort = ((InetSocketAddress) ocspResponder.localAddress()).getPort();

            // The server certificate points at our local OCSP responder using the AIA extension.
            GeneralName ocspUri = new GeneralName(GeneralName.uniformResourceIdentifier,
                    "http://" + NetUtil.LOCALHOST4.getHostAddress() + ':' + ocspPort + '/');
            AuthorityInformationAccess aia = new AuthorityInformationAccess(
                    new AccessDescription(AccessDescription.id_ad_ocsp, ocspUri));
            X509Bundle serverBundle = new CertificateBuilder()
                    .algorithm(CertificateBuilder.Algorithm.ecp256)
                    .subject("CN=localhost")
                    .addSanDnsName("localhost")
                    .addExtendedKeyUsageServerAuth()
                    .addExtensionOctetString(OID_AUTHORITY_INFORMATION_ACCESS, false, aia.getEncoded())
                    .buildIssuedBy(issuer);

            ocspResponseBytes.set(ocspResponse(issuer, serverBundle.getCertificate(), spec));

            serverSslContext = SslContextBuilder.forServer(serverBundle.getKeyPair().getPrivate(),
                    serverBundle.getCertificatePathWithRoot()).build();
            final SslContext serverSslCtx = serverSslContext;
            tlsServer = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(serverSslCtx.newHandler(ch.alloc()));
                        }
                    })
                    .bind(NetUtil.LOCALHOST4, 0)
                    .sync()
                    .channel();
            final int tlsPort = ((InetSocketAddress) tlsServer.localAddress()).getPort();

            clientSslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            final SslContext clientSslCtx = clientSslContext;

            final ValidationOutcome outcome = new ValidationOutcome();
            final CountDownLatch verdictLatch = new CountDownLatch(1);
            client = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(clientSslCtx.newHandler(ch.alloc(), "localhost", tlsPort));
                            pipeline.addLast(new OcspServerCertificateValidator(
                                    true, false, ioTransport, dnsNameResolver));
                            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt instanceof OcspValidationEvent) {
                                        outcome.status = ((OcspValidationEvent) evt).response().status();
                                        verdictLatch.countDown();
                                    }
                                    ctx.fireUserEventTriggered(evt);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    if (outcome.failure == null) {
                                        outcome.failure = cause;
                                    }
                                    verdictLatch.countDown();
                                }
                            });
                        }
                    })
                    .connect(NetUtil.LOCALHOST4, tlsPort)
                    .sync()
                    .channel();

            outcome.verdictReceived = verdictLatch.await(20, TimeUnit.SECONDS);
            outcome.channelClosedFuture = client.closeFuture();
            outcome.ocspRequests = ocspRequests.get();
            assertions.accept(outcome);
        } finally {
            if (client != null) {
                client.close().syncUninterruptibly();
            }
            if (tlsServer != null) {
                tlsServer.close().syncUninterruptibly();
            }
            if (ocspResponder != null) {
                ocspResponder.close().syncUninterruptibly();
            }
            dnsNameResolver.close();
            ReferenceCountUtil.release(clientSslContext);
            ReferenceCountUtil.release(serverSslContext);
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    /**
     * Build a signed OCSP response for the given certificate, as described by the given {@link ResponseSpec}.
     */
    private static byte[] ocspResponse(X509Bundle issuer, X509Certificate certificate,
                                       ResponseSpec spec) throws Exception {
        JcaX509CertificateHolder issuerHolder = new JcaX509CertificateHolder(issuer.getCertificate());
        CertificateID certificateId = new CertificateID(
                new JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1),
                issuerHolder, certificate.getSerialNumber());

        long now = System.currentTimeMillis();
        Date thisUpdate = new Date(now - spec.thisUpdateAgeMillis);
        Date nextUpdate = spec.nextUpdateOffsetMillis == null ? null :
                new Date(now + spec.nextUpdateOffsetMillis);
        CertificateStatus status = spec.revoked ?
                new RevokedStatus(new Date(now - TimeUnit.DAYS.toMillis(1))) : CertificateStatus.GOOD;

        BasicOCSPRespBuilder builder = new BasicOCSPRespBuilder(new RespID(issuerHolder.getSubject()));
        builder.addResponse(certificateId, status, thisUpdate, nextUpdate);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .build(issuer.getKeyPair().getPrivate());
        BasicOCSPResp basicResponse = builder.build(signer, null, new Date(now));
        return new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basicResponse).getEncoded();
    }

    /**
     * Describes the OCSP response the test responder should serve.
     */
    private static final class ResponseSpec {
        private final boolean revoked;
        private final long thisUpdateAgeMillis;
        private final Long nextUpdateOffsetMillis;

        /**
         * @param revoked                if {@code true} the certificate is reported as {@code REVOKED},
         *                               otherwise as {@code GOOD}
         * @param thisUpdateAgeMillis    how long ago {@code thisUpdate} is
         * @param nextUpdateOffsetMillis how far ahead of now {@code nextUpdate} is; a negative value puts it in
         *                               the past, and {@code null} omits the OPTIONAL field entirely
         */
        ResponseSpec(boolean revoked, long thisUpdateAgeMillis, Long nextUpdateOffsetMillis) {
            this.revoked = revoked;
            this.thisUpdateAgeMillis = thisUpdateAgeMillis;
            this.nextUpdateOffsetMillis = nextUpdateOffsetMillis;
        }
    }

    private static final class ValidationOutcome {
        volatile OcspResponse.Status status;
        volatile Throwable failure;
        volatile boolean verdictReceived;
        volatile ChannelFuture channelClosedFuture;
        volatile int ocspRequests;
    }
}
