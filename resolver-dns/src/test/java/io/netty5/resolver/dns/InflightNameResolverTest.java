/*
 * Copyright 2022 The Netty Project
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

package io.netty5.resolver.dns;

import io.netty5.resolver.NameResolver;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InflightNameResolverTest {

    @Test
    void testResolve() throws InterruptedException {
        InflightNameResolver<InetSocketAddress> inflightNameResolver =
                new InflightNameResolver<>(GlobalEventExecutor.INSTANCE, new TestNameResolver(),
                        new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        ExecutorService executorService1 = Executors.newSingleThreadExecutor();
        ExecutorService executorService2 = Executors.newSingleThreadExecutor();
        CountDownLatch addressResolvedLatch = new CountDownLatch(2);
        try {
            executorService1.execute(() -> inflightNameResolver.resolve("localhost")
                    .addListener(transferResult(addressResolvedLatch)));
            executorService2.execute(() -> inflightNameResolver.resolve("localhost")
                    .addListener(transferResult(addressResolvedLatch)));
            assertThat(addressResolvedLatch.await(5, TimeUnit.SECONDS))
                    .as("addressResolvedLatch.await")
                    .isTrue();
        } finally {
            executorService1.shutdown();
            executorService2.shutdown();
        }
    }

    private static FutureListener<InetSocketAddress> transferResult(CountDownLatch addressResolvedLatch) {
        return f -> {
            if (f.isSuccess()) {
                addressResolvedLatch.countDown();
            }
        };
    }

    private static final class TestNameResolver implements NameResolver<InetSocketAddress> {

        @Override
        public Future<InetSocketAddress> resolve(String inetHost) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<InetSocketAddress> resolve(String inetHost, Promise<InetSocketAddress> promise) {
            return promise.setSuccess(new InetSocketAddress(inetHost, 80)).asFuture();
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(String inetHost) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<List<InetSocketAddress>> resolveAll(String inetHost, Promise<List<InetSocketAddress>> promise) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
