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

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;
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
 * An {@link TrustManagerFactory} that trusts an X.509 certificate whose hash matches.
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
 * The hash of an X.509 certificate is calculated from its DER encoded format.  You can get the fingerprint of
 * an X.509 certificate using the {@code openssl} command.  For example:
 *
 * <pre>
 * $ openssl x509 -fingerprint -sha256 -in my_certificate.crt
 * SHA256 Fingerprint=1C:53:0E:6B:FF:93:F0:DE:C2:E6:E7:9D:10:53:58:FF:DD:8E:68:CD:82:D9:C9:36:9B:43:EE:B3:DC:13:68:FB
 * -----BEGIN CERTIFICATE-----
 * MIIC/jCCAeagAwIBAgIIIMONxElm0AIwDQYJKoZIhvcNAQELBQAwPjE8MDoGA1UE
 * AwwzZThhYzAyZmEwZDY1YTg0MjE5MDE2MDQ1ZGI4YjA1YzQ4NWI0ZWNkZi5uZXR0
 * eS50ZXN0MCAXDTEzMDgwMjA3NTEzNloYDzk5OTkxMjMxMjM1OTU5WjA+MTwwOgYD
 * VQQDDDNlOGFjMDJmYTBkNjVhODQyMTkwMTYwNDVkYjhiMDVjNDg1YjRlY2RmLm5l
 * dHR5LnRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDb+HBO3C0U
 * RBKvDUgJHbhIlBye8X/cbNH3lDq3XOOFBz7L4XZKLDIXS+FeQqSAUMo2otmU+Vkj
 * 0KorshMjbUXfE1KkTijTMJlaga2M2xVVt21fRIkJNWbIL0dWFLWyRq7OXdygyFkI
 * iW9b2/LYaePBgET22kbtHSCAEj+BlSf265+1rNxyAXBGGGccCKzEbcqASBKHOgVp
 * 6pLqlQAfuSy6g/OzGzces3zXRrGu1N3pBIzAIwCW429n52ZlYfYR0nr+REKDnRrP
 * IIDsWASmEHhBezTD+v0qCJRyLz2usFgWY+7agUJE2yHHI2mTu2RAFngBilJXlMCt
 * VwT0xGuQxkbHAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAEv8N7Xm8qaY2FgrOc6P
 * a1GTgA+AOb3aU33TGwAR86f+nLf6BSPaohcQfOeJid7FkFuYInuXl+oqs+RqM/j8
 * R0E5BuGYY2wOKpL/PbFi1yf/Kyvft7KVh8e1IUUec/i1DdYTDB0lNWvXXxjfMKGL
 * ct3GMbEHKvLfHx42Iwz/+fva6LUrO4u2TDfv0ycHuR7UZEuC1DJ4xtFhbpq/QRAj
 * CyfNx3cDc7L2EtJWnCmivTFA9l8MF1ZPMDSVd4ecQ7B0xZIFQ5cSSFt7WGaJCsGM
 * zYkU4Fp4IykQcWxdlNX7wJZRwQ2TZJFFglpTiFZdeq6I6Ad9An1Encpz5W8UJ4tv
 * hmw=
 * -----END CERTIFICATE-----
 * </pre>
 * </p>
 */
public final class FingerprintTrustManagerFactory extends SimpleTrustManagerFactory {

    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");
    private static final Pattern FINGERPRINT_STRIP_PATTERN = Pattern.compile(":");

    /**
     * Creates a builder for {@link FingerprintTrustManagerFactory}.
     *
     * @param algorithm a hash algorithm
     * @return a builder
     */
    public static FingerprintTrustManagerFactoryBuilder builder(String algorithm) {
        return new FingerprintTrustManagerFactoryBuilder(algorithm);
    }

    private final FastThreadLocal<MessageDigest> tlmd;

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
     * @deprecated This deprecated constructor uses SHA-1 that is considered insecure.
     *      It is recommended to specify a stronger hash algorithm, such as SHA-256,
     *      by calling {@link FingerprintTrustManagerFactory#builder(String)} method.
     *
     * @param fingerprints a list of SHA1 fingerprints in hexadecimal form
     */
    @Deprecated
    public FingerprintTrustManagerFactory(Iterable<String> fingerprints) {
        this("SHA1", toFingerprintArray(fingerprints));
    }

    /**
     * Creates a new instance.
     *
     * @deprecated This deprecated constructor uses SHA-1 that is considered insecure.
     *      It is recommended to specify a stronger hash algorithm, such as SHA-256,
     *      by calling {@link FingerprintTrustManagerFactory#builder(String)} method.
     *
     * @param fingerprints a list of SHA1 fingerprints in hexadecimal form
     */
    @Deprecated
    public FingerprintTrustManagerFactory(String... fingerprints) {
        this("SHA1", toFingerprintArray(Arrays.asList(fingerprints)));
    }

    /**
     * Creates a new instance.
     *
     * @deprecated This deprecated constructor uses SHA-1 that is considered insecure.
     *      It is recommended to specify a stronger hash algorithm, such as SHA-256,
     *      by calling {@link FingerprintTrustManagerFactory#builder(String)} method.
     *
     * @param fingerprints a list of SHA1 fingerprints
     */
    @Deprecated
    public FingerprintTrustManagerFactory(byte[]... fingerprints) {
        this("SHA1", fingerprints);
    }

    /**
     * Creates a new instance.
     *
     * @param algorithm a hash algorithm
     * @param fingerprints a list of fingerprints
     */
    FingerprintTrustManagerFactory(final String algorithm, byte[][] fingerprints) {
        ObjectUtil.checkNotNull(algorithm, "algorithm");
        ObjectUtil.checkNotNull(fingerprints, "fingerprints");

        if (fingerprints.length == 0) {
            throw new IllegalArgumentException("No fingerprints provided");
        }

        // check early if the hash algorithm is available
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(
                    String.format("Unsupported hash algorithm: %s", algorithm), e);
        }

        int hashLength = md.getDigestLength();
        List<byte[]> list = new ArrayList<byte[]>(fingerprints.length);
        for (byte[] f: fingerprints) {
            if (f == null) {
                break;
            }
            if (f.length != hashLength) {
                throw new IllegalArgumentException(
                        String.format("malformed fingerprint (length is %d but expected %d): %s",
                                      f.length, hashLength, ByteBufUtil.hexDump(Unpooled.wrappedBuffer(f))));
            }
            list.add(f.clone());
        }

        this.tlmd = new FastThreadLocal<MessageDigest>() {

            @Override
            protected MessageDigest initialValue() {
                try {
                    return MessageDigest.getInstance(algorithm);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalArgumentException(
                            String.format("Unsupported hash algorithm: %s", algorithm), e);
                }
            }
        };

        this.fingerprints = list.toArray(new byte[0][]);
    }

    static byte[][] toFingerprintArray(Iterable<String> fingerprints) {
        ObjectUtil.checkNotNull(fingerprints, "fingerprints");

        List<byte[]> list = new ArrayList<byte[]>();
        for (String f: fingerprints) {
            if (f == null) {
                break;
            }

            if (!FINGERPRINT_PATTERN.matcher(f).matches()) {
                throw new IllegalArgumentException("malformed fingerprint: " + f);
            }
            f = FINGERPRINT_STRIP_PATTERN.matcher(f).replaceAll("");

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
