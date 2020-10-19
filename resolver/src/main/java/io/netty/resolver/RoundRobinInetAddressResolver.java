/*
 * Copyright 2016 The Netty Project
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
package io.netty.resolver;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link NameResolver} that resolves {@link InetAddress} and force Round Robin by choosing a single address
 * randomly in {@link #resolve(String)} and {@link #resolve(String, Promise)}
 * if multiple are returned by the {@link NameResolver}.
 * Use {@link #asAddressResolver()} to create a {@link InetSocketAddress} resolver
 */
public class RoundRobinInetAddressResolver extends InetNameResolver {
    private final NameResolver<InetAddress> nameResolver;

    /**
     * @param executor the {@link EventExecutor} which is used to notify the listeners of the {@link Future} returned by
     * {@link #resolve(String)}
     * @param nameResolver the {@link NameResolver} used for name resolution
     */
    public RoundRobinInetAddressResolver(EventExecutor executor, NameResolver<InetAddress> nameResolver) {
        super(executor);
        this.nameResolver = nameResolver;
    }

    @Override
    protected void doResolve(final String inetHost, final Promise<InetAddress> promise) throws Exception {
        // hijack the doResolve request, but do a doResolveAll request under the hood.
        // Note that InetSocketAddress.getHostName() will never incur a reverse lookup here,
        // because an unresolved address always has a host name.
        nameResolver.resolveAll(inetHost).addListener(new FutureListener<List<InetAddress>>() {
            @Override
            public void operationComplete(Future<List<InetAddress>> future) throws Exception {
                if (future.isSuccess()) {
                    List<InetAddress> inetAddresses = future.getNow();
                    int numAddresses = inetAddresses.size();
                    if (numAddresses > 0) {
                        // if there are multiple addresses: we shall pick one by one
                        // to support the round robin distribution
                        promise.setSuccess(inetAddresses.get(randomIndex(numAddresses)));
                    } else {
                        promise.setFailure(new UnknownHostException(inetHost));
                    }
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });
    }

    @Override
    protected void doResolveAll(String inetHost, final Promise<List<InetAddress>> promise) throws Exception {
        nameResolver.resolveAll(inetHost).addListener(new FutureListener<List<InetAddress>>() {
            @Override
            public void operationComplete(Future<List<InetAddress>> future) throws Exception {
                if (future.isSuccess()) {
                    List<InetAddress> inetAddresses = future.getNow();
                    if (!inetAddresses.isEmpty()) {
                        // create a copy to make sure that it's modifiable random access collection
                        List<InetAddress> result = new ArrayList<InetAddress>(inetAddresses);
                        // rotate by different distance each time to force round robin distribution
                        Collections.rotate(result, randomIndex(inetAddresses.size()));
                        promise.setSuccess(result);
                    } else {
                        promise.setSuccess(inetAddresses);
                    }
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });
    }

    private static int randomIndex(int numAddresses) {
        return numAddresses == 1 ? 0 : PlatformDependent.threadLocalRandom().nextInt(numAddresses);
    }

    @Override
    public void close() {
        nameResolver.close();
    }
}
