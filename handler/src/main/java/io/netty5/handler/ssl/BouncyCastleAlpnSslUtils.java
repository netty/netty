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
package io.netty5.handler.ssl;

import io.netty5.util.internal.EmptyArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiFunction;

import static io.netty5.handler.ssl.SslUtils.getSSLContext;

final class BouncyCastleAlpnSslUtils {
    private static final Logger logger = LoggerFactory.getLogger(BouncyCastleAlpnSslUtils.class);

    private static final Method SET_PARAMETERS;
    private static final Method GET_PARAMETERS;
    private static final Method SET_APPLICATION_PROTOCOLS;
    private static final Method GET_APPLICATION_PROTOCOL;
    private static final Method GET_HANDSHAKE_APPLICATION_PROTOCOL;
    private static final Method SET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR;
    private static final Method GET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR;
    private static final Class<?> BC_APPLICATION_PROTOCOL_SELECTOR;
    private static final Method BC_APPLICATION_PROTOCOL_SELECTOR_SELECT;

    static {
        Class<?> bcSslEngine;
        Method getParameters;

        Method setParameters;
        Method setApplicationProtocols;
        Method getApplicationProtocol;
        Method getHandshakeApplicationProtocol;
        Method setHandshakeApplicationProtocolSelector;
        Method getHandshakeApplicationProtocolSelector;
        Method bcApplicationProtocolSelectorSelect;
        Class<?> bcApplicationProtocolSelector;

        try {
            bcSslEngine = Class.forName("org.bouncycastle.jsse.BCSSLEngine");
            final Class<?> testBCSslEngine = bcSslEngine;

            bcApplicationProtocolSelector =
                    Class.forName("org.bouncycastle.jsse.BCApplicationProtocolSelector");

            final Class<?> testBCApplicationProtocolSelector = bcApplicationProtocolSelector;

            bcApplicationProtocolSelectorSelect =
                    testBCApplicationProtocolSelector.getMethod("select", Object.class, List.class);

            SSLContext context = getSSLContext("BCJSSE");
            SSLEngine engine = context.createSSLEngine();

            getParameters = testBCSslEngine.getMethod("getParameters");

            final Object bcSslParameters = getParameters.invoke(engine);
            final Class<?> bCSslParametersClass = bcSslParameters.getClass();

            setParameters = testBCSslEngine.getMethod("setParameters", bCSslParametersClass);
            setParameters.invoke(engine, bcSslParameters);

            setApplicationProtocols = bCSslParametersClass.getMethod("setApplicationProtocols", String[].class);
            setApplicationProtocols.invoke(bcSslParameters, new Object[] { EmptyArrays.EMPTY_STRINGS });

            getApplicationProtocol = testBCSslEngine.getMethod("getApplicationProtocol");
            getApplicationProtocol.invoke(engine);

            getHandshakeApplicationProtocol = testBCSslEngine.getMethod("getHandshakeApplicationProtocol");
            getHandshakeApplicationProtocol.invoke(engine);

            setHandshakeApplicationProtocolSelector =
                    testBCSslEngine.getMethod("setBCHandshakeApplicationProtocolSelector",
                                              testBCApplicationProtocolSelector);

            getHandshakeApplicationProtocolSelector =
                    testBCSslEngine.getMethod("getBCHandshakeApplicationProtocolSelector");
            getHandshakeApplicationProtocolSelector.invoke(engine);

        } catch (Throwable t) {
            logger.error("Unable to initialize BouncyCastleAlpnSslUtils.", t);
            setParameters = null;
            getParameters = null;
            setApplicationProtocols = null;
            getApplicationProtocol = null;
            getHandshakeApplicationProtocol = null;
            setHandshakeApplicationProtocolSelector = null;
            getHandshakeApplicationProtocolSelector = null;
            bcApplicationProtocolSelectorSelect = null;
            bcApplicationProtocolSelector = null;
        }
        SET_PARAMETERS = setParameters;
        GET_PARAMETERS = getParameters;
        SET_APPLICATION_PROTOCOLS = setApplicationProtocols;
        GET_APPLICATION_PROTOCOL = getApplicationProtocol;
        GET_HANDSHAKE_APPLICATION_PROTOCOL = getHandshakeApplicationProtocol;
        SET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR = setHandshakeApplicationProtocolSelector;
        GET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR = getHandshakeApplicationProtocolSelector;
        BC_APPLICATION_PROTOCOL_SELECTOR_SELECT = bcApplicationProtocolSelectorSelect;
        BC_APPLICATION_PROTOCOL_SELECTOR = bcApplicationProtocolSelector;
    }

    private BouncyCastleAlpnSslUtils() {
    }

    static String getApplicationProtocol(SSLEngine sslEngine) {
        try {
            return (String) GET_APPLICATION_PROTOCOL.invoke(sslEngine);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void setApplicationProtocols(SSLEngine engine, List<String> supportedProtocols) {
        String[] protocolArray = supportedProtocols.toArray(EmptyArrays.EMPTY_STRINGS);
        try {
            Object bcSslParameters = GET_PARAMETERS.invoke(engine);
            SET_APPLICATION_PROTOCOLS.invoke(bcSslParameters, new Object[]{protocolArray});
            SET_PARAMETERS.invoke(engine, bcSslParameters);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        JdkAlpnSslUtils.setApplicationProtocols(engine, supportedProtocols);
    }

    static String getHandshakeApplicationProtocol(SSLEngine sslEngine) {
        try {
            return (String) GET_HANDSHAKE_APPLICATION_PROTOCOL.invoke(sslEngine);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void setHandshakeApplicationProtocolSelector(
            SSLEngine engine, final BiFunction<SSLEngine, List<String>, String> selector) {
        try {
            Object selectorProxyInstance = Proxy.newProxyInstance(
                    BouncyCastleAlpnSslUtils.class.getClassLoader(),
                    new Class[]{BC_APPLICATION_PROTOCOL_SELECTOR},
                    (proxy, method, args) -> {
                        if ("select".equals(method.getName())) {
                            try {
                                return selector.apply((SSLEngine) args[0], (List<String>) args[1]);
                            } catch (ClassCastException e) {
                                throw new RuntimeException("BCApplicationProtocolSelector select method " +
                                        "parameter of invalid type.", e);
                            }
                        } else {
                            throw new UnsupportedOperationException(String.format("Method '%s' not supported.",
                                    method.getName()));
                        }
                    });

            SET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR.invoke(engine, selectorProxyInstance);
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector(SSLEngine engine) {
        try {
            final Object selector = GET_HANDSHAKE_APPLICATION_PROTOCOL_SELECTOR.invoke(engine);
            return (sslEngine, strings) -> {
                try {
                    return (String) BC_APPLICATION_PROTOCOL_SELECTOR_SELECT.invoke(selector, sslEngine,
                            strings);
                } catch (Exception e) {
                    throw new RuntimeException("Could not call getHandshakeApplicationProtocolSelector", e);
                }
            };

        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
