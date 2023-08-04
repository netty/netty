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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class EnhancedX509ExtendedTrustManagerTest {

    private static final X509Certificate TEST_CERT = new X509Certificate() {

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() {
            return Arrays.asList(Arrays.asList(1, new Object()), Arrays.asList(2, "some.netty.io"));
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
            throw new CertificateException("No subject alternative DNS name matching netty.io.");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            fail();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            throw new CertificateException("No subject alternative DNS name matching netty.io.");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            fail();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            throw new CertificateException("No subject alternative DNS name matching netty.io.");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    });

    static List<Executable> throwingMatchingExecutables() {
        return Arrays.asList(new Executable() {
            @Override
            public void execute() throws Throwable {
                MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null);
            }
        }, new Executable() {
            @Override
            public void execute() throws Throwable {
                MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null, (SSLEngine) null);
            }
        }, new Executable() {
            @Override
            public void execute() throws Throwable {
                MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null, (SSLSocket) null);
            }
        });
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

    static List<Executable> throwingNonMatchingExecutables() {
        return Arrays.asList(new Executable() {
            @Override
            public void execute() throws Throwable {
                NON_MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null);
            }
        }, new Executable() {
            @Override
            public void execute() throws Throwable {
                NON_MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null, (SSLEngine) null);
            }
        }, new Executable() {
            @Override
            public void execute() throws Throwable {
                NON_MATCHING_MANAGER.checkServerTrusted(new X509Certificate[] { TEST_CERT }, null, (SSLSocket) null);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("throwingMatchingExecutables")
    void testEnhanceException(Executable executable)  {
        CertificateException exception = assertThrows(CertificateException.class, executable);
        // We should wrap the original cause with our own.
        assertInstanceOf(CertificateException.class, exception.getCause());
        assertThat(exception.getMessage(), Matchers.containsString("some.netty.io"));
    }

    @ParameterizedTest
    @MethodSource("throwingNonMatchingExecutables")
    void testNotEnhanceException(Executable executable) {
        CertificateException exception = assertThrows(CertificateException.class, executable);
        // We should not wrap the original cause with our own.
        assertNull(exception.getCause());
        assertThat(exception.getMessage(), Matchers.not(Matchers.containsString("some.netty.io")));
    }
}
