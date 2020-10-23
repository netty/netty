/*
 * Copyright 2014 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelectionListener;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelector;
import io.netty.util.internal.PlatformDependent;

import java.util.LinkedHashSet;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import org.eclipse.jetty.npn.NextProtoNego;
import org.eclipse.jetty.npn.NextProtoNego.ClientProvider;
import org.eclipse.jetty.npn.NextProtoNego.ServerProvider;

final class JettyNpnSslEngine extends JdkSslEngine {
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
            // Always use bootstrap class loader.
            Class.forName("sun.security.ssl.NextProtoNegoExtension", true, null);
            available = true;
        } catch (Exception ignore) {
            // npn-boot was not loaded.
        }
    }

    JettyNpnSslEngine(SSLEngine engine, final JdkApplicationProtocolNegotiator applicationNegotiator, boolean server) {
        super(engine);
        checkNotNull(applicationNegotiator, "applicationNegotiator");

        if (server) {
            final ProtocolSelectionListener protocolListener = checkNotNull(applicationNegotiator
                    .protocolListenerFactory().newListener(this, applicationNegotiator.protocols()),
                    "protocolListener");
            NextProtoNego.put(engine, new ServerProvider() {
                @Override
                public void unsupported() {
                    protocolListener.unsupported();
                }

                @Override
                public List<String> protocols() {
                    return applicationNegotiator.protocols();
                }

                @Override
                public void protocolSelected(String protocol) {
                    try {
                        protocolListener.selected(protocol);
                    } catch (Throwable t) {
                        PlatformDependent.throwException(t);
                    }
                }
            });
        } else {
            final ProtocolSelector protocolSelector = checkNotNull(applicationNegotiator.protocolSelectorFactory()
                    .newSelector(this, new LinkedHashSet<String>(applicationNegotiator.protocols())),
                    "protocolSelector");
            NextProtoNego.put(engine, new ClientProvider() {
                @Override
                public boolean supports() {
                    return true;
                }

                @Override
                public void unsupported() {
                    protocolSelector.unsupported();
                }

                @Override
                public String selectProtocol(List<String> protocols) {
                    try {
                        return protocolSelector.select(protocols);
                    } catch (Throwable t) {
                        PlatformDependent.throwException(t);
                        return null;
                    }
                }
            });
        }
    }

    @Override
    public void closeInbound() throws SSLException {
        NextProtoNego.remove(getWrappedEngine());
        super.closeInbound();
    }

    @Override
    public void closeOutbound() {
        NextProtoNego.remove(getWrappedEngine());
        super.closeOutbound();
    }
}
