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

import org.eclipse.jetty.npn.NextProtoNego;
import org.eclipse.jetty.npn.NextProtoNego.ClientProvider;
import org.eclipse.jetty.npn.NextProtoNego.ServerProvider;

final class JettyNpnSslEngine extends JettySslEngine {
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
                // although it's not perfect to tell if NPN extension has been loaded.
                bootloader = ClassLoader.getSystemClassLoader();
            }
            Class.forName("sun.security.ssl.NextProtoNegoExtension", true, bootloader);
            available = true;
        } catch (Exception ignore) {
            // npn-boot was not loaded.
        }
    }

    JettyNpnSslEngine(SSLEngine engine, final List<String> nextProtocols, boolean server) {
        super(engine, nextProtocols, server);

        if (server) {
            NextProtoNego.put(engine, new ServerProvider() {
                @Override
                public void unsupported() {
                    getSession().setApplicationProtocol(nextProtocols.get(nextProtocols.size() - 1));
                }

                @Override
                public List<String> protocols() {
                    return nextProtocols;
                }

                @Override
                public void protocolSelected(String protocol) {
                    getSession().setApplicationProtocol(protocol);
                }
            });
        } else {
            final String[] array = nextProtocols.toArray(new String[nextProtocols.size()]);

            NextProtoNego.put(engine, new ClientProvider() {
                @Override
                public boolean supports() {
                    return true;
                }

                @Override
                public void unsupported() {
                    session.setApplicationProtocol(null);
                }

                @Override
                public String selectProtocol(List<String> protocols) {
                    for (int i = 0; i < array.length; ++i) {
                        String p = array[i];
                        if (protocols.contains(p)) {
                            session.setApplicationProtocol(p);
                            return p;
                        }
                    }
                    session.setApplicationProtocol(null);
                    return null;
                }
            });
        }
    }

    @Override
    public void closeInbound() throws SSLException {
        NextProtoNego.remove(engine);
        super.closeInbound();
    }

    @Override
    public void closeOutbound() {
        NextProtoNego.remove(engine);
        super.closeOutbound();
    }
}
