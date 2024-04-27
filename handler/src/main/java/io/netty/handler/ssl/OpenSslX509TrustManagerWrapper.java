/*
 * Copyright 2018 The Netty Project
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
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivilegedAction;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Utility which allows to wrap {@link X509TrustManager} implementations with the internal implementation used by
 * {@code SSLContextImpl} that provides extended verification.
 * <p>
 * This is really a "hack" until there is an official API as requested on the in
 * <a href="https://bugs.openjdk.java.net/projects/JDK/issues/JDK-8210843">JDK-8210843</a>.
 */
final class OpenSslX509TrustManagerWrapper {
    private static final InternalLogger LOGGER = InternalLoggerFactory
            .getInstance(OpenSslX509TrustManagerWrapper.class);
    private static final TrustManagerWrapper WRAPPER;

    static {
        // By default we will not do any wrapping but just return the passed in manager.
        TrustManagerWrapper wrapper = new TrustManagerWrapper() {
            @Override
            public X509TrustManager wrapIfNeeded(X509TrustManager manager) {
                return manager;
            }
        };

        Throwable cause = null;
        Throwable unsafeCause = PlatformDependent.getUnsafeUnavailabilityCause();
        if (unsafeCause == null) {
            SSLContext context;
            try {
                context = newSSLContext();
                // Now init with an array that only holds a X509TrustManager. This should be wrapped into an
                // AbstractTrustManagerWrapper which will delegate the TrustManager itself but also do extra
                // validations.
                //
                // See:
                // - https://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/
                //          cadea780bc76/src/share/classes/sun/security/ssl/SSLContextImpl.java#l127
                context.init(null, new TrustManager[] {
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                                    throws CertificateException {
                                throw new CertificateException();
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                                    throws CertificateException {
                                throw new CertificateException();
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return EmptyArrays.EMPTY_X509_CERTIFICATES;
                            }
                        }
                }, null);
            } catch (Throwable error) {
                context = null;
                cause = error;
            }
            if (cause != null) {
                LOGGER.debug("Unable to access wrapped TrustManager", cause);
            } else {
                final SSLContext finalContext = context;
                Object maybeWrapper = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    @Override
                    public Object run() {
                        try {
                            Field contextSpiField = SSLContext.class.getDeclaredField("contextSpi");
                            final long spiOffset = PlatformDependent.objectFieldOffset(contextSpiField);
                            Object spi = PlatformDependent.getObject(finalContext, spiOffset);
                            if (spi != null) {
                                Class<?> clazz = spi.getClass();

                                // Let's cycle through the whole hierarchy until we find what we are looking for or
                                // there is nothing left in which case we will not wrap at all.
                                do {
                                    try {
                                        Field trustManagerField = clazz.getDeclaredField("trustManager");
                                        final long tmOffset = PlatformDependent.objectFieldOffset(trustManagerField);
                                        Object trustManager = PlatformDependent.getObject(spi, tmOffset);
                                        if (trustManager instanceof X509ExtendedTrustManager) {
                                            return new UnsafeTrustManagerWrapper(spiOffset, tmOffset);
                                        }
                                    } catch (NoSuchFieldException ignore) {
                                        // try next
                                    }
                                    clazz = clazz.getSuperclass();
                                } while (clazz != null);
                            }
                            throw new NoSuchFieldException();
                        } catch (NoSuchFieldException e) {
                            return e;
                        } catch (SecurityException e) {
                            return e;
                        }
                    }
                });
                if (maybeWrapper instanceof Throwable) {
                    LOGGER.debug("Unable to access wrapped TrustManager", (Throwable) maybeWrapper);
                } else {
                    wrapper = (TrustManagerWrapper) maybeWrapper;
                }
            }
        } else {
            LOGGER.debug("Unable to access wrapped TrustManager", cause);
        }
        WRAPPER = wrapper;
    }

    private OpenSslX509TrustManagerWrapper() { }

    static X509TrustManager wrapIfNeeded(X509TrustManager trustManager) {
        return WRAPPER.wrapIfNeeded(trustManager);
    }

    private interface TrustManagerWrapper {
        X509TrustManager wrapIfNeeded(X509TrustManager manager);
    }

    private static SSLContext newSSLContext() throws NoSuchAlgorithmException, NoSuchProviderException {
        // As this depends on the implementation detail we should explicit select the correct provider.
        // See https://github.com/netty/netty/issues/10374
        return SSLContext.getInstance("TLS", "SunJSSE");
    }

    private static final class UnsafeTrustManagerWrapper implements TrustManagerWrapper {
        private final long spiOffset;
        private final long tmOffset;

        UnsafeTrustManagerWrapper(long spiOffset, long tmOffset) {
            this.spiOffset = spiOffset;
            this.tmOffset = tmOffset;
        }

        @Override
        public X509TrustManager wrapIfNeeded(X509TrustManager manager) {
            if (!(manager instanceof X509ExtendedTrustManager)) {
                try {
                    SSLContext ctx = newSSLContext();
                    ctx.init(null, new TrustManager[] { manager }, null);
                    Object spi = PlatformDependent.getObject(ctx, spiOffset);
                    if (spi != null) {
                        Object tm = PlatformDependent.getObject(spi, tmOffset);
                        if (tm instanceof X509ExtendedTrustManager) {
                            return (X509TrustManager) tm;
                        }
                    }
                } catch (NoSuchAlgorithmException e) {
                    // This should never happen as we did the same in the static block
                    // before.
                    PlatformDependent.throwException(e);
                } catch (KeyManagementException e) {
                    // This should never happen as we did the same in the static block
                    // before.
                    PlatformDependent.throwException(e);
                } catch (NoSuchProviderException e) {
                    // This should never happen as we did the same in the static block
                    // before.
                    PlatformDependent.throwException(e);
                }
            }
            return manager;
        }
    }
}
