/*
 * Copyright 2017 The Netty Project
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


import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.function.BiFunction;

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

final class Java9SslUtils {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Java9SslUtils.class);
    private static final MethodHandle SET_APPLICATION_PROTOCOLS;
    private static final MethodHandle GET_APPLICATION_PROTOCOL;
    private static final MethodHandle GET_HANDSHAKE_APPLICATION_PROTOCOL;
    private static final MethodHandle SET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR;
    private static final MethodHandle GET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR;

    static {
        MethodHandle getHandshakeApplicationProtocol;
        MethodHandle getApplicationProtocol;
        MethodHandle setApplicationProtocols;
        MethodHandle setHandshakeApplicationProtocolSelector;
        MethodHandle getHandshakeApplicationProtocolSelector;

        try {
            SSLContext context = SSLContext.getInstance(JdkSslContext.PROTOCOL);
            context.init(null, null, null);
            SSLEngine engine = context.createSSLEngine();
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            getHandshakeApplicationProtocol =
                    AccessController.doPrivileged((PrivilegedExceptionAction<MethodHandle>) () ->
                    lookup.findVirtual(SSLEngine.class, "getHandshakeApplicationProtocol",
                            MethodType.methodType(String.class)));
            getHandshakeApplicationProtocol.invokeExact(engine);

            getApplicationProtocol = AccessController.doPrivileged((PrivilegedExceptionAction<MethodHandle>) () ->
                    lookup.findVirtual(SSLEngine.class, "getApplicationProtocol",
                            MethodType.methodType(String.class)));
            getApplicationProtocol.invokeExact(engine);

            setApplicationProtocols = AccessController.doPrivileged((PrivilegedExceptionAction<MethodHandle>) () ->
                    lookup.findVirtual(SSLParameters.class, "setApplicationProtocols",
                            MethodType.methodType(void.class, String[].class)));
            setApplicationProtocols.invokeExact(engine.getSSLParameters(), EmptyArrays.EMPTY_STRINGS);

            setHandshakeApplicationProtocolSelector =
                    AccessController.doPrivileged((PrivilegedExceptionAction<MethodHandle>) () ->
                            lookup.findVirtual(SSLEngine.class, "setHandshakeApplicationProtocolSelector",
                                    MethodType.methodType(void.class, BiFunction.class)));
            setHandshakeApplicationProtocolSelector.invokeExact(engine,
                    (BiFunction<SSLEngine, List<String>, String>) (sslEngine, strings) -> null);

            getHandshakeApplicationProtocolSelector =
                    AccessController.doPrivileged((PrivilegedExceptionAction<MethodHandle>) () ->
                            lookup.findVirtual(SSLEngine.class, "getHandshakeApplicationProtocolSelector",
                                    MethodType.methodType(BiFunction.class)));
            getHandshakeApplicationProtocolSelector.invokeExact(engine);
        } catch (Throwable t) {
            logger.error("Unable to initialize Java9SslUtils, but the detected javaVersion was: {}",
                    PlatformDependent.javaVersion(), t);
            getHandshakeApplicationProtocol = null;
            getApplicationProtocol = null;
            setApplicationProtocols = null;
            setHandshakeApplicationProtocolSelector = null;
            getHandshakeApplicationProtocolSelector = null;
        }
        GET_HANDSHAKE_APPLICATION_PROTOCOL = getHandshakeApplicationProtocol;
        GET_APPLICATION_PROTOCOL = getApplicationProtocol;
        SET_APPLICATION_PROTOCOLS = setApplicationProtocols;
        SET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR = setHandshakeApplicationProtocolSelector;
        GET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR = getHandshakeApplicationProtocolSelector;
    }

    private Java9SslUtils() {
    }

    static boolean supportsAlpn() {
        return GET_APPLICATION_PROTOCOL != null;
    }

    static String getApplicationProtocol(SSLEngine sslEngine) {
        try {
            return (String) GET_APPLICATION_PROTOCOL.invokeExact(sslEngine);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String getHandshakeApplicationProtocol(SSLEngine sslEngine) {
        try {
            return (String) GET_HANDSHAKE_APPLICATION_PROTOCOL.invokeExact(sslEngine);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void setApplicationProtocols(SSLEngine engine, List<String> supportedProtocols) {
        SSLParameters parameters = engine.getSSLParameters();

        String[] protocolArray = supportedProtocols.toArray(EmptyArrays.EMPTY_STRINGS);
        try {
            SET_APPLICATION_PROTOCOLS.invokeExact(parameters, protocolArray);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
        engine.setSSLParameters(parameters);
    }

    static void setHandshakeApplicationProtocolSelector(
            SSLEngine engine, BiFunction<SSLEngine, List<String>, String> selector) {
        try {
            SET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR.invokeExact(engine, selector);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    static BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector(SSLEngine engine) {
        try {
            return (BiFunction<SSLEngine, List<String>, String>)
                    GET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR.invokeExact(engine);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }
}
