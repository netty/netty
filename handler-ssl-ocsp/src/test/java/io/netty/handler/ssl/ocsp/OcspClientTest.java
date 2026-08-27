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

import io.netty.util.concurrent.Promise;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import static io.netty.handler.ssl.ocsp.OcspServerCertificateValidator.createDefaultResolver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OcspClientTest {

    private static final SecureRandom RANDOM = new SecureRandom();

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

            Promise<BasicOCSPResp> promise = IoTransport.DEFAULT.eventLoop().newPromise();
            OcspClient.query(serverCert, certIssuer, false,
                    IoTransport.DEFAULT, createDefaultResolver(IoTransport.DEFAULT), promise);
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
        final CertAndKey rootIssuer = buildCertificate("CN=SomeRootCA", true, null);
        CertAndKey intermediateIssuer = buildCertificate("CN=SomeIntermediateCA", true, rootIssuer);
        CertAndKey ocspResponder = buildCertificate("CN=SomeOCSPResponder", false, intermediateIssuer);

        // Create actual OCSP response with the responder's certificate
        X509CertificateHolder responderHolder = new JcaX509CertificateHolder(ocspResponder.certificate);
        X509CertificateHolder intermediateHolder = new JcaX509CertificateHolder(intermediateIssuer.certificate);

        // Create a minimal BasicOCSPResp that contains the certificate chain
        final BasicOCSPResp resp = createBasicOcspResponse(
                ocspResponder,
                new X509CertificateHolder[]{responderHolder, intermediateHolder}
        );

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                OcspClient.validateSignature(resp, rootIssuer.certificate);
            }
        });
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

    /**
     * Build an X.509 certificate with the given subject. If {@code issuer} is {@code null} the certificate is
     * self-signed, otherwise it is issued (signed) by the given issuer. Uses RSA-2048 keys.
     */
    private static CertAndKey buildCertificate(String subjectDn, boolean isCertificateAuthority,
                                               CertAndKey issuer) throws Exception {
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

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signingKey);
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(holder);

        return new CertAndKey(certificate, keyPair);
    }

    private static final class CertAndKey {
        final X509Certificate certificate;
        final KeyPair keyPair;

        CertAndKey(X509Certificate certificate, KeyPair keyPair) {
            this.certificate = certificate;
            this.keyPair = keyPair;
        }
    }
}
