/*
 * Copyright 2018 The Netty Project
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

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Provide a way to use {@code TLSv1.3} with Java versions prior to 11 by adding a
 * <a href="http://mail.openjdk.java.net/pipermail/security-dev/2018-September/018242.html>workaround</a> for the
 * default {@link X509ExtendedTrustManager} implementations provided by the JDK that can not handle a protocol version
 * of {@code TLSv1.3}.
 */
final class OpenSslTlsv13X509ExtendedTrustManager extends X509ExtendedTrustManager {

    private final X509ExtendedTrustManager tm;

    private OpenSslTlsv13X509ExtendedTrustManager(X509ExtendedTrustManager tm) {
        this.tm = tm;
    }

    static X509ExtendedTrustManager wrap(X509ExtendedTrustManager tm, boolean client) {
        if (PlatformDependent.javaVersion() < 11) {
            X509Certificate[] certs = new X509Certificate[1];
            try {
                if (client) {
                    tm.checkServerTrusted(certs, "RSA", new DummySSLEngine(true));
                } else {
                    tm.checkClientTrusted(certs, "RSA", new DummySSLEngine(false));
                }
            } catch (IllegalArgumentException e) {
                // If this happened we failed because our protocol version was not known by the implementation.
                // See http://mail.openjdk.java.net/pipermail/security-dev/2018-September/018242.html.
                return new OpenSslTlsv13X509ExtendedTrustManager(tm);
            } catch (Throwable ignore) {
                // Just assume we do not need to wrap.
            }
        }
        return tm;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
            throws CertificateException {
        tm.checkClientTrusted(x509Certificates, s, socket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
            throws CertificateException {
        tm.checkServerTrusted(x509Certificates, s, socket);
    }

    private static SSLEngine wrapEngine(final SSLEngine engine) {
        final SSLSession session = engine.getHandshakeSession();
        if (session != null && SslUtils.PROTOCOL_TLS_V1_3.equals(session.getProtocol())) {
            return new JdkSslEngine(engine) {
                @Override
                public String getNegotiatedApplicationProtocol() {
                    if (engine instanceof ApplicationProtocolAccessor) {
                        return ((ApplicationProtocolAccessor) engine).getNegotiatedApplicationProtocol();
                    }
                    return super.getNegotiatedApplicationProtocol();
                }

                @Override
                public SSLSession getHandshakeSession() {
                    if (PlatformDependent.javaVersion() >= 7 && session instanceof ExtendedOpenSslSession) {
                        final ExtendedOpenSslSession extendedOpenSslSession = (ExtendedOpenSslSession) session;
                        return new ExtendedOpenSslSession(extendedOpenSslSession) {
                            @Override
                            public List getRequestedServerNames() {
                                return extendedOpenSslSession.getRequestedServerNames();
                            }

                            @Override
                            public String[] getPeerSupportedSignatureAlgorithms() {
                                return extendedOpenSslSession.getPeerSupportedSignatureAlgorithms();
                            }

                            @Override
                            public String getProtocol() {
                                return SslUtils.PROTOCOL_TLS_V1_2;
                            }
                        };
                    } else {
                        return new SSLSession() {
                            @Override
                            public byte[] getId() {
                                return session.getId();
                            }

                            @Override
                            public SSLSessionContext getSessionContext() {
                                return session.getSessionContext();
                            }

                            @Override
                            public long getCreationTime() {
                                return session.getCreationTime();
                            }

                            @Override
                            public long getLastAccessedTime() {
                                return session.getLastAccessedTime();
                            }

                            @Override
                            public void invalidate() {
                                session.invalidate();
                            }

                            @Override
                            public boolean isValid() {
                                return session.isValid();
                            }

                            @Override
                            public void putValue(String s, Object o) {
                                session.putValue(s, o);
                            }

                            @Override
                            public Object getValue(String s) {
                               return session.getValue(s);
                            }

                            @Override
                            public void removeValue(String s) {
                                session.removeValue(s);
                            }

                            @Override
                            public String[] getValueNames() {
                                return session.getValueNames();
                            }

                            @Override
                            public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
                                return session.getPeerCertificates();
                            }

                            @Override
                            public Certificate[] getLocalCertificates() {
                                return session.getLocalCertificates();
                            }

                            @Override
                            public javax.security.cert.X509Certificate[] getPeerCertificateChain()
                                    throws SSLPeerUnverifiedException {
                                return session.getPeerCertificateChain();
                            }

                            @Override
                            public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
                                return session.getPeerPrincipal();
                            }

                            @Override
                            public Principal getLocalPrincipal() {
                                return session.getLocalPrincipal();
                            }

                            @Override
                            public String getCipherSuite() {
                                return session.getCipherSuite();
                            }

                            @Override
                            public String getProtocol() {
                                return SslUtils.PROTOCOL_TLS_V1_2;
                            }

                            @Override
                            public String getPeerHost() {
                                return session.getPeerHost();
                            }

                            @Override
                            public int getPeerPort() {
                                return session.getPeerPort();
                            }

                            @Override
                            public int getPacketBufferSize() {
                                return session.getPacketBufferSize();
                            }

                            @Override
                            public int getApplicationBufferSize() {
                                return session.getApplicationBufferSize();
                            }
                        };
                    }
                }
            };
        }
        return engine;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, final String s, SSLEngine sslEngine)
            throws CertificateException {
        tm.checkClientTrusted(x509Certificates, s, wrapEngine(sslEngine));
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
            throws CertificateException {
        tm.checkServerTrusted(x509Certificates, s, wrapEngine(sslEngine));
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        tm.checkClientTrusted(x509Certificates, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        tm.checkServerTrusted(x509Certificates, s);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return tm.getAcceptedIssuers();
    }

    private static final class DummySSLEngine extends SSLEngine {

        private final boolean client;

        DummySSLEngine(boolean client) {
            this.client = client;
        }

        @Override
        public SSLSession getHandshakeSession() {
            return new SSLSession() {
                @Override
                public byte[] getId() {
                    return EmptyArrays.EMPTY_BYTES;
                }

                @Override
                public SSLSessionContext getSessionContext() {
                    return null;
                }

                @Override
                public long getCreationTime() {
                    return 0;
                }

                @Override
                public long getLastAccessedTime() {
                    return 0;
                }

                @Override
                public void invalidate() {
                    // NOOP
                }

                @Override
                public boolean isValid() {
                    return false;
                }

                @Override
                public void putValue(String s, Object o) {
                    // NOOP
                }

                @Override
                public Object getValue(String s) {
                    return null;
                }

                @Override
                public void removeValue(String s) {
                    // NOOP
                }

                @Override
                public String[] getValueNames() {
                    return EmptyArrays.EMPTY_STRINGS;
                }

                @Override
                public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
                    return EmptyArrays.EMPTY_CERTIFICATES;
                }

                @Override
                public Certificate[] getLocalCertificates() {
                    return EmptyArrays.EMPTY_CERTIFICATES;
                }

                @Override
                public javax.security.cert.X509Certificate[] getPeerCertificateChain()
                        throws SSLPeerUnverifiedException {
                    return EmptyArrays.EMPTY_JAVAX_X509_CERTIFICATES;
                }

                @Override
                public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
                    return null;
                }

                @Override
                public Principal getLocalPrincipal() {
                    return null;
                }

                @Override
                public String getCipherSuite() {
                    return null;
                }

                @Override
                public String getProtocol() {
                    return SslUtils.PROTOCOL_TLS_V1_3;
                }

                @Override
                public String getPeerHost() {
                    return null;
                }

                @Override
                public int getPeerPort() {
                    return 0;
                }

                @Override
                public int getPacketBufferSize() {
                    return 0;
                }

                @Override
                public int getApplicationBufferSize() {
                    return 0;
                }
            };
        }

        @Override
        public SSLEngineResult wrap(ByteBuffer[] byteBuffers, int i, int i1, ByteBuffer byteBuffer)
                            throws SSLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer[] byteBuffers, int i, int i1)
                            throws SSLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Runnable getDelegatedTask() {
            return null;
        }

        @Override
        public void closeInbound() throws SSLException {
            // NOOP
        }

        @Override
        public boolean isInboundDone() {
            return true;
        }

        @Override
        public void closeOutbound() {
            // NOOP
        }

        @Override
        public boolean isOutboundDone() {
            return true;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return EmptyArrays.EMPTY_STRINGS;
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return EmptyArrays.EMPTY_STRINGS;
        }

        @Override
        public void setEnabledCipherSuites(String[] strings) {
            // NOOP
        }

        @Override
        public String[] getSupportedProtocols() {
            return new String[] { SslUtils.PROTOCOL_TLS_V1_3 };
        }

        @Override
        public String[] getEnabledProtocols() {
            return new String[] { SslUtils.PROTOCOL_TLS_V1_3 };
        }

        @Override
        public void setEnabledProtocols(String[] strings) {
            // NOOP
        }

        @Override
        public SSLSession getSession() {
            return getHandshakeSession();
        }

        @Override
        public void beginHandshake() throws SSLException {
            // NOOP
        }

        @Override
        public HandshakeStatus getHandshakeStatus() {
            return HandshakeStatus.NEED_TASK;
        }

        @Override
        public void setUseClientMode(boolean b) {
            // NOOP
        }

        @Override
        public boolean getUseClientMode() {
            return client;
        }

        @Override
        public void setNeedClientAuth(boolean b) {
            // NOOP
        }

        @Override
        public boolean getNeedClientAuth() {
            return false;
        }

        @Override
        public void setWantClientAuth(boolean b) {
            // NOOP
        }

        @Override
        public boolean getWantClientAuth() {
            return false;
        }

        @Override
        public void setEnableSessionCreation(boolean b) {
            // NOOP
        }

        @Override
        public boolean getEnableSessionCreation() {
            return false;
        }
    }
}
