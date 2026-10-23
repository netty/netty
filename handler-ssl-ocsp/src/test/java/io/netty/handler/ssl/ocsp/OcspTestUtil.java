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
package io.netty.handler.ssl.ocsp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.NetUtil;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Helpers that allow the OCSP tests to run against a local OCSP responder instead of the network.
 */
final class OcspTestUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OcspTestUtil() {
    }

    /**
     * Start a local OCSP responder on the loopback interface. It answers every request with a signed {@code good}
     * status for the requested certificate and echoes the nonce.
     *
     * @return the {@link Channel} that is bound to a random port
     */
    static Channel startOcspResponder(EventLoopGroup group, final CertAndKey issuer) throws InterruptedException {
        return new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec(), new HttpObjectAggregator(64 * 1024),
                                new LocalOcspResponder(issuer));
                    }
                })
                .bind(NetUtil.LOCALHOST4, 0)
                .sync().channel();
    }

    /**
     * Returns the AIA extension that points to the OCSP responder that listens on the given {@link Channel}.
     */
    static Extension ocspUrlExtension(Channel ocspResponder) throws IOException {
        int port = ((InetSocketAddress) ocspResponder.localAddress()).getPort();
        GeneralName ocspName = new GeneralName(GeneralName.uniformResourceIdentifier,
                "http://" + NetUtil.LOCALHOST4.getHostAddress() + ':' + port + '/');
        AuthorityInformationAccess aia = new AuthorityInformationAccess(
                new AccessDescription(AccessDescription.id_ad_ocsp, ocspName));
        return new Extension(Extension.authorityInfoAccess, false, new DEROctetString(aia));
    }

    /**
     * Build an X.509 certificate with the given subject. If {@code issuer} is {@code null} the certificate is
     * self-signed, otherwise it is issued (signed) by the given issuer. Uses RSA-2048 keys and adds the given
     * {@code extensions} to the certificate.
     */
    static CertAndKey buildCertificate(String subjectDn, boolean isCertificateAuthority,
                                       CertAndKey issuer, Extension... extensions) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name subject = new X500Name(subjectDn);
        boolean selfSigned = issuer == null;
        X500Name issuerName = selfSigned
                ? subject
                : new JcaX509CertificateHolder(issuer.certificate).getSubject();
        PrivateKey signingKey = selfSigned ? keyPair.getPrivate() : issuer.keyPair.getPrivate();

        Date notBefore = new Date(System.currentTimeMillis() - 86400000L);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 86400000L);

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerName, new BigInteger(64, RANDOM), notBefore, notAfter, subject, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCertificateAuthority));
        for (Extension extension : extensions) {
            builder.addExtension(extension);
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signingKey);
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(holder);

        return new CertAndKey(certificate, keyPair);
    }

    static final class CertAndKey {
        final X509Certificate certificate;
        final KeyPair keyPair;

        CertAndKey(X509Certificate certificate, KeyPair keyPair) {
            this.certificate = certificate;
            this.keyPair = keyPair;
        }
    }

    private static final class LocalOcspResponder extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final CertAndKey issuer;

        LocalOcspResponder(CertAndKey issuer) {
            this.issuer = issuer;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            assertEquals(HttpMethod.POST, request.method());
            assertEquals("application/ocsp-request", request.headers().get(HttpHeaderNames.CONTENT_TYPE));

            byte[] requestBytes = new byte[request.content().readableBytes()];
            request.content().readBytes(requestBytes);
            OCSPReq ocspReq = new OCSPReq(requestBytes);

            X509CertificateHolder issuerHolder = new JcaX509CertificateHolder(issuer.certificate);
            BasicOCSPRespBuilder respBuilder = new BasicOCSPRespBuilder(new RespID(issuerHolder.getSubject()));
            for (Req req : ocspReq.getRequestList()) {
                respBuilder.addResponse(req.getCertID(), CertificateStatus.GOOD);
            }
            Extension nonce = ocspReq.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
            if (nonce != null) {
                respBuilder.setResponseExtensions(new Extensions(nonce));
            }
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .build(issuer.keyPair.getPrivate());
            BasicOCSPResp basicResp = respBuilder.build(
                    signer, new X509CertificateHolder[] { issuerHolder }, new Date());
            byte[] encoded = new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basicResp).getEncoded();

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(encoded));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/ocsp-response");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, encoded.length);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
