/*
 * Copyright 2023 The Netty Project
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

import io.netty.util.internal.EmptyArrays;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.net.Socket;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.x500.X500Principal;

import static io.netty.handler.ssl.EnhancingX509ExtendedTrustManager.ALTNAME_DNS;
import static io.netty.handler.ssl.EnhancingX509ExtendedTrustManager.ALTNAME_IP;
import static io.netty.handler.ssl.EnhancingX509ExtendedTrustManager.ALTNAME_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class EnhancedX509ExtendedTrustManagerTest {

    private static final String HOSTNAME = "netty.io";
    private static final String SAN_ENTRY_DNS = "some.netty.io";
    private static final String SAN_ENTRY_IP = "127.0.0.1";
    private static final String SAN_ENTRY_URI = "URI:https://uri.netty.io/profile";
    private static final String SAN_ENTRY_RFC822 = "info@netty.io";
    private static final String COMMON_NAME = "leaf.netty.io";

    private static final X509Certificate TEST_CERT = new X509Certificate() {

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() {
            return Arrays.asList(Arrays.asList(1, new Object()),
                    Arrays.asList(ALTNAME_DNS, SAN_ENTRY_DNS), Arrays.asList(ALTNAME_IP, SAN_ENTRY_IP),
                    Arrays.asList(ALTNAME_URI, SAN_ENTRY_URI), Arrays.asList(1 /* rfc822Name */, SAN_ENTRY_RFC822));
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return new X500Principal("CN=" + COMMON_NAME + ", O=Netty");
        }

        @Override
        public void checkValidity() {
            // NOOP
        }

        @Override
        public void checkValidity(Date date) {
            // NOOP
        }

        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public BigInteger getSerialNumber() {
            return null;
        }

        @Override
        public Principal getIssuerDN() {
            return null;
        }

        @Override
        public Principal getSubjectDN() {
            return null;
        }

        @Override
        public Date getNotBefore() {
            return null;
        }

        @Override
        public Date getNotAfter() {
            return null;
        }

        @Override
        public byte[] getTBSCertificate() {
            return EmptyArrays.EMPTY_BYTES;
        }

        @Override
        public byte[] getSignature() {
            return EmptyArrays.EMPTY_BYTES;
        }

        @Override
        public String getSigAlgName() {
            return null;
        }

        @Override
        public String getSigAlgOID() {
            return null;
        }

        @Override
        public byte[] getSigAlgParams() {
            return EmptyArrays.EMPTY_BYTES;
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return new boolean[0];
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return new boolean[0];
        }

        @Override
        public boolean[] getKeyUsage() {
            return new boolean[0];
        }

        @Override
        public int getBasicConstraints() {
            return 0;
        }

        @Override
        public byte[] getEncoded() {
            return EmptyArrays.EMPTY_BYTES;
        }

        @Override
        public void verify(PublicKey key) {
            // NOOP
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
            // NOOP
        }

        @Override
        public String toString() {
            return null;
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return EmptyArrays.EMPTY_BYTES;
        }
    };

    private static final EnhancingX509ExtendedTrustManager MATCHING_MANAGER =
            new EnhancingX509ExtendedTrustManager(new X509ExtendedTrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            fail();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            throw newCertificateExceptionWithMatchingMessage();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            fail();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            throw newCertificateExceptionWithMatchingMessage();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            fail();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            throw newCertificateExceptionWithMatchingMessage();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        private CertificateException newCertificateExceptionWithMatchingMessage() {
            return new CertificateException("No subject alternative DNS name matching " + HOSTNAME + " found.");
        }
    });

    static List<Arguments> throwingMatchingExecutables() {
        return Arrays.asList(arguments(named("checkServerTrusted", new Executable() {
            @Override
            public void execute() throws Throwable {
                MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null);
            }
        })), arguments(named("checkServerTrusted with SSLEngine", new Executable() {
            @Override
            public void execute() throws Throwable {
                SSLSession session = mockSSLSession();
                SSLEngine engine = Mockito.mock(SSLEngine.class);
                Mockito.when(engine.getHandshakeSession()).thenReturn(session);
                MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null, engine);
            }
        })), arguments(named("checkServerTrusted with SSLSocket", new Executable() {
            @Override
            public void execute() throws Throwable {
                SSLSession session = mockSSLSession();
                SSLSocket socket = Mockito.mock(SSLSocket.class);
                Mockito.when(socket.getHandshakeSession()).thenReturn(session);
                MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null, socket);
            }
        })));
    }

    private static SSLSession mockSSLSession() {
        ExtendedSSLSession session = Mockito.mock(ExtendedSSLSession.class);
        Mockito.when(session.getRequestedServerNames()).thenReturn(Arrays.asList(new SNIHostName(HOSTNAME)));
        Mockito.when(session.getPeerHost()).thenReturn(HOSTNAME);
        return session;
    }

    private static final EnhancingX509ExtendedTrustManager NON_MATCHING_MANAGER =
            new EnhancingX509ExtendedTrustManager(new X509ExtendedTrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                        throws CertificateException {
                    fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                        throws CertificateException {
                    throw new CertificateException();
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                        throws CertificateException {
                    fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                        throws CertificateException {
                    throw new CertificateException();
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    fail();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    throw new CertificateException();
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            });

    static List<Arguments> throwingNonMatchingExecutables() {
        return Arrays.asList(arguments(named("checkServerTrusted", new Executable() {
            @Override
            public void execute() throws Throwable {
                NON_MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null);
            }
        })), arguments(named("checkServerTrusted with SSLEngine", new Executable() {
            @Override
            public void execute() throws Throwable {
                NON_MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null, (SSLEngine) null);
            }
        })), arguments(named("checkServerTrusted with SSLSocket", new Executable() {
            @Override
            public void execute() throws Throwable {
                NON_MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null, (SSLSocket) null);
            }
        })));
    }

    @ParameterizedTest
    @MethodSource("throwingMatchingExecutables")
    void testEnhanceException(Executable executable, TestInfo testInfo)  {
        CertificateException exception = assertThrows(CertificateException.class, executable);
        // We should wrap the original cause with our own.
        assertInstanceOf(CertificateException.class, exception.getCause());
        String message = exception.getMessage();
        if (testInfo.getDisplayName().contains("with")) {
            // The following data can be extracted only when we run the test with SSLEngine or SSLSocket:
            assertThat(message).contains("SNIHostName=" + HOSTNAME);
            assertThat(message).contains("peerHost=" + HOSTNAME);
        }
        assertThat(message).contains("DNS:" + SAN_ENTRY_DNS);
        assertThat(message).contains("IP:" + SAN_ENTRY_IP);
        assertThat(message).contains("URI:" + SAN_ENTRY_URI);
        assertThat(message).contains("CN=" + COMMON_NAME);
        assertThat(message).doesNotContain(SAN_ENTRY_RFC822);
    }

    @ParameterizedTest
    @MethodSource("throwingNonMatchingExecutables")
    void testNotEnhanceException(Executable executable) {
        CertificateException exception = assertThrows(CertificateException.class, executable);
        // We should not wrap the original cause with our own.
        assertNull(exception.getCause());
        assertNull(exception.getMessage());
    }
}
