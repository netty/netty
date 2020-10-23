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
package io.netty.handler.ssl;

import io.netty.util.internal.SuppressJava6Requirement;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

final class OpenSslX509Certificate extends X509Certificate {

    private final byte[] bytes;
    private X509Certificate wrapped;

    OpenSslX509Certificate(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
        unwrap().checkValidity();
    }

    @Override
    public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {
        unwrap().checkValidity(date);
    }

    @Override
    public X500Principal getIssuerX500Principal() {
        return unwrap().getIssuerX500Principal();
    }

    @Override
    public X500Principal getSubjectX500Principal() {
        return unwrap().getSubjectX500Principal();
    }

    @Override
    public List<String> getExtendedKeyUsage() throws CertificateParsingException {
        return unwrap().getExtendedKeyUsage();
    }

    @Override
    public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
        return unwrap().getSubjectAlternativeNames();
    }

    @Override
    public Collection<List<?>> getIssuerAlternativeNames() throws CertificateParsingException {
        return unwrap().getSubjectAlternativeNames();
    }

    // No @Override annotation as it was only introduced in Java8.
    @SuppressJava6Requirement(reason = "Can only be called from Java8 as class is package-private")
    public void verify(PublicKey key, Provider sigProvider)
            throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        unwrap().verify(key, sigProvider);
    }

    @Override
    public int getVersion() {
        return unwrap().getVersion();
    }

    @Override
    public BigInteger getSerialNumber() {
        return unwrap().getSerialNumber();
    }

    @Override
    public Principal getIssuerDN() {
        return unwrap().getIssuerDN();
    }

    @Override
    public Principal getSubjectDN() {
        return unwrap().getSubjectDN();
    }

    @Override
    public Date getNotBefore() {
        return unwrap().getNotBefore();
    }

    @Override
    public Date getNotAfter() {
        return unwrap().getNotAfter();
    }

    @Override
    public byte[] getTBSCertificate() throws CertificateEncodingException {
        return unwrap().getTBSCertificate();
    }

    @Override
    public byte[] getSignature() {
        return unwrap().getSignature();
    }

    @Override
    public String getSigAlgName() {
        return unwrap().getSigAlgName();
    }

    @Override
    public String getSigAlgOID() {
        return unwrap().getSigAlgOID();
    }

    @Override
    public byte[] getSigAlgParams() {
        return unwrap().getSigAlgParams();
    }

    @Override
    public boolean[] getIssuerUniqueID() {
        return unwrap().getIssuerUniqueID();
    }

    @Override
    public boolean[] getSubjectUniqueID() {
        return unwrap().getSubjectUniqueID();
    }

    @Override
    public boolean[] getKeyUsage() {
        return unwrap().getKeyUsage();
    }

    @Override
    public int getBasicConstraints() {
        return unwrap().getBasicConstraints();
    }

    @Override
    public byte[] getEncoded() {
        return bytes.clone();
    }

    @Override
    public void verify(PublicKey key)
            throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException {
        unwrap().verify(key);
    }

    @Override
    public void verify(PublicKey key, String sigProvider)
            throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchProviderException, SignatureException {
        unwrap().verify(key, sigProvider);
    }

    @Override
    public String toString() {
        return unwrap().toString();
    }

    @Override
    public PublicKey getPublicKey() {
        return unwrap().getPublicKey();
    }

    @Override
    public boolean hasUnsupportedCriticalExtension() {
        return unwrap().hasUnsupportedCriticalExtension();
    }

    @Override
    public Set<String> getCriticalExtensionOIDs() {
        return unwrap().getCriticalExtensionOIDs();
    }

    @Override
    public Set<String> getNonCriticalExtensionOIDs() {
        return unwrap().getNonCriticalExtensionOIDs();
    }

    @Override
    public byte[] getExtensionValue(String oid) {
        return unwrap().getExtensionValue(oid);
    }

    private X509Certificate unwrap() {
        X509Certificate wrapped = this.wrapped;
        if (wrapped == null) {
            try {
                wrapped = this.wrapped = (X509Certificate) SslContext.X509_CERT_FACTORY.generateCertificate(
                        new ByteArrayInputStream(bytes));
            } catch (CertificateException e) {
                throw new IllegalStateException(e);
            }
        }
        return wrapped;
    }
}
