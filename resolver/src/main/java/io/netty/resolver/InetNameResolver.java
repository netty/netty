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

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.SocketUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;

/**
 * A skeletal {@link NameResolver} implementation that resolves {@link InetAddress}.
 */
public abstract class InetNameResolver extends SimpleNameResolver<InetAddress> {

    private final InetAddress loopbackAddress;

    private volatile AddressResolver<InetSocketAddress> addressResolver;

    /**
     * @param executor the {@link EventExecutor} which is used to notify the listeners of the {@link Future} returned
     *                 by {@link #resolve(String)}
     */
    protected InetNameResolver(EventExecutor executor) {
        super(executor);
        loopbackAddress = SocketUtils.loopbackAddress();
    }

    /**
     * Returns the {@link InetAddress} for loopback.
     */
    protected InetAddress loopbackAddress() {
        return loopbackAddress;
    }

    @Override
    public Future<InetAddress> resolve(String inetHost, Promise<InetAddress> promise) {
        if (inetHost == null || inetHost.isEmpty()) {
            // If an empty hostname is used we should use "localhost", just like InetAddress.getByName(...) does.
            return promise.setSuccess(loopbackAddress());
        }
        return super.resolve(inetHost, promise);
    }

    @Override
    public Future<List<InetAddress>> resolveAll(String inetHost, Promise<List<InetAddress>> promise) {
        if (inetHost == null || inetHost.isEmpty()) {
            // If an empty hostname is used we should use "localhost", just like InetAddress.getByName(...) does.
            return promise.setSuccess(Collections.singletonList(loopbackAddress()));
        }
        return super.resolveAll(inetHost, promise);
    }

    /**
     * Return a {@link AddressResolver} that will use this name resolver underneath.
     * It's cached internally, so the same instance is always returned.
     */
    public AddressResolver<InetSocketAddress> asAddressResolver() {
        AddressResolver<InetSocketAddress> result = addressResolver;
        if (result == null) {
            synchronized (this) {
                result = addressResolver;
                if (result == null) {
                    addressResolver = result = new InetSocketAddressResolver(executor(), this);
                }
            }
        }
        return result;
    }
}
