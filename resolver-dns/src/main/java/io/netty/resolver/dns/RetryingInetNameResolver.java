/*
 * Copyright 2017 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.resolver.InetNameResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.util.List;

/**
 * {@link InetNameResolver} implementation which allows to add retry cababilities to a {@link DnsNameResolver}.
 */
@UnstableApi
public final class RetryingInetNameResolver extends InetNameResolver {

    private final DnsNameResolver resolver;
    private final int retries;

    /**
     * Create a new instance.
     *
     * @param resolver the {@link DnsNameResolver} which will be used for the queries.
     * @param retries the number of retries that should be used for timeouts / IO errors.
     */
    public RetryingInetNameResolver(DnsNameResolver resolver, int retries) {
        super(resolver.executor());
        this.resolver = resolver;
        this.retries = ObjectUtil.checkPositiveOrZero(retries, "retries");
    }

    @Override
    protected void doResolve(final String inetHost, final Promise<InetAddress> promise) throws Exception {
        resolver.resolve(inetHost).addListener(new RetryFutureListener<InetAddress>(retries, inetHost, promise) {
            @Override
            protected Future<InetAddress> resolveAgain(String inetHost) {
                return resolver.resolve(inetHost);
            }
        });
    }

    @Override
    protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {
        resolver.resolveAll(inetHost).addListener(
                new RetryFutureListener<List<InetAddress>>(retries, inetHost, promise) {
            @Override
            protected Future<List<InetAddress>> resolveAgain(String inetHost) {
                return resolver.resolveAll(inetHost);
            }
        });
    }

    private abstract static class RetryFutureListener<T> implements FutureListener<T> {

        private final String inetHost;
        private final Promise<T> promise;
        private int retries;

        RetryFutureListener(int retries, String inetHost, Promise<T> promise) {
            this.retries = retries;
            this.inetHost = inetHost;
            this.promise = promise;
        }

        @Override
        public void operationComplete(Future<T> future) throws Exception {
            Throwable cause = future.cause();
            if (cause == null) {
                promise.setSuccess(future.getNow());
            } else if (DnsNameResolver.isTransportOrTimeoutError(cause) && --retries >= 0) {
                resolveAgain(inetHost).addListener(this);
            } else {
                promise.setFailure(cause);
            }
        }

        protected abstract Future<T> resolveAgain(String inetHost);
    }
}
