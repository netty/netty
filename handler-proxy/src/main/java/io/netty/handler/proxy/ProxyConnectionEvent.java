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

package io.netty.handler.proxy;

import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;

public final class ProxyConnectionEvent {

    private final String protocol;
    private final String authScheme;
    private final SocketAddress proxyAddress;
    private final SocketAddress destinationAddress;
    private String strVal;

    /**
     * Creates a new event that indicates a successful connection attempt to the destination address.
     */
    public ProxyConnectionEvent(
            String protocol, String authScheme, SocketAddress proxyAddress, SocketAddress destinationAddress) {
        if (protocol == null) {
            throw new NullPointerException("protocol");
        }
        if (authScheme == null) {
            throw new NullPointerException("authScheme");
        }
        if (proxyAddress == null) {
            throw new NullPointerException("proxyAddress");
        }
        if (destinationAddress == null) {
            throw new NullPointerException("destinationAddress");
        }

        this.protocol = protocol;
        this.authScheme = authScheme;
        this.proxyAddress = proxyAddress;
        this.destinationAddress = destinationAddress;
    }

    /**
     * Returns the name of the proxy protocol in use.
     */
    public String protocol() {
        return protocol;
    }

    /**
     * Returns the name of the authentication scheme in use.
     */
    public String authScheme() {
        return authScheme;
    }

    /**
     * Returns the address of the proxy server.
     */
    @SuppressWarnings("unchecked")
    public <T extends SocketAddress> T proxyAddress() {
        return (T) proxyAddress;
    }

    /**
     * Returns the address of the destination.
     */
    @SuppressWarnings("unchecked")
    public <T extends SocketAddress> T destinationAddress() {
        return (T) destinationAddress;
    }

    @Override
    public String toString() {
        if (strVal != null) {
            return strVal;
        }

        StringBuilder buf = new StringBuilder(128);
        buf.append(StringUtil.simpleClassName(this));
        buf.append('(');
        buf.append(protocol);
        buf.append(", ");
        buf.append(authScheme);
        buf.append(", ");
        buf.append(proxyAddress);
        buf.append(" => ");
        buf.append(destinationAddress);
        buf.append(')');

        return strVal = buf.toString();
    }
}
