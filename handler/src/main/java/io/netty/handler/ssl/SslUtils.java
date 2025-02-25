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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;
import io.netty.util.NetUtil;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;

import static java.util.Arrays.asList;

/**
 * Constants for SSL packets.
 */
final class SslUtils {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SslUtils.class);

    // See https://tools.ietf.org/html/rfc8446#appendix-B.4
    static final Set<String> TLSV13_CIPHERS = Collections.unmodifiableSet(new LinkedHashSet<String>(
            asList("TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256",
                          "TLS_AES_128_GCM_SHA256", "TLS_AES_128_CCM_8_SHA256",
                          "TLS_AES_128_CCM_SHA256")));

    static final short DTLS_1_0 = (short) 0xFEFF;
    static final short DTLS_1_2 = (short) 0xFEFD;
    static final short DTLS_1_3 = (short) 0xFEFC;
    static final short DTLS_RECORD_HEADER_LENGTH = 13;

    private static final String DEFAULT_ENDPOINT_VERIFICATION_ALGORITHM_PROPERTY =
            "io.netty.handler.ssl.defaultEndpointVerificationAlgorithm";
    /**
     * Endpoint verification is enabled by default from Netty 4.2 onward, but it wasn't in Netty 4.1 and earlier.
     * The {@value #DEFAULT_ENDPOINT_VERIFICATION_ALGORITHM_PROPERTY} can be set to one of the following
     * values to control this behavior:
     * <ul>
     *     <li>{@code "HTTPS"} — verify subject by DNS hostnames; this is the Netty 4.2 default.</li>
     *     <li>{@code "LDAP"} — verify subject by LDAP identity.</li>
     *     <li>{@code "NONE"} — don't enable endpoint verification by default; this is the Netty 4.1 behavior.</li>
     * </ul>
     */
    static final String defaultEndpointVerificationAlgorithm;

    /**
     * GMSSL Protocol Version
     */
    static final int GMSSL_PROTOCOL_VERSION = 0x101;

    static final String INVALID_CIPHER = "SSL_NULL_WITH_NULL_NULL";

    /**
     * change cipher spec
     */
    static final int SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC = 20;

    /**
     * alert
     */
    static final int SSL_CONTENT_TYPE_ALERT = 21;

    /**
     * handshake
     */
    static final int SSL_CONTENT_TYPE_HANDSHAKE = 22;

    /**
     * application data
     */
    static final int SSL_CONTENT_TYPE_APPLICATION_DATA = 23;

    /**
     * HeartBeat Extension
     */
    static final int SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT = 24;

    /**
     * the length of the ssl record header (in bytes)
     */
    static final int SSL_RECORD_HEADER_LENGTH = 5;

    /**
     * Not enough data in buffer to parse the record length
     */
    static final int NOT_ENOUGH_DATA = -1;

    /**
     * data is not encrypted
     */
    static final int NOT_ENCRYPTED = -2;

    static final String[] DEFAULT_CIPHER_SUITES;
    static final String[] DEFAULT_TLSV13_CIPHER_SUITES;
    static final String[] TLSV13_CIPHER_SUITES = { "TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384" };

    // self-signed certificate for netty.io and the matching private-key
    static final String PROBING_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIICrjCCAZagAwIBAgIIdSvQPv1QAZQwDQYJKoZIhvcNAQELBQAwFjEUMBIGA1UEAxMLZXhhbXBs\n" +
            "ZS5jb20wIBcNMTgwNDA2MjIwNjU5WhgPOTk5OTEyMzEyMzU5NTlaMBYxFDASBgNVBAMTC2V4YW1w\n" +
            "bGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAggbWsmDQ6zNzRZ5AW8E3eoGl\n" +
            "qWvOBDb5Fs1oBRrVQHuYmVAoaqwDzXYJ0LOwa293AgWEQ1jpcbZ2hpoYQzqEZBTLnFhMrhRFlH6K\n" +
            "bJND8Y33kZ/iSVBBDuGbdSbJShlM+4WwQ9IAso4MZ4vW3S1iv5fGGpLgbtXRmBf/RU8omN0Gijlv\n" +
            "WlLWHWijLN8xQtySFuBQ7ssW8RcKAary3pUm6UUQB+Co6lnfti0Tzag8PgjhAJq2Z3wbsGRnP2YS\n" +
            "vYoaK6qzmHXRYlp/PxrjBAZAmkLJs4YTm/XFF+fkeYx4i9zqHbyone5yerRibsHaXZWLnUL+rFoe\n" +
            "MdKvr0VS3sGmhQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQADQi441pKmXf9FvUV5EHU4v8nJT9Iq\n" +
            "yqwsKwXnr7AsUlDGHBD7jGrjAXnG5rGxuNKBQ35wRxJATKrUtyaquFUL6H8O6aGQehiFTk6zmPbe\n" +
            "12Gu44vqqTgIUxnv3JQJiox8S2hMxsSddpeCmSdvmalvD6WG4NthH6B9ZaBEiep1+0s0RUaBYn73\n" +
            "I7CCUaAtbjfR6pcJjrFk5ei7uwdQZFSJtkP2z8r7zfeANJddAKFlkaMWn7u+OIVuB4XPooWicObk\n" +
            "NAHFtP65bocUYnDpTVdiyvn8DdqyZ/EO8n1bBKBzuSLplk2msW4pdgaFgY7Vw/0wzcFXfUXmL1uy\n" +
            "G8sQD/wx\n" +
            "-----END CERTIFICATE-----";
    static final String PROBING_KEY = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCCBtayYNDrM3NFnkBbwTd6gaWp\n" +
            "a84ENvkWzWgFGtVAe5iZUChqrAPNdgnQs7Brb3cCBYRDWOlxtnaGmhhDOoRkFMucWEyuFEWUfops\n" +
            "k0PxjfeRn+JJUEEO4Zt1JslKGUz7hbBD0gCyjgxni9bdLWK/l8YakuBu1dGYF/9FTyiY3QaKOW9a\n" +
            "UtYdaKMs3zFC3JIW4FDuyxbxFwoBqvLelSbpRRAH4KjqWd+2LRPNqDw+COEAmrZnfBuwZGc/ZhK9\n" +
            "ihorqrOYddFiWn8/GuMEBkCaQsmzhhOb9cUX5+R5jHiL3OodvKid7nJ6tGJuwdpdlYudQv6sWh4x\n" +
            "0q+vRVLewaaFAgMBAAECggEAP8tPJvFtTxhNJAkCloHz0D0vpDHqQBMgntlkgayqmBqLwhyb18pR\n" +
            "i0qwgh7HHc7wWqOOQuSqlEnrWRrdcI6TSe8R/sErzfTQNoznKWIPYcI/hskk4sdnQ//Yn9/Jvnsv\n" +
            "U/BBjOTJxtD+sQbhAl80JcA3R+5sArURQkfzzHOL/YMqzAsn5hTzp7HZCxUqBk3KaHRxV7NefeOE\n" +
            "xlZuWSmxYWfbFIs4kx19/1t7h8CHQWezw+G60G2VBtSBBxDnhBWvqG6R/wpzJ3nEhPLLY9T+XIHe\n" +
            "ipzdMOOOUZorfIg7M+pyYPji+ZIZxIpY5OjrOzXHciAjRtr5Y7l99K1CG1LguQKBgQDrQfIMxxtZ\n" +
            "vxU/1cRmUV9l7pt5bjV5R6byXq178LxPKVYNjdZ840Q0/OpZEVqaT1xKVi35ohP1QfNjxPLlHD+K\n" +
            "iDAR9z6zkwjIrbwPCnb5kuXy4lpwPcmmmkva25fI7qlpHtbcuQdoBdCfr/KkKaUCMPyY89LCXgEw\n" +
            "5KTDj64UywKBgQCNfbO+eZLGzhiHhtNJurresCsIGWlInv322gL8CSfBMYl6eNfUTZvUDdFhPISL\n" +
            "UljKWzXDrjw0ujFSPR0XhUGtiq89H+HUTuPPYv25gVXO+HTgBFZEPl4PpA+BUsSVZy0NddneyqLk\n" +
            "42Wey9omY9Q8WsdNQS5cbUvy0uG6WFoX7wKBgQDZ1jpW8pa0x2bZsQsm4vo+3G5CRnZlUp+XlWt2\n" +
            "dDcp5dC0xD1zbs1dc0NcLeGDOTDv9FSl7hok42iHXXq8AygjEm/QcuwwQ1nC2HxmQP5holAiUs4D\n" +
            "WHM8PWs3wFYPzE459EBoKTxeaeP/uWAn+he8q7d5uWvSZlEcANs/6e77eQKBgD21Ar0hfFfj7mK8\n" +
            "9E0FeRZBsqK3omkfnhcYgZC11Xa2SgT1yvs2Va2n0RcdM5kncr3eBZav2GYOhhAdwyBM55XuE/sO\n" +
            "eokDVutNeuZ6d5fqV96TRaRBpvgfTvvRwxZ9hvKF4Vz+9wfn/JvCwANaKmegF6ejs7pvmF3whq2k\n" +
            "drZVAoGAX5YxQ5XMTD0QbMAl7/6qp6S58xNoVdfCkmkj1ZLKaHKIjS/benkKGlySVQVPexPfnkZx\n" +
            "p/Vv9yyphBoudiTBS9Uog66ueLYZqpgxlM/6OhYg86Gm3U2ycvMxYjBM1NFiyze21AqAhI+HX+Ot\n" +
            "mraV2/guSgDgZAhukRZzeQ2RucI=\n" +
            "-----END PRIVATE KEY-----";

    private static final boolean TLSV1_3_JDK_SUPPORTED;
    private static final boolean TLSV1_3_JDK_DEFAULT_ENABLED;

    static {
        TLSV1_3_JDK_SUPPORTED = isTLSv13SupportedByJDK0(null);
        TLSV1_3_JDK_DEFAULT_ENABLED = isTLSv13EnabledByJDK0(null);
        if (TLSV1_3_JDK_SUPPORTED) {
            DEFAULT_TLSV13_CIPHER_SUITES = TLSV13_CIPHER_SUITES;
        } else {
            DEFAULT_TLSV13_CIPHER_SUITES = EmptyArrays.EMPTY_STRINGS;
        }

        Set<String> defaultCiphers = new LinkedHashSet<String>();
        // GCM (Galois/Counter Mode) requires JDK 8.
        defaultCiphers.add("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384");
        defaultCiphers.add("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256");
        defaultCiphers.add("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        defaultCiphers.add("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
        defaultCiphers.add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA");
        // AES256 requires JCE unlimited strength jurisdiction policy files.
        defaultCiphers.add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA");
        // GCM (Galois/Counter Mode) requires JDK 8.
        defaultCiphers.add("TLS_RSA_WITH_AES_128_GCM_SHA256");
        defaultCiphers.add("TLS_RSA_WITH_AES_128_CBC_SHA");
        // AES256 requires JCE unlimited strength jurisdiction policy files.
        defaultCiphers.add("TLS_RSA_WITH_AES_256_CBC_SHA");

        Collections.addAll(defaultCiphers, DEFAULT_TLSV13_CIPHER_SUITES);

        DEFAULT_CIPHER_SUITES = defaultCiphers.toArray(EmptyArrays.EMPTY_STRINGS);

        String defaultEndpointVerification = SystemPropertyUtil.get(DEFAULT_ENDPOINT_VERIFICATION_ALGORITHM_PROPERTY);
        if ("LDAP".equalsIgnoreCase(defaultEndpointVerification)) {
            defaultEndpointVerificationAlgorithm = "LDAP";
        } else if ("NONE".equalsIgnoreCase(defaultEndpointVerification)) {
            logger.info("Default SSL endpoint verification has been disabled:  -D{}=\"{}\"",
                    DEFAULT_ENDPOINT_VERIFICATION_ALGORITHM_PROPERTY, defaultEndpointVerification);
            defaultEndpointVerificationAlgorithm = null;
        } else {
            if (defaultEndpointVerification != null && !"HTTPS".equalsIgnoreCase(defaultEndpointVerification)) {
                logger.warn("Unknown default SSL endpoint verification algorithm: -D{}=\"{}\", " +
                                "will use \"HTTPS\" instead.",
                        DEFAULT_ENDPOINT_VERIFICATION_ALGORITHM_PROPERTY, defaultEndpointVerification);
            }
            defaultEndpointVerificationAlgorithm = "HTTPS";
        }
    }

    /**
     * Returns {@code true} if the JDK itself supports TLSv1.3, {@code false} otherwise.
     */
    static boolean isTLSv13SupportedByJDK(Provider provider) {
        if (provider == null) {
            return TLSV1_3_JDK_SUPPORTED;
        }
        return isTLSv13SupportedByJDK0(provider);
    }

    private static boolean isTLSv13SupportedByJDK0(Provider provider) {
        try {
            return arrayContains(newInitContext(provider)
                    .getSupportedSSLParameters().getProtocols(), SslProtocols.TLS_v1_3);
        } catch (Throwable cause) {
            logger.debug("Unable to detect if JDK SSLEngine with provider {} supports TLSv1.3, assuming no",
                    provider, cause);
            return false;
        }
    }

    /**
     * Returns {@code true} if the JDK itself supports TLSv1.3 and enabled it by default, {@code false} otherwise.
     */
    static boolean isTLSv13EnabledByJDK(Provider provider) {
        if (provider == null) {
            return TLSV1_3_JDK_DEFAULT_ENABLED;
        }
        return isTLSv13EnabledByJDK0(provider);
    }

    private static boolean isTLSv13EnabledByJDK0(Provider provider) {
        try {
            return arrayContains(newInitContext(provider)
                    .getDefaultSSLParameters().getProtocols(), SslProtocols.TLS_v1_3);
        } catch (Throwable cause) {
            logger.debug("Unable to detect if JDK SSLEngine with provider {} enables TLSv1.3 by default," +
                    " assuming no", provider, cause);
            return false;
        }
    }

    private static SSLContext newInitContext(Provider provider)
            throws NoSuchAlgorithmException, KeyManagementException {
        final SSLContext context;
        if (provider == null) {
            context = SSLContext.getInstance("TLS");
        } else {
            context = SSLContext.getInstance("TLS", provider);
        }
        context.init(null, new TrustManager[0], null);
        return context;
    }

    static SSLContext getSSLContext(String provider)
            throws NoSuchAlgorithmException, KeyManagementException, NoSuchProviderException {
        final SSLContext context;
        if (StringUtil.isNullOrEmpty(provider)) {
            context = SSLContext.getInstance(getTlsVersion());
        } else {
            context = SSLContext.getInstance(getTlsVersion(), provider);
        }
        context.init(null, new TrustManager[0], null);
        return context;
    }

    private static String getTlsVersion() {
        return TLSV1_3_JDK_SUPPORTED ? SslProtocols.TLS_v1_3 : SslProtocols.TLS_v1_2;
    }

    static boolean arrayContains(String[] array, String value) {
        for (String v: array) {
            if (value.equals(v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add elements from {@code names} into {@code enabled} if they are in {@code supported}.
     */
    static void addIfSupported(Set<String> supported, List<String> enabled, String... names) {
        for (String n: names) {
            if (supported.contains(n)) {
                enabled.add(n);
            }
        }
    }

    static void useFallbackCiphersIfDefaultIsEmpty(List<String> defaultCiphers, Iterable<String> fallbackCiphers) {
        if (defaultCiphers.isEmpty()) {
            for (String cipher : fallbackCiphers) {
                if (cipher.startsWith("SSL_") || cipher.contains("_RC4_")) {
                    continue;
                }
                defaultCiphers.add(cipher);
            }
        }
    }

    static void useFallbackCiphersIfDefaultIsEmpty(List<String> defaultCiphers, String... fallbackCiphers) {
        useFallbackCiphersIfDefaultIsEmpty(defaultCiphers, asList(fallbackCiphers));
    }

    /**
     * Converts the given exception to a {@link SSLHandshakeException}, if it isn't already.
     */
    static SSLHandshakeException toSSLHandshakeException(Throwable e) {
        if (e instanceof SSLHandshakeException) {
            return (SSLHandshakeException) e;
        }

        return (SSLHandshakeException) new SSLHandshakeException(e.getMessage()).initCause(e);
    }

    /**
     * Return how much bytes can be read out of the encrypted data. Be aware that this method will not increase
     * the readerIndex of the given {@link ByteBuf}.
     *
     * @param   buffer      The {@link ByteBuf} to read from.
     * @param   offset      The offset to start from.
     * @param   probeSSLv2  {@code true} if the input {@code buffer} might be SSLv2.
     * @return              The length of the encrypted packet that is included in the buffer or
     *                      {@link #SslUtils#NOT_ENOUGH_DATA} if not enough data is present in the
     *                      {@link ByteBuf}. This will return {@link SslUtils#NOT_ENCRYPTED} if
     *                      the given {@link ByteBuf} is not encrypted at all.
     */
    static int getEncryptedPacketLength(ByteBuf buffer, int offset, boolean probeSSLv2) {
        assert offset >= buffer.readerIndex();
        int remaining = buffer.writerIndex() - offset;
        if (remaining < SSL_RECORD_HEADER_LENGTH) {
            return NOT_ENOUGH_DATA;
        }
        int packetLength = 0;
        // SSLv3 or TLS - Check ContentType
        boolean tls;
        switch (buffer.getUnsignedByte(offset)) {
            case SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
            case SSL_CONTENT_TYPE_ALERT:
            case SSL_CONTENT_TYPE_HANDSHAKE:
            case SSL_CONTENT_TYPE_APPLICATION_DATA:
            case SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT:
                tls = true;
                break;
            default:
                // SSLv2 or bad data
                if (!probeSSLv2) {
                    return NOT_ENCRYPTED;
                }
                tls = false;
        }

        if (tls) {
            // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1 - Check ProtocolVersion
            int majorVersion = buffer.getUnsignedByte(offset + 1);
            int version = buffer.getShort(offset + 1);
            if (majorVersion == 3 || version == GMSSL_PROTOCOL_VERSION) {
                // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1
                packetLength = unsignedShortBE(buffer, offset + 3) + SSL_RECORD_HEADER_LENGTH;
                if (packetLength <= SSL_RECORD_HEADER_LENGTH) {
                    // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                    tls = false;
                }
            } else if (version == DTLS_1_0 || version == DTLS_1_2 || version == DTLS_1_3) {
                if (remaining < DTLS_RECORD_HEADER_LENGTH) {
                    return NOT_ENOUGH_DATA;
                }
                // length is the last 2 bytes in the 13 byte header.
                packetLength = unsignedShortBE(buffer, offset + DTLS_RECORD_HEADER_LENGTH - 2) +
                        DTLS_RECORD_HEADER_LENGTH;
            } else {
                // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                tls = false;
            }
        }

        if (!tls) {
            // SSLv2 or bad data - Check the version
            int headerLength = (buffer.getUnsignedByte(offset) & 0x80) != 0 ? 2 : 3;
            int majorVersion = buffer.getUnsignedByte(offset + headerLength + 1);
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                packetLength = headerLength == 2 ?
                        (shortBE(buffer, offset) & 0x7FFF) + 2 : (shortBE(buffer, offset) & 0x3FFF) + 3;
                if (packetLength <= headerLength) {
                    // If there's no data then consider this package as not encrypted.
                    return NOT_ENCRYPTED;
                }
            } else {
                return NOT_ENCRYPTED;
            }
        }
        return packetLength;
    }

    // Reads a big-endian unsigned short integer from the buffer
    @SuppressWarnings("deprecation")
    private static int unsignedShortBE(ByteBuf buffer, int offset) {
        int value = buffer.getUnsignedShort(offset);
        if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
            value = Integer.reverseBytes(value) >>> Short.SIZE;
        }
        return value;
    }

    // Reads a big-endian short integer from the buffer
    @SuppressWarnings("deprecation")
    private static short shortBE(ByteBuf buffer, int offset) {
        short value = buffer.getShort(offset);
        if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
            value = Short.reverseBytes(value);
        }
        return value;
    }

    private static short unsignedByte(byte b) {
        return (short) (b & 0xFF);
    }

    // Reads a big-endian unsigned short integer from the buffer
    private static int unsignedShortBE(ByteBuffer buffer, int offset) {
        return shortBE(buffer, offset) & 0xFFFF;
    }

    // Reads a big-endian short integer from the buffer
    private static short shortBE(ByteBuffer buffer, int offset) {
        return buffer.order() == ByteOrder.BIG_ENDIAN ?
                buffer.getShort(offset) : ByteBufUtil.swapShort(buffer.getShort(offset));
    }

    static int getEncryptedPacketLength(ByteBuffer[] buffers, int offset) {
        ByteBuffer buffer = buffers[offset];

        // Check if everything we need is in one ByteBuffer. If so we can make use of the fast-path.
        if (buffer.remaining() >= SSL_RECORD_HEADER_LENGTH) {
            return getEncryptedPacketLength(buffer);
        }

        // We need to copy 5 bytes into a temporary buffer so we can parse out the packet length easily.
        ByteBuffer tmp = ByteBuffer.allocate(SSL_RECORD_HEADER_LENGTH);

        do {
            buffer = buffers[offset++].duplicate();
            if (buffer.remaining() > tmp.remaining()) {
                buffer.limit(buffer.position() + tmp.remaining());
            }
            tmp.put(buffer);
        } while (tmp.hasRemaining() && offset < buffers.length);

        // Done, flip the buffer so we can read from it.
        tmp.flip();
        return getEncryptedPacketLength(tmp);
    }

    private static int getEncryptedPacketLength(ByteBuffer buffer) {
        int remaining = buffer.remaining();
        if (remaining < SSL_RECORD_HEADER_LENGTH) {
            return NOT_ENOUGH_DATA;
        }
        int packetLength = 0;
        int pos = buffer.position();

        // SSLv3 or TLS - Check ContentType
        boolean tls;
        switch (unsignedByte(buffer.get(pos))) {
            case SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
            case SSL_CONTENT_TYPE_ALERT:
            case SSL_CONTENT_TYPE_HANDSHAKE:
            case SSL_CONTENT_TYPE_APPLICATION_DATA:
            case SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT:
                tls = true;
                break;
            default:
                // SSLv2 or bad data
                tls = false;
        }

        if (tls) {
            // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1 - Check ProtocolVersion
            int majorVersion = unsignedByte(buffer.get(pos + 1));
            if (majorVersion == 3 || buffer.getShort(pos + 1) == GMSSL_PROTOCOL_VERSION) {
                // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1
                packetLength = unsignedShortBE(buffer, pos + 3) + SSL_RECORD_HEADER_LENGTH;
                if (packetLength <= SSL_RECORD_HEADER_LENGTH) {
                    // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                    tls = false;
                }
            } else {
                // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                tls = false;
            }
        }

        if (!tls) {
            // SSLv2 or bad data - Check the version
            int headerLength = (unsignedByte(buffer.get(pos)) & 0x80) != 0 ? 2 : 3;
            int majorVersion = unsignedByte(buffer.get(pos + headerLength + 1));
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                packetLength = headerLength == 2 ?
                        (shortBE(buffer, pos) & 0x7FFF) + 2 : (shortBE(buffer, pos) & 0x3FFF) + 3;
                if (packetLength <= headerLength) {
                    // If there's no data then consider this package as not encrypted.
                    return NOT_ENCRYPTED;
                }
            } else {
                return NOT_ENCRYPTED;
            }
        }
        return packetLength;
    }

    static void handleHandshakeFailure(ChannelHandlerContext ctx, Throwable cause, boolean notify) {
        // We have may haven written some parts of data before an exception was thrown so ensure we always flush.
        // See https://github.com/netty/netty/issues/3900#issuecomment-172481830
        ctx.flush();
        if (notify) {
            ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
        }
        ctx.close();
    }

    /**
     * Fills the {@link ByteBuf} with zero bytes.
     */
    static void zeroout(ByteBuf buffer) {
        if (!buffer.isReadOnly()) {
            buffer.setZero(0, buffer.capacity());
        }
    }

    /**
     * Fills the {@link ByteBuf} with zero bytes and releases it.
     */
    static void zerooutAndRelease(ByteBuf buffer) {
        zeroout(buffer);
        buffer.release();
    }

    /**
     * Same as {@link Base64#encode(ByteBuf, boolean)} but allows the use of a custom {@link ByteBufAllocator}.
     *
     * @see Base64#encode(ByteBuf, boolean)
     */
    static ByteBuf toBase64(ByteBufAllocator allocator, ByteBuf src) {
        ByteBuf dst = Base64.encode(src, src.readerIndex(),
                src.readableBytes(), true, Base64Dialect.STANDARD, allocator);
        src.readerIndex(src.writerIndex());
        return dst;
    }

    /**
     * Validate that the given hostname can be used in SNI extension.
     */
    static boolean isValidHostNameForSNI(String hostname) {
        // See  https://datatracker.ietf.org/doc/html/rfc6066#section-3
        return hostname != null &&
                // SNI HostName has to be a FQDN according to TLS SNI Extension spec (see [1]),
                // which means that is has to have at least a host name and a domain part.
                hostname.indexOf('.') > 0 &&
                !hostname.endsWith(".") && !hostname.startsWith("/") &&
                !NetUtil.isValidIpV4Address(hostname) &&
                !NetUtil.isValidIpV6Address(hostname);
    }

    /**
     * Returns {@code true} if the given cipher (in openssl format) is for TLSv1.3, {@code false} otherwise.
     */
    static boolean isTLSv13Cipher(String cipher) {
        // See https://tools.ietf.org/html/rfc8446#appendix-B.4
        return TLSV13_CIPHERS.contains(cipher);
    }

    private SslUtils() {
    }
}
