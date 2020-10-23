/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.ssl.util;

import io.netty.util.internal.SuppressJava6Requirement;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateIssuerName;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import java.util.Date;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;

import static io.netty.handler.ssl.util.SelfSignedCertificate.*;

/**
 * Generates a self-signed certificate using {@code sun.security.x509} package provided by OpenJDK.
 */
final class OpenJdkSelfSignedCertGenerator {

    @SuppressJava6Requirement(reason = "Usage guarded by dependency check")
    static String[] generate(String fqdn, KeyPair keypair, SecureRandom random, Date notBefore, Date notAfter,
                             String algorithm) throws Exception {
        PrivateKey key = keypair.getPrivate();

        // Prepare the information required for generating an X.509 certificate.
        X509CertInfo info = new X509CertInfo();
        X500Name owner = new X500Name("CN=" + fqdn);
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, random)));
        try {
            info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
        } catch (CertificateException ignore) {
            info.set(X509CertInfo.SUBJECT, owner);
        }
        try {
            info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
        } catch (CertificateException ignore) {
            info.set(X509CertInfo.ISSUER, owner);
        }
        info.set(X509CertInfo.VALIDITY, new CertificateValidity(notBefore, notAfter));
        info.set(X509CertInfo.KEY, new CertificateX509Key(keypair.getPublic()));
        info.set(X509CertInfo.ALGORITHM_ID,
                // sha256WithRSAEncryption
                new CertificateAlgorithmId(AlgorithmId.get("1.2.840.113549.1.1.11")));

        // Sign the cert to identify the algorithm that's used.
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(key, algorithm.equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA");

        // Update the algorithm and sign again.
        info.set(CertificateAlgorithmId.NAME + '.' + CertificateAlgorithmId.ALGORITHM, cert.get(X509CertImpl.SIG_ALG));
        cert = new X509CertImpl(info);
        cert.sign(key, algorithm.equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA");
        cert.verify(keypair.getPublic());

        return newSelfSignedCertificate(fqdn, key, cert);
    }

    private OpenJdkSelfSignedCertGenerator() { }
}
