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

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.ocsp.OcspTestUtil.CertAndKey;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.concurrent.Promise;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.ssl.ocsp.OcspServerCertificateValidator.createDefaultResolver;
import static io.netty.handler.ssl.ocsp.OcspTestUtil.buildCertificate;
import static io.netty.handler.ssl.ocsp.OcspTestUtil.ocspUrlExtension;
import static io.netty.handler.ssl.ocsp.OcspTestUtil.startOcspResponder;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OcspClientTest {

    @Test
    void simpleOcspQueryTest() throws Exception {
        CertAndKey caRoot = buildCertificate("CN=TrustedRootCA", true, null);

        EventLoopGroup serverGroup = new NioEventLoopGroup(1);
        try {
            // Start a local OCSP responder so the test never leaves the loopback interface.
            Channel server = startOcspResponder(serverGroup, caRoot);
            try {
                CertAndKey serverCert = buildCertificate("CN=TargetServer", false, caRoot,
                        ocspUrlExtension(server));

                DnsNameResolver resolver = createDefaultResolver(IoTransport.DEFAULT);
                try {
                    Promise<BasicOCSPResp> promise = IoTransport.DEFAULT.eventLoop().newPromise();
                    OcspClient.query(serverCert.certificate, caRoot.certificate, true,
                            IoTransport.DEFAULT, resolver, promise);
                    BasicOCSPResp basicOCSPResp = promise.get(10, TimeUnit.SECONDS);

                    // 'null' means certificate is valid
                    assertNull(basicOCSPResp.getResponses()[0].getCertStatus());
                } finally {
                    resolver.close();
                }
            } finally {
                server.close().sync();
            }
        } finally {
            serverGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    void validateSignatureWithInvalidChainThrows() throws Exception {
        // Build an unrelated responder chain so nothing is signed by the provided issuer (using RSA)
        final CertAndKey issuerBundle = buildCertificate("CN=Issuer", true, null);

        // Different CA
        CertAndKey otherRoot = buildCertificate("CN=SomeRootCA", true, null);
        CertAndKey otherIntermediate = buildCertificate("CN=SomeIntermediateCA", true, otherRoot);
        CertAndKey otherResponder = buildCertificate("CN=SomeResponder", false, otherIntermediate);

        X509CertificateHolder responderHolder = new JcaX509CertificateHolder(otherResponder.certificate);
        X509CertificateHolder intermediateHolder = new JcaX509CertificateHolder(otherIntermediate.certificate);

        // Create actual OCSP response with untrusted chain
        final BasicOCSPResp resp = createBasicOcspResponse(
                otherResponder,
                new X509CertificateHolder[]{responderHolder, intermediateHolder}
        );

        assertThrows(OCSPException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                OcspClient.validateSignature(resp, issuerBundle.certificate);
            }
        });
    }

    private static BasicOCSPResp createBasicOcspResponse(CertAndKey responderBundle,
                                                         X509CertificateHolder[] certChain) throws Exception {
        CertAndKey dummyCert = buildCertificate("CN=DummyCert", true, null);

        // Create certificate ID for OCSP response
        CertificateID certId = new CertificateID(
                new JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1),
                new JcaX509CertificateHolder(dummyCert.certificate),
                dummyCert.certificate.getSerialNumber()
        );

        // Create response builder with responder ID based on certificate
        X509CertificateHolder responderHolder = new JcaX509CertificateHolder(responderBundle.certificate);
        RespID respID = new RespID(responderHolder.getSubject());

        BasicOCSPRespBuilder respBuilder = new BasicOCSPRespBuilder(respID);

        // Add response for the certificate (status: good)
        respBuilder.addResponse(certId, CertificateStatus.GOOD);

        // Build and sign the response with the responder's private key
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(responderBundle.keyPair.getPrivate());

        return respBuilder.build(signer, certChain, new Date());
    }
}
