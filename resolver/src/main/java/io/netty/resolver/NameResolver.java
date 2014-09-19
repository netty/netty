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

package io.netty.resolver;

import io.netty.util.concurrent.Future;

import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;

/**
 * Resolves an arbitrary string that represents the name of an endpoint into a {@link SocketAddress}.
 */
public interface NameResolver {

    /**
     * Returns {@code true} if and only if the specified address is supported by this resolved.
     */
    boolean isSupported(SocketAddress address);

    /**
     * Returns {@code true} if and only if the specified address has been resolved.
     *
     * @throws UnsupportedAddressTypeException if the specified address is not supported by this resolver
     */
    boolean isResolved(SocketAddress address);

    /**
     * Resolves the specified name into a {@link SocketAddress}.
     *
     * @param inetHost the name to resolve
     *
     * @return the {@link SocketAddress} as the result of the resolution
     */
    Future<SocketAddress> resolve(String inetHost, int inetPort);

    /**
     * Resolves the specified unresolved address into a resolved one. If the specified address is resolved already,
     * this method does nothing but returning the original address.
     *
     * @param unresolvedAddress the unresolved address
     *
     * @return the {@link SocketAddress} as the result of the resolution
     */
    Future<SocketAddress> resolve(SocketAddress unresolvedAddress);
}
