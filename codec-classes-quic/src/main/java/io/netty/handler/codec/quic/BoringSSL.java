/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.handler.ssl.util.LazyX509Certificate;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

final class BoringSSL {

    static final int SSL_VERIFY_NONE = BoringSSLNativeStaticallyReferencedJniMethods.ssl_verify_none();
    static final int SSL_VERIFY_FAIL_IF_NO_PEER_CERT = BoringSSLNativeStaticallyReferencedJniMethods
            .ssl_verify_fail_if_no_peer_cert();
    static final int SSL_VERIFY_PEER = BoringSSLNativeStaticallyReferencedJniMethods.ssl_verify_peer();
    static final int X509_V_OK = BoringSSLNativeStaticallyReferencedJniMethods.x509_v_ok();
    static final int X509_V_ERR_CERT_HAS_EXPIRED =
            BoringSSLNativeStaticallyReferencedJniMethods.x509_v_err_cert_has_expired();
    static final int X509_V_ERR_CERT_NOT_YET_VALID =
            BoringSSLNativeStaticallyReferencedJniMethods.x509_v_err_cert_not_yet_valid();
    static final int X509_V_ERR_CERT_REVOKED = BoringSSLNativeStaticallyReferencedJniMethods.x509_v_err_cert_revoked();
    static final int X509_V_ERR_UNSPECIFIED = BoringSSLNativeStaticallyReferencedJniMethods.x509_v_err_unspecified();

    static long SSLContext_new(boolean server, String[] applicationProtocols,
                               BoringSSLHandshakeCompleteCallback handshakeCompleteCallback,
                               BoringSSLCertificateCallback certificateCallback,
                               BoringSSLCertificateVerifyCallback verifyCallback,
                               @Nullable BoringSSLTlsextServernameCallback servernameCallback,
                               @Nullable BoringSSLKeylogCallback keylogCallback,
                               @Nullable BoringSSLSessionCallback sessionCallback,
                               @Nullable BoringSSLPrivateKeyMethod privateKeyMethod,
                               BoringSSLSessionTicketCallback sessionTicketCallback,
                               int verifyMode,
                               byte[][] subjectNames) {
        return SSLContext_new0(server, toWireFormat(applicationProtocols),
                handshakeCompleteCallback, certificateCallback, verifyCallback, servernameCallback,
                keylogCallback, sessionCallback, privateKeyMethod, sessionTicketCallback, verifyMode, subjectNames);
    }

    private static byte @Nullable [] toWireFormat(String @Nullable [] applicationProtocols) {
        if (applicationProtocols == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : applicationProtocols) {
                byte[] bytes = p.getBytes(StandardCharsets.US_ASCII);
                out.write(bytes.length);
                out.write(bytes);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static native long SSLContext_new();

    private static native long SSLContext_new0(boolean server,
                                               byte @Nullable [] applicationProtocols, Object handshakeCompleteCallback,
                                               Object certificateCallback, Object verifyCallback,
                                               @Nullable Object servernameCallback, @Nullable Object keylogCallback,
                                               @Nullable Object sessionCallback,
                                               @Nullable Object privateKeyMethod,
                                               Object sessionTicketCallback,
                                               int verifyDepth, byte[][] subjectNames);
    static native void SSLContext_set_early_data_enabled(long context, boolean enabled);
    static native long SSLContext_setSessionCacheSize(long context, long size);
    static native long SSLContext_setSessionCacheTimeout(long context, long size);

    static native void SSLContext_setSessionTicketKeys(long context, boolean enableCallback);

    static int SSLContext_set1_groups_list(long ctx, String... groups) {
        if (groups == null) {
            throw new NullPointerException("curves");
        }
        if (groups.length == 0) {
            throw new IllegalArgumentException();
        }
        StringBuilder sb = new StringBuilder();
        for (String group: groups) {
            sb.append(group);
            // Groups are separated by : as explained in the manpage.
            sb.append(':');
        }
        sb.setLength(sb.length() - 1);
        return SSLContext_set1_groups_list(ctx, sb.toString());
    }

    static int SSLContext_set1_sigalgs_list(long ctx, String... sigalgs) {
        if (sigalgs.length == 0) {
            throw new IllegalArgumentException();
        }
        StringBuilder sb = new StringBuilder();
        for (String sigalg: sigalgs) {
            sb.append(sigalg);
            // Groups are separated by : as explained in the manpage.
            sb.append(':');
        }
        sb.setLength(sb.length() - 1);
        return SSLContext_set1_sigalgs_list(ctx, sb.toString());
    }

    private static native int SSLContext_set1_sigalgs_list(long context, String sigalgs);

    private static native int SSLContext_set1_groups_list(long context, String groups);
    static native void SSLContext_free(long context);
    static long SSL_new(long context, boolean server, String hostname) {
        return SSL_new0(context, server, tlsExtHostName(hostname));
    }
    static native long SSL_new0(long context, boolean server, @Nullable String hostname);
    static native void SSL_free(long ssl);

    static native Runnable SSL_getTask(long ssl);

    static native void SSL_cleanup(long ssl);

    static native long EVP_PKEY_parse(byte[] bytes, String pass);
    static native void EVP_PKEY_free(long key);

    static native long CRYPTO_BUFFER_stack_new(long ssl, byte[][] bytes);
    static native void CRYPTO_BUFFER_stack_free(long chain);

    @Nullable
    static native String ERR_last_error();

    @Nullable
    private static String tlsExtHostName(@Nullable String hostname) {
        if (hostname != null && hostname.endsWith(".")) {
            // Strip trailing dot if included.
            // See https://github.com/netty/netty-tcnative/issues/400
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        return hostname;
    }

    static X509Certificate[] certificates(byte[][] chain) {
        X509Certificate[] peerCerts = new X509Certificate[chain.length];
        for (int i = 0; i < peerCerts.length; i++) {
            peerCerts[i] = new LazyX509Certificate(chain[i]);
        }
        return peerCerts;
    }

    static byte[][] subjectNames(X509Certificate[] certificates) {
        byte[][] subjectNames = new byte[certificates.length][];
        for (int i = 0; i < certificates.length; i++) {
            subjectNames[i] = certificates[i].getSubjectX500Principal().getEncoded();
        }
        return subjectNames;
    }

    private BoringSSL() { }
}
