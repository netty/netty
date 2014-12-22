/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

final class OpenSslX509Certificate extends X509Certificate {

    private final byte[] bytes;
    private X509Certificate wrapped;

    public OpenSslX509Certificate(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
        wrapped().checkValidity();
    }

    @Override
    public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {
        wrapped().checkValidity(date);
    }

    @Override
    public int getVersion() {
        return wrapped().getVersion();
    }

    @Override
    public BigInteger getSerialNumber() {
        return wrapped().getSerialNumber();
    }

    @Override
    public Principal getIssuerDN() {
        return wrapped().getIssuerDN();
    }

    @Override
    public Principal getSubjectDN() {
        return wrapped().getSubjectDN();
    }

    @Override
    public Date getNotBefore() {
        return wrapped().getNotBefore();
    }

    @Override
    public Date getNotAfter() {
        return wrapped().getNotAfter();
    }

    @Override
    public byte[] getTBSCertificate() throws CertificateEncodingException {
        return wrapped().getTBSCertificate();
    }

    @Override
    public byte[] getSignature() {
        return wrapped().getSignature();
    }

    @Override
    public String getSigAlgName() {
        return wrapped().getSigAlgName();
    }

    @Override
    public String getSigAlgOID() {
        return wrapped().getSigAlgOID();
    }

    @Override
    public byte[] getSigAlgParams() {
        return wrapped().getSigAlgParams();
    }

    @Override
    public boolean[] getIssuerUniqueID() {
        return wrapped().getIssuerUniqueID();
    }

    @Override
    public boolean[] getSubjectUniqueID() {
        return wrapped().getSubjectUniqueID();
    }

    @Override
    public boolean[] getKeyUsage() {
        return wrapped().getKeyUsage();
    }

    @Override
    public int getBasicConstraints() {
        return wrapped().getBasicConstraints();
    }

    @Override
    public byte[] getEncoded() {
        return bytes.clone();
    }

    @Override
    public void verify(PublicKey key)
            throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException {
        wrapped().verify(key);
    }

    @Override
    public void verify(PublicKey key, String sigProvider)
            throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchProviderException, SignatureException {
        wrapped().verify(key, sigProvider);
    }

    @Override
    public String toString() {
        return wrapped().toString();
    }

    @Override
    public PublicKey getPublicKey() {
        return wrapped().getPublicKey();
    }

    @Override
    public boolean hasUnsupportedCriticalExtension() {
        return wrapped().hasUnsupportedCriticalExtension();
    }

    @Override
    public Set<String> getCriticalExtensionOIDs() {
        return wrapped().getCriticalExtensionOIDs();
    }

    @Override
    public Set<String> getNonCriticalExtensionOIDs() {
        return wrapped().getNonCriticalExtensionOIDs();
    }

    @Override
    public byte[] getExtensionValue(String oid) {
        return wrapped().getExtensionValue(oid);
    }

    private X509Certificate wrapped() {
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
