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

package io.netty.handler.proxy;

import io.netty.util.internal.ObjectUtil;
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
        this.protocol = ObjectUtil.checkNotNull(protocol, "protocol");
        this.authScheme = ObjectUtil.checkNotNull(authScheme, "authScheme");
        this.proxyAddress = ObjectUtil.checkNotNull(proxyAddress, "proxyAddress");
        this.destinationAddress = ObjectUtil.checkNotNull(destinationAddress, "destinationAddress");
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

        StringBuilder buf = new StringBuilder(128)
            .append(StringUtil.simpleClassName(this))
            .append('(')
            .append(protocol)
            .append(", ")
            .append(authScheme)
            .append(", ")
            .append(proxyAddress)
            .append(" => ")
            .append(destinationAddress)
            .append(')');

        return strVal = buf.toString();
    }
}
