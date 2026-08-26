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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.concurrent.Promise;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPResponse;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.asn1.ocsp.ResponseBytes;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import static io.netty.handler.ssl.ocsp.OcspServerCertificateValidator.createDefaultResolver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OcspClientTest extends AbstractOcspTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://apple.com"})
    void simpleOcspQueryTest(String urlString) throws IOException, ExecutionException, InterruptedException {
        HttpsURLConnection httpsConnection = null;
        try {
            URL url = new URL(urlString);
            httpsConnection = (HttpsURLConnection) url.openConnection();
            httpsConnection.connect();

            // Pull server certificates for validation
            X509Certificate[] certs = (X509Certificate[]) httpsConnection.getServerCertificates();
            X509Certificate serverCert = certs[0];
            X509Certificate certIssuer = certs[1];

            IoTransport transport = createDefaultTransport();
            Promise<BasicOCSPResp> promise = transport.eventLoop().newPromise();
            OcspClient.query(serverCert, certIssuer, false,
                    transport, createDefaultResolver(transport), promise);
            BasicOCSPResp basicOCSPResp = promise.get();

            // 'null' means certificate is valid
            assertNull(basicOCSPResp.getResponses()[0].getCertStatus());
        } finally {
            if (httpsConnection != null) {
                httpsConnection.disconnect();
            }
        }
    }

    @Test
    void validateSignatureWithIncludedChainSucceeds() throws Exception {
        X509Bundle rootIssuer = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=SomeRootCA")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        X509Bundle intermediateIssuer = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=SomeIntermediateCA")
                .setIsCertificateAuthority(true)
                .buildIssuedBy(rootIssuer);

        X509Bundle ocspResponder = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=SomeOCSPResponder")
                .buildIssuedBy(intermediateIssuer);

        // Create actual OCSP response with the responder's certificate
        X509CertificateHolder responderHolder = new JcaX509CertificateHolder(ocspResponder.getCertificate());
        X509CertificateHolder intermediateHolder = new JcaX509CertificateHolder(intermediateIssuer.getCertificate());

        // Create a minimal BasicOCSPResp that contains the certificate chain
        BasicOCSPResp resp = createBasicOcspResponse(
                ocspResponder,
                new X509CertificateHolder[]{responderHolder, intermediateHolder}
        );

        assertDoesNotThrow(() -> OcspClient.validateSignature(resp, rootIssuer.getCertificate()));
    }

    @Test
    void validateSignatureWithInvalidChainThrows() throws Exception {
        // Build an unrelated responder chain so nothing is signed by the provided issuer (using RSA)
        X509Bundle issuerBundle = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=Issuer")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        // Different CA
        X509Bundle otherRoot = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=SomeRootCA")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        X509Bundle otherIntermediate = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=SomeIntermediateCA")
                .setIsCertificateAuthority(true)
                .buildIssuedBy(otherRoot);

        X509Bundle otherResponder = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=SomeResponder")
                .buildIssuedBy(otherIntermediate);

        X509CertificateHolder responderHolder = new JcaX509CertificateHolder(otherResponder.getCertificate());
        X509CertificateHolder intermediateHolder = new JcaX509CertificateHolder(otherIntermediate.getCertificate());

        // Create actual OCSP response with untrusted chain
        BasicOCSPResp resp = createBasicOcspResponse(
                otherResponder,
                new X509CertificateHolder[]{responderHolder, intermediateHolder}
        );

        assertThrows(OCSPException.class, () ->
                OcspClient.validateSignature(resp, issuerBundle.getCertificate())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"unrelated-certificate", "non-basic-response-type", "null-response-bytes"})
    void testCertIdBypass(String scenario) throws Exception {
        X509Bundle caRoot = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=TrustedRootCA")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        GeneralName ocspName = new GeneralName(GeneralName.uniformResourceIdentifier, "http://localhost/");
        AuthorityInformationAccess aia = new AuthorityInformationAccess(
                new AccessDescription(AccessDescription.id_ad_ocsp, ocspName));
        X509Bundle targetCert = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=TargetServer")
                .addExtensionOctetString("1.3.6.1.5.5.7.1.1", false, aia.getEncoded())
                .buildIssuedBy(caRoot);

        byte[] forgedResponseEncoded = forgedResponse(scenario, caRoot);
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            IoTransport transport = IoTransport.create(group.next(), () -> {
                NioSocketChannel channel = new NioSocketChannel();
                channel.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                        SocketAddress localAddress, ChannelPromise promise) {
                        promise.setSuccess();

                        ctx.executor().execute(() -> {
                            ctx.pipeline().fireChannelActive();
                            DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.OK, Unpooled.wrappedBuffer(forgedResponseEncoded));
                            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/ocsp-response");
                            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH,
                                    httpResponse.content().readableBytes());

                            ctx.pipeline().fireChannelRead(httpResponse);
                        });
                    }
                });
                return channel;
            }, NioDatagramChannel::new);

            DnsNameResolver resolver = OcspServerCertificateValidator.createDefaultResolver(transport);
            Promise<BasicOCSPResp> promise = transport.eventLoop().newPromise();
            OcspClient.query(
                    targetCert.getCertificate(), caRoot.getCertificate(), false,
                    transport, resolver, promise);

            promise.await();

            assertFalse(promise.isSuccess());
            resolver.close();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static byte[] forgedResponse(String scenario, X509Bundle caRoot) throws Exception {
        if ("non-basic-response-type".equals(scenario)) {
            ResponseBytes responseBytes = new ResponseBytes(
                                       new ASN1ObjectIdentifier("1.2.3.4.5"),
                    new DEROctetString(new byte[]{ 0x30, 0x00 }));
                        return new OCSPResponse(new OCSPResponseStatus(OCSPResponseStatus.SUCCESSFUL), responseBytes)
                                        .getEncoded();
        }
        if ("null-response-bytes".equals(scenario)) {
            return new OCSPResponse(new OCSPResponseStatus(OCSPResponseStatus.UNAUTHORIZED), null).getEncoded();
        }
        X509CertificateHolder caHolder = new JcaX509CertificateHolder(caRoot.getCertificate());
        BasicOCSPResp forgedBasicResp = createBasicOcspResponse(caRoot, new X509CertificateHolder[]{caHolder});
        return new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, forgedBasicResp).getEncoded();
    }

    private static BasicOCSPResp createBasicOcspResponse(X509Bundle responderBundle,
                                                         X509CertificateHolder[] certChain) throws Exception {
        X509Bundle dummyCert = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.rsa2048)
                .subject("CN=DummyCert")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        // Create certificate ID for OCSP response
        CertificateID certId = new CertificateID(
                new JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1),
                new JcaX509CertificateHolder(dummyCert.getCertificate()),
                dummyCert.getCertificate().getSerialNumber()
        );

        // Create response builder with responder ID based on certificate
        X509CertificateHolder responderHolder = new JcaX509CertificateHolder(responderBundle.getCertificate());
        RespID respID = new RespID(responderHolder.getSubject());

        BasicOCSPRespBuilder respBuilder = new BasicOCSPRespBuilder(respID);

        // Add response for the certificate (status: good)
        respBuilder.addResponse(certId, CertificateStatus.GOOD);

        // Build and sign the response with the responder's private key
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(responderBundle.getKeyPair().getPrivate());

        return respBuilder.build(signer, certChain, new Date());
    }
}
