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
package io.netty.resolver;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.UnstableApi;

import java.io.Closeable;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.List;

/**
 * Resolves a possibility unresolved {@link SocketAddress}.
 */
@UnstableApi
public interface AddressResolver<T extends SocketAddress> extends Closeable {

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
   * Resolves the specified address. If the specified address is resolved already, this method does nothing
   * but returning the original address.
   *
   * @param address the address to resolve
   *
   * @return the {@link SocketAddress} as the result of the resolution
   */
  Future<T> resolve(SocketAddress address);

  /**
   * Resolves the specified address. If the specified address is resolved already, this method does nothing
   * but returning the original address.
   *
   * @param address the address to resolve
   * @param promise the {@link Promise} which will be fulfilled when the name resolution is finished
   *
   * @return the {@link SocketAddress} as the result of the resolution
   */
  Future<T> resolve(SocketAddress address, Promise<T> promise);

  /**
   * Resolves the specified address. If the specified address is resolved already, this method does nothing
   * but returning the original address.
   *
   * @param address the address to resolve
   *
   * @return the list of the {@link SocketAddress}es as the result of the resolution
   */
  Future<List<T>> resolveAll(SocketAddress address);

  /**
   * Resolves the specified address. If the specified address is resolved already, this method does nothing
   * but returning the original address.
   *
   * @param address the address to resolve
   * @param promise the {@link Promise} which will be fulfilled when the name resolution is finished
   *
   * @return the list of the {@link SocketAddress}es as the result of the resolution
   */
  Future<List<T>> resolveAll(SocketAddress address, Promise<List<T>> promise);

  /**
   * Closes all the resources allocated and used by this resolver.
   */
  @Override
  void close();
}
