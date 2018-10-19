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

package io.netty.handler.ssl.util;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.StringUtil;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An {@link TrustManagerFactory} that trusts an X.509 certificate whose SHA1 checksum matches.
 * <p>
 * <strong>NOTE:</strong> It is recommended to verify certificates and their chain to prevent
 * <a href="https://en.wikipedia.org/wiki/Man-in-the-middle_attack">Man-in-the-middle attacks</a>.
 * This {@link TrustManagerFactory} will <strong>only</strong> verify that the fingerprint of certificates match one
 * of the given fingerprints. This procedure is called
 * <a href="https://en.wikipedia.org/wiki/Transport_Layer_Security#Certificate_pinning">certificate pinning</a> and
 * is an effective protection. For maximum security one should verify that the whole certificate chain is as expected.
 * It is worth mentioning that certain firewalls, proxies or other appliances found in corporate environments,
 * actually perform Man-in-the-middle attacks and thus present a different certificate fingerprint.
 * </p>
 * <p>
 * The SHA1 checksum of an X.509 certificate is calculated from its DER encoded format.  You can get the fingerprint of
 * an X.509 certificate using the {@code openssl} command.  For example:
 *
 * <pre>
 * $ openssl x509 -fingerprint -sha1 -in my_certificate.crt
 * SHA1 Fingerprint=4E:85:10:55:BC:7B:12:08:D1:EA:0A:12:C9:72:EE:F3:AA:B2:C7:CB
 * -----BEGIN CERTIFICATE-----
 * MIIBqjCCAROgAwIBAgIJALiT3Nvp0kvmMA0GCSqGSIb3DQEBBQUAMBYxFDASBgNV
 * BAMTC2V4YW1wbGUuY29tMCAXDTcwMDEwMTAwMDAwMFoYDzk5OTkxMjMxMjM1OTU5
 * WjAWMRQwEgYDVQQDEwtleGFtcGxlLmNvbTCBnzANBgkqhkiG9w0BAQEFAAOBjQAw
 * gYkCgYEAnadvODG0QCiHhaFZlLHtr5gLIkDQS8ErZ//KfqeCHTC/KJsl3xYFk0zG
 * aCv2FcmkOlokm77qV8qOW2DZdND7WuYzX6nLVuLb+GYxZ7b45iMAbAajvGh8jc9U
 * o07fUIahGqTDAIAGCWsoLUOQ9nMzO/8GRHcXJAeQ2MGY2VpCcv0CAwEAATANBgkq
 * hkiG9w0BAQUFAAOBgQBpRCnmjmNM0D7yrpkUJpBTNiqinhKLbeOvPWm+YmdInUUs
 * LoMu0mZ1IANemLwqbwJJ76fknngeB+YuVAj46SurvVCV6ekwHcbgpW1u063IRwKk
 * tQhOBO0HQxldUS4+4MYv/kuvnKkbjfgh5qfWw89Kx4kD+cycpP4yPtgDGk8ZMA==
 * -----END CERTIFICATE-----
 * </pre>
 * </p>
 */
public final class FingerprintTrustManagerFactory extends SimpleTrustManagerFactory {

    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");
    private static final Pattern FINGERPRINT_STRIP_PATTERN = Pattern.compile(":");
    private static final int SHA1_BYTE_LEN = 20;
    private static final int SHA1_HEX_LEN = SHA1_BYTE_LEN * 2;

    private static final FastThreadLocal<MessageDigest> tlmd = new FastThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
                // All Java implementation must have SHA1 digest algorithm.
                throw new Error(e);
            }
        }
    };

    private final TrustManager tm = new X509TrustManager() {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String s) throws CertificateException {
            checkTrusted("client", chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String s) throws CertificateException {
            checkTrusted("server", chain);
        }

        private void checkTrusted(String type, X509Certificate[] chain) throws CertificateException {
            X509Certificate cert = chain[0];
            byte[] fingerprint = fingerprint(cert);
            boolean found = false;
            for (byte[] allowedFingerprint: fingerprints) {
                if (Arrays.equals(fingerprint, allowedFingerprint)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new CertificateException(
                        type + " certificate with unknown fingerprint: " + cert.getSubjectDN());
            }
        }

        private byte[] fingerprint(X509Certificate cert) throws CertificateEncodingException {
            MessageDigest md = tlmd.get();
            md.reset();
            return md.digest(cert.getEncoded());
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return EmptyArrays.EMPTY_X509_CERTIFICATES;
        }
    };

    private final byte[][] fingerprints;

    /**
     * Creates a new instance.
     *
     * @param fingerprints a list of SHA1 fingerprints in hexadecimal form
     */
    public FingerprintTrustManagerFactory(Iterable<String> fingerprints) {
        this(toFingerprintArray(fingerprints));
    }

    /**
     * Creates a new instance.
     *
     * @param fingerprints a list of SHA1 fingerprints in hexadecimal form
     */
    public FingerprintTrustManagerFactory(String... fingerprints) {
        this(toFingerprintArray(Arrays.asList(fingerprints)));
    }

    /**
     * Creates a new instance.
     *
     * @param fingerprints a list of SHA1 fingerprints
     */
    public FingerprintTrustManagerFactory(byte[]... fingerprints) {
        if (fingerprints == null) {
            throw new NullPointerException("fingerprints");
        }

        List<byte[]> list = new ArrayList<byte[]>(fingerprints.length);
        for (byte[] f: fingerprints) {
            if (f == null) {
                break;
            }
            if (f.length != SHA1_BYTE_LEN) {
                throw new IllegalArgumentException("malformed fingerprint: " +
                        ByteBufUtil.hexDump(Unpooled.wrappedBuffer(f)) + " (expected: SHA1)");
            }
            list.add(f.clone());
        }

        this.fingerprints = list.toArray(new byte[0][]);
    }

    private static byte[][] toFingerprintArray(Iterable<String> fingerprints) {
        if (fingerprints == null) {
            throw new NullPointerException("fingerprints");
        }

        List<byte[]> list = new ArrayList<byte[]>();
        for (String f: fingerprints) {
            if (f == null) {
                break;
            }

            if (!FINGERPRINT_PATTERN.matcher(f).matches()) {
                throw new IllegalArgumentException("malformed fingerprint: " + f);
            }
            f = FINGERPRINT_STRIP_PATTERN.matcher(f).replaceAll("");
            if (f.length() != SHA1_HEX_LEN) {
                throw new IllegalArgumentException("malformed fingerprint: " + f + " (expected: SHA1)");
            }

            list.add(StringUtil.decodeHexDump(f));
        }

        return list.toArray(new byte[0][]);
    }

    @Override
    protected void engineInit(KeyStore keyStore) throws Exception { }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws Exception { }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return new TrustManager[] { tm };
    }
}
