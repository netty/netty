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

import org.eclipse.jetty.alpn.ALPN;
import org.eclipse.jetty.alpn.ALPN.ClientProvider;
import org.eclipse.jetty.alpn.ALPN.ServerProvider;

final class JettyAlpnSslEngine extends JettySslEngine {
    private static boolean available;

    static boolean isAvailable() {
        updateAvailability();
        return available;
    }

    private static void updateAvailability() {
        if (available) {
            return;
        }

        try {
            // Try to get the bootstrap class loader.
            ClassLoader bootloader = ClassLoader.getSystemClassLoader().getParent();
            if (bootloader == null) {
                // If failed, use the system class loader,
                // although it's not perfect to tell if APLN extension has been loaded.
                bootloader = ClassLoader.getSystemClassLoader();
            }
            Class.forName("sun.security.ssl.ALPNExtension", true, bootloader);
            available = true;
        } catch (Exception ignore) {
            // alpn-boot was not loaded.
        }
    }

    JettyAlpnSslEngine(SSLEngine engine, final List<String> nextProtocols, boolean server) {
        super(engine, nextProtocols, server);

        if (server) {
            final String[] array = nextProtocols.toArray(new String[nextProtocols.size()]);
            final String fallback = array[array.length - 1];

            ALPN.put(engine, new ServerProvider() {
                @Override
                public String select(List<String> protocols) {
                    for (int i = 0; i < array.length; ++i) {
                        String p = array[i];
                        if (protocols.contains(p)) {
                            session.setApplicationProtocol(p);
                            return p;
                        }
                    }
                    session.setApplicationProtocol(fallback);
                    return fallback;
                }

                @Override
                public void unsupported() {
                    session.setApplicationProtocol(null);
                }
            });
        } else {
            ALPN.put(engine, new ClientProvider() {
                @Override
                public List<String> protocols() {
                    return nextProtocols;
                }

                @Override
                public void selected(String protocol) {
                    getSession().setApplicationProtocol(protocol);
                }

                @Override
                public boolean supports() {
                    return true;
                }

                @Override
                public void unsupported() {
                    getSession().setApplicationProtocol(nextProtocols.get(nextProtocols.size() - 1));
                }
            });
        }
    }

    @Override
    public void closeInbound() throws SSLException {
        ALPN.remove(engine);
        super.closeInbound();
    }

    @Override
    public void closeOutbound() {
        ALPN.remove(engine);
        super.closeOutbound();
    }
}
