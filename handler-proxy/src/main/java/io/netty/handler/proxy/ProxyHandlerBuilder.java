/*
 * Copyright 2015 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.util.internal.StringUtil;

/**
 * Creates a new {@link ProxyHandler} of desired type with requested proxy
 * address and authentication. The builder gracefully accepts null or empty
 * values for username and password, which allows callers to pass in values from
 * configuration files without additional checking.
 */
public final class ProxyHandlerBuilder {

    public static enum ProxyType {
        HTTP, SOCKS4, SOCKS5
    }

    /**
     * Obtains a ProxyHandlerBuilder for a given ProxyType.
     */
    public static ProxyHandlerBuilder forType(ProxyType proxyType) {
        return new ProxyHandlerBuilder(proxyType);
    }

    /**
     * Obtains a ProxyHandlerBuilder for a given proxy type String. It will
     * lookup it case-insensitive from ProxyType and throw an exception if it
     * cannot be found.
     *
     * @throws IllegalArgumentException
     *             if the proxyType cannot be resolved to a ProxyType enum.
     */
    public static ProxyHandlerBuilder forType(String proxyType) {
        for (ProxyType type : ProxyType.values()) {
            if (type.name().equalsIgnoreCase(proxyType)) {
                return new ProxyHandlerBuilder(type);
            }
        }
        throw new IllegalArgumentException("Proxy type " + proxyType + " not supported.");
    }

    private final ProxyType type;
    private SocketAddress proxyAddress;
    private String username;
    private String password;

    private ProxyHandlerBuilder(ProxyType type) {
        this.type = type;
    }

    /**
     * Sets an existing SocketAddress as the remote address of the proxy.
     */
    public ProxyHandlerBuilder proxyAddress(SocketAddress proxyAddress) {
        this.proxyAddress = checkNotNull(proxyAddress, "proxyAddress");
        return this;
    }

    /**
     * Sets an unresolved InetSocketAddress as the remote address of the proxy.
     */
    public ProxyHandlerBuilder proxyAddress(String host, int port) {
        this.proxyAddress = InetSocketAddress.createUnresolved(checkNotNull(host, "proxyHost"), port);
        return this;
    }

    /**
     * Sets the username for proxy authentication. An HTTP proxy requires a
     * non-null password if a non-null username is set
     */
    public ProxyHandlerBuilder username(String username) {
        this.username = username;
        return this;
    }

    /**
     * Sets the password for proxy authentication. A SOCKS4 proxy will ignore
     * this password however.
     */
    public ProxyHandlerBuilder password(String password) {
        this.password = password;
        return this;
    }

    /**
     * Builds a ProxyHandler using the configured parameters.
     *
     * @return a ProxyHandler subclass for the specified ProxyType. Never null.
     * @throws IllegalStateException
     *             when proxyAddress was not set.
     */
    public ProxyHandler build() {
        if (proxyAddress == null) {
            throw new IllegalStateException("Cannot build a ProxyHandler without proxyAddress.");
        }
        switch (type) {
        case HTTP:
            if (StringUtil.isNullOrEmpty(username)) {
                return new HttpProxyHandler(proxyAddress);
            } else {
                return new HttpProxyHandler(proxyAddress, username, password);
            }
        case SOCKS4:
            if (StringUtil.isNullOrEmpty(username)) {
                return new Socks4ProxyHandler(proxyAddress);
            } else {
                return new Socks4ProxyHandler(proxyAddress, username);
            }
        case SOCKS5:
            if (StringUtil.isNullOrEmpty(username)) {
                return new Socks5ProxyHandler(proxyAddress);
            } else {
                return new Socks5ProxyHandler(proxyAddress, username, password);
            }
        default:
            throw new Error("Proxy type " + type + " not supported.");
        }
    }
}
