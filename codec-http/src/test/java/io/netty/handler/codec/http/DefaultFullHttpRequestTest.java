/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DefaultFullHttpRequestTest {

    private static HttpHeadersFactory countingFactory(final HttpHeadersFactory delegate, final AtomicInteger counter) {
        return new HttpHeadersFactory() {
            @Override
            public HttpHeaders newHeaders() {
                counter.incrementAndGet();
                return delegate.newHeaders();
            }

            @Override
            public HttpHeaders newEmptyHeaders() {
                counter.incrementAndGet();
                return delegate.newEmptyHeaders();
            }
        };
    }

    @Test
    public void trailingHeadersAreLazilyAllocatedWhenConstructedWithFactory() {
        AtomicInteger trailersAllocations = new AtomicInteger();
        HttpHeadersFactory countingTrailers =
                countingFactory(DefaultHttpHeadersFactory.trailersFactory(), trailersAllocations);

        DefaultFullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER,
                DefaultHttpHeadersFactory.headersFactory(), countingTrailers);

        // Most requests never carry trailers; the factory must not be invoked at construction time.
        assertEquals(0, trailersAllocations.get(),
                "trailing headers should not be eagerly allocated when constructed via factory");

        HttpHeaders trailers = req.trailingHeaders();
        assertNotNull(trailers);
        assertEquals(1, trailersAllocations.get(),
                "trailing headers should be allocated on first access");

        HttpHeaders trailersAgain = req.trailingHeaders();
        assertSame(trailers, trailersAgain, "trailing headers must be cached after first access");
        assertEquals(1, trailersAllocations.get(),
                "subsequent trailingHeaders() calls must not allocate again");
    }

    @Test
    public void trailingHeadersFromExplicitConstructorAreReturnedAsIs() {
        HttpHeaders explicitTrailers = DefaultHttpHeadersFactory.trailersFactory().newHeaders();
        explicitTrailers.add("x-trailer", "v");

        DefaultFullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER,
                DefaultHttpHeadersFactory.headersFactory().newHeaders(), explicitTrailers);

        assertSame(explicitTrailers, req.trailingHeaders(),
                "explicit trailing headers must be returned without re-allocation");
        assertEquals("v", req.trailingHeaders().get("x-trailer"));
    }

    /**
     * Race-exposing stress test in the style of {@code UniqueIpFilterTest}: two threads start in
     * lock-step on a {@link CyclicBarrier} and concurrently call
     * {@link DefaultFullHttpRequest#trailingHeaders()} on a freshly-constructed request. Both
     * threads MUST observe the same {@link HttpHeaders} instance — otherwise mutations applied
     * by the loser of the race would be silently dropped by subsequent readers seeing the
     * winner's instance.
     */
    @Test
    public void trailingHeadersIsRaceFreeOnFirstAccess() throws ExecutionException, InterruptedException {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10000; round++) {
                final DefaultFullHttpRequest req = new DefaultFullHttpRequest(
                        HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER,
                        DefaultHttpHeadersFactory.headersFactory(),
                        DefaultHttpHeadersFactory.trailersFactory());

                Future<HttpHeaders> f1 = trailingHeadersAsync(barrier, executorService, req);
                Future<HttpHeaders> f2 = trailingHeadersAsync(barrier, executorService, req);

                HttpHeaders h1 = f1.get();
                HttpHeaders h2 = f2.get();

                assertSame(h1, h2,
                        "concurrent first-access callers of trailingHeaders() must observe the "
                        + "same instance (round " + round + ')');
                assertSame(h1, req.trailingHeaders(),
                        "subsequent trailingHeaders() must return the same instance the racers saw "
                        + "(round " + round + ')');

                barrier.reset();
            }
        } finally {
            executorService.shutdown();
        }
    }

    private static Future<HttpHeaders> trailingHeadersAsync(final CyclicBarrier barrier,
                                                            ExecutorService executorService,
                                                            final DefaultFullHttpRequest req) {
        return executorService.submit(new Callable<HttpHeaders>() {
            @Override
            public HttpHeaders call() throws Exception {
                barrier.await();
                return req.trailingHeaders();
            }
        });
    }
}
