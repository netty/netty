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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.reflect.Method;
import java.security.AccessController;
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

    static {
        boolean support = false;
        Method setter = null;
        Method getter = null;

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
                support = true;
            } catch (Throwable t) {
                log.error("Unable to initialize Java9SslUtils, but the detected javaVersion was " +
                    PlatformDependent.javaVersion(), t);
            }
        }

        supportsAlpn = support;
        getApplicationProtocol = getter;
        setApplicationProtocols = setter;
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

        SSLParameters params = engine.getSSLParameters();
        setApplicationProtocols(params, supportedProtocols);
        engine.setSSLParameters(params);

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
}
