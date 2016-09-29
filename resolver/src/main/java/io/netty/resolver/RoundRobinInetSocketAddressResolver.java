/*
 * Copyright 2016 The Netty Project
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
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * A {@link AbstractAddressResolver} that resolves {@link InetAddress} and chooses a single address randomly if multiple
 * are returned by the {@link NameResolver}.
 */
@UnstableApi
public class RoundRobinInetSocketAddressResolver extends InetSocketAddressResolver {

    /**
     * @param executor the {@link EventExecutor} which is used to notify the listeners of the {@link Future} returned by
     * {@link #resolve(java.net.SocketAddress)}
     * @param nameResolver the {@link NameResolver} used for name resolution
     */
    public RoundRobinInetSocketAddressResolver(EventExecutor executor, NameResolver<InetAddress> nameResolver) {
        super(executor, nameResolver);
    }

    @Override
    protected void doResolve(final InetSocketAddress unresolvedAddress, final Promise<InetSocketAddress> promise)
            throws Exception {
        // hijack the doResolve request, but do a doResolveAll request under the hood
        // Note that InetSocketAddress.getHostName() will never incur a reverse lookup here,
        // because an unresolved address always has a host name.
        nameResolver.resolveAll(unresolvedAddress.getHostName())
                    .addListener(new FutureListener<List<InetAddress>>() {
                        @Override
                        public void operationComplete(Future<List<InetAddress>> future) throws Exception {
                            if (future.isSuccess()) {
                                List<InetAddress> inetAddresses = future.getNow();
                                int numAddresses = inetAddresses.size();
                                if (numAddresses == 0) {
                                    promise.setFailure(new UnknownHostException(unresolvedAddress.getHostName()));
                                } else {
                                    // if there are multiple addresses: we shall pick one at random
                                    // this is to support the round robin distribution
                                    int index =
                                            (numAddresses == 1)? 0 : ThreadLocalRandom.current().nextInt(numAddresses);
                                    promise.setSuccess(new InetSocketAddress(inetAddresses.get(index),
                                                                             unresolvedAddress.getPort()));
                                }
                            } else {
                                promise.setFailure(future.cause());
                            }
                        }
                    });
    }
}
