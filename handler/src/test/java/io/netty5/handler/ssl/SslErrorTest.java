/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty5.pkitesting.CertificateBuilder;
import io.netty5.pkitesting.X509Bundle;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.EmptyArrays;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.security.KeyStore;
import java.security.cert.CRLReason;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static io.netty5.util.internal.SilentDispose.autoClosing;

public class SslErrorTest {
    private static final X509Bundle CERT;

    static {
        try {
            CERT = new CertificateBuilder()
                    .subject("cn=localhost")
                    .setIsCertificateAuthority(true)
                    .buildSelfSigned();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Collection<Object[]> data() {
        List<SslProvider> serverProviders = new ArrayList<>(2);
        List<SslProvider> clientProviders = new ArrayList<>(3);

        if (OpenSsl.isAvailable()) {
            serverProviders.add(SslProvider.OPENSSL);
            serverProviders.add(SslProvider.OPENSSL_REFCNT);
            clientProviders.add(SslProvider.OPENSSL);
            clientProviders.add(SslProvider.OPENSSL_REFCNT);
        }
        // We not test with SslProvider.JDK on the server side as the JDK implementation currently just send the same
        // alert all the time, sigh.....
        clientProviders.add(SslProvider.JDK);

        List<CertificateException> exceptions = new ArrayList<>(6);
        exceptions.add(new CertificateExpiredException());
        exceptions.add(new CertificateNotYetValidException());
        exceptions.add(new CertificateRevokedException(
                new Date(), CRLReason.AA_COMPROMISE, new X500Principal(""),
                Collections.emptyMap()));

        // Also use wrapped exceptions as this is what the JDK implementation of X509TrustManagerFactory is doing.
        exceptions.add(newCertificateException(CertPathValidatorException.BasicReason.EXPIRED));
        exceptions.add(newCertificateException(CertPathValidatorException.BasicReason.NOT_YET_VALID));
        exceptions.add(newCertificateException(CertPathValidatorException.BasicReason.REVOKED));

        List<Object[]> params = new ArrayList<>();
        for (SslProvider serverProvider: serverProviders) {
            for (SslProvider clientProvider: clientProviders) {
                for (CertificateException exception: exceptions) {
                    params.add(new Object[] { serverProvider, clientProvider, exception, true });
                    params.add(new Object[] { serverProvider, clientProvider, exception, false });
                }
            }
        }
        return params;
    }

    private static CertificateException newCertificateException(CertPathValidatorException.Reason reason) {
        return new TestCertificateException(
                new CertPathValidatorException("x", null, null, -1, reason));
    }

    @ParameterizedTest(
            name = "{index}: serverProvider = {0}, clientProvider = {1}, exception = {2}, serverProduceError = {3}")
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testCorrectAlert(SslProvider serverProvider, final SslProvider clientProvider,
                                 final CertificateException exception, final boolean serverProduceError)
            throws Exception {
        // As this only works correctly at the moment when OpenSslEngine is used on the server-side there is
        // no need to run it if there is no openssl is available at all.
        OpenSsl.ensureAvailability();

        SslContextBuilder sslServerCtxBuilder = SslContextBuilder.forServer(CERT.getKeyPair().getPrivate(),
                        CERT.getCertificatePath())
                .sslProvider(serverProvider)
                .clientAuth(ClientAuth.REQUIRE);
        SslContextBuilder sslClientCtxBuilder =  SslContextBuilder.forClient()
                .keyManager(new File(getClass().getResource("test.crt").getFile()),
                        new File(getClass().getResource("test_unencrypted.pem").getFile()))
                .sslProvider(clientProvider);

        if (serverProduceError) {
            sslServerCtxBuilder.trustManager(new ExceptionTrustManagerFactory(exception));
            sslClientCtxBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        } else {
            sslServerCtxBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            sslClientCtxBuilder.trustManager(new ExceptionTrustManagerFactory(exception));
        }

        final SslContext sslServerCtx = sslServerCtxBuilder.build();
        final SslContext sslClientCtx = sslClientCtxBuilder.build();

        Channel serverChannel = null;
        Channel clientChannel = null;
        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        final Promise<Void> promise = group.next().newPromise();
        try (AutoCloseable ignore1 = autoClosing(sslServerCtx);
             AutoCloseable ignore2 = autoClosing(sslClientCtx)) {
            serverChannel = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(sslServerCtx.newHandler(ch.bufferAllocator()));
                            if (!serverProduceError) {
                                ch.pipeline().addLast(new AlertValidationHandler(
                                        clientProvider, serverProduceError, exception, promise));
                            }
                            ch.pipeline().addLast(new ChannelHandler() {

                                @Override
                                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    ctx.close();
                                }
                            });
                        }
                    }).bind(0).asStage().get();

            clientChannel = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(sslClientCtx.newHandler(ch.bufferAllocator()));

                            if (serverProduceError) {
                                ch.pipeline().addLast(new AlertValidationHandler(
                                        clientProvider, serverProduceError, exception, promise));
                            }
                            ch.pipeline().addLast(new ChannelHandler() {

                                @Override
                                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    ctx.close();
                                }
                            });
                        }
                    }).connect(serverChannel.localAddress()).asStage().get();
            // Block until we received the correct exception
            promise.asFuture().asStage().sync();
        } finally {
            if (clientChannel != null) {
                clientChannel.close().asStage().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().asStage().sync();
            }
            group.shutdownGracefully();
        }
    }

    private static final class ExceptionTrustManagerFactory extends SimpleTrustManagerFactory {
        private final CertificateException exception;

        ExceptionTrustManagerFactory(CertificateException exception) {
            this.exception = exception;
        }

        @Override
        protected void engineInit(KeyStore keyStore) { }
        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters) { }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[] { new X509TrustManager() {

                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                        throws CertificateException {
                    throw exception;
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                        throws CertificateException {
                    throw exception;
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return EmptyArrays.EMPTY_X509_CERTIFICATES;
                }
            } };
        }
    }

    private static final class AlertValidationHandler implements ChannelHandler {
        private final SslProvider clientProvider;
        private final boolean serverProduceError;
        private final CertificateException exception;
        private final Promise<Void> promise;

        AlertValidationHandler(SslProvider clientProvider, boolean serverProduceError,
                               CertificateException exception, Promise<Void> promise) {
            this.clientProvider = clientProvider;
            this.serverProduceError = serverProduceError;
            this.exception = exception;
            this.promise = promise;
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Unwrap as its wrapped by a DecoderException
            Throwable unwrappedCause = cause.getCause();
            if (unwrappedCause instanceof SSLException) {
                if (exception instanceof TestCertificateException) {
                    CertPathValidatorException.Reason reason =
                            ((CertPathValidatorException) exception.getCause()).getReason();
                    if (reason == CertPathValidatorException.BasicReason.EXPIRED) {
                        verifyException(clientProvider, serverProduceError, unwrappedCause, promise, "expired");
                    } else if (reason == CertPathValidatorException.BasicReason.NOT_YET_VALID) {
                        // BoringSSL may use "expired" in this case while others use "bad"
                        verifyException(clientProvider, serverProduceError, unwrappedCause, promise, "expired", "bad");
                    } else if (reason == CertPathValidatorException.BasicReason.REVOKED) {
                        verifyException(clientProvider, serverProduceError, unwrappedCause, promise, "revoked");
                    }
                } else if (exception instanceof CertificateExpiredException) {
                    verifyException(clientProvider, serverProduceError, unwrappedCause, promise,  "expired");
                } else if (exception instanceof CertificateNotYetValidException) {
                    // BoringSSL may use "expired" in this case while others use "bad"
                    verifyException(clientProvider, serverProduceError, unwrappedCause, promise, "expired", "bad");
                } else if (exception instanceof CertificateRevokedException) {
                    verifyException(clientProvider, serverProduceError, unwrappedCause, promise, "revoked");
                }
            }
        }
    }

    // Its a bit hacky to verify against the message that is part of the exception but there is no other way
    // at the moment as there are no different exceptions for the different alerts.
    private static void verifyException(SslProvider clientProvider, boolean serverProduceError,
                                 Throwable cause, Promise<Void> promise, String... messageParts) {
        String message = cause.getMessage();
        // When the error is produced on the client side and the client side uses JDK as provider it will always
        // use "certificate unknown".
        if (!serverProduceError && clientProvider == SslProvider.JDK &&
                message.toLowerCase(Locale.UK).contains("unknown")) {
            promise.setSuccess(null);
            return;
        }

        for (String m: messageParts) {
            if (message.toLowerCase(Locale.UK).contains(m.toLowerCase(Locale.UK))) {
                promise.setSuccess(null);
                return;
            }
        }
        Throwable error = new AssertionError("message not contains any of '"
                + Arrays.toString(messageParts) + "': " + message, cause);
        promise.setFailure(error);
    }

    private static final class TestCertificateException extends CertificateException {
        private static final long serialVersionUID = -5816338303868751410L;

        TestCertificateException(Throwable cause) {
            super(cause);
        }
    }
}
