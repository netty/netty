/*
 * Copyright 2014 The Netty Project
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

import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

/**
 * Factory for wrapping {@link SSLEngine} object in {@link JettyNpnSslEngine} objects
 */
public final class JettyNpnSslEngineWrapper implements SslEngineWrapperFactory {
    private static JettyNpnSslEngineWrapper instance;

    private JettyNpnSslEngineWrapper() throws SSLException {
        if (!JettyNpnSslEngine.isAvailable()) {
            throw new SSLException("NPN unsupported. Is your classpatch configured correctly?" +
                            " See http://www.eclipse.org/jetty/documentation/current/npn-chapter.html#npn-starting");
        }
    }

    public static JettyNpnSslEngineWrapper instance() throws SSLException {
        if (instance == null) {
            instance = new JettyNpnSslEngineWrapper();
        }
        return instance;
    }

    @Override
    public SSLEngine wrapSslEngine(SSLEngine engine, List<String> protocols, boolean isServer) {
        return new JettyNpnSslEngine(engine, protocols, isServer);
    }
}
