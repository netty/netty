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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.List;

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

final class Java9SslUtils {
    private static final InternalLogger log = InternalLoggerFactory.getInstance(Java9SslUtils.class);
    private static final boolean supportsAlpn;
    private static final Method setApplicationProtocols;
    private static final Method getApplicationProtocol;
    private static final Method setHandshakeApplicationProtocolSelector;
    private static final Class biFunctionClass;

    static {
        boolean support = false;
        Method setter = null;
        Method getter = null;
        Method selector = null;
        Class biFunction = null;

        if (PlatformDependent.javaVersion() >= 9) {
            try {
                getter = AccessController.doPrivileged(new PrivilegedExceptionAction<Method>() {
                    @Override
                    public Method run() throws Exception {
                        return SSLEngine.class.getMethod("getApplicationProtocol");
                    }
                });
                setter = AccessController.doPrivileged(new PrivilegedExceptionAction<Method>() {
                    @Override
                    public Method run() throws Exception {
                        return SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
                    }
                });
                biFunction = Class.forName("java.util.function.BiFunction");
                selector = AccessController.doPrivileged(new PrivilegedExceptionAction<Method>() {
                    @Override
                    public Method run() throws Exception {
                        Class biFunction = Class.forName("java.util.function.BiFunction");
                        return SSLEngine.class.getMethod("setHandshakeApplicationProtocolSelector", biFunction);
                    }
                });
                support = true;
            } catch (Throwable t) {
                log.error("Unable to initialize Java9SslUtils, but the detected javaVersion was " +
                    PlatformDependent.javaVersion(), t);
            }
        }

        supportsAlpn = support;
        getApplicationProtocol = getter;
        setApplicationProtocols = setter;
        biFunctionClass = biFunction;
        setHandshakeApplicationProtocolSelector = selector;
    }

    private Java9SslUtils() {
    }

    static boolean supportsAlpn(SSLEngine engine) {
        if (!supportsAlpn) {
            return false;
        }

        try {
            getApplicationProtocol.invoke(engine);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    static SSLEngine configureAlpn(
        SSLEngine engine,
        JdkApplicationProtocolNegotiator applicationNegotiator,
        boolean isServer,
        boolean failIfNoCommonProtocols
    ) {
        List<String> supportedProtocols = applicationNegotiator.protocols();

        if (isServer && !failIfNoCommonProtocols) {
            installSelector(engine, supportedProtocols);
        } else {
            SSLParameters params = engine.getSSLParameters();
            setApplicationProtocols(params, supportedProtocols);
            engine.setSSLParameters(params);
        }

        return engine;
    }

    static String getApplicationProtocol(SSLEngine sslEngine) {
        try {
            return (String) getApplicationProtocol.invoke(sslEngine);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void setApplicationProtocols(SSLParameters parameters, List<String> supportedProtocols) {
        String[] protocolArray = supportedProtocols.toArray(EmptyArrays.EMPTY_STRINGS);
        try {
            setApplicationProtocols.invoke(parameters, new Object[]{ protocolArray });
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void installSelector(final SSLEngine sslEngine, final List<String> supportedProtocols) {
        checkNotNull(supportedProtocols, "supportedProtocols");

        Object biFunction = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                return Proxy.newProxyInstance(Java9SslUtils.class.getClassLoader(), new Class[]{ biFunctionClass },
                        new AlpnSelector(supportedProtocols));
            }
        });

        try {
            setHandshakeApplicationProtocolSelector.invoke(sslEngine, biFunction);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class AlpnSelector implements InvocationHandler {
        private final List<String> supportedProtocols;

        private AlpnSelector(List<String> supportedProtocols) {
            this.supportedProtocols = supportedProtocols;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!"apply".equals(method.getName())) {
                throw new UnsupportedOperationException("Invoked unknown method " + method.getName());
            }

            SSLEngine sslEngine = (SSLEngine) args[0];
            List<String> protocols = (List<String>) args[1];
            for (String p : supportedProtocols) {
                if (protocols.contains(p)) {
                    return p;
                }
            }

            return "";
        }
    }
}
