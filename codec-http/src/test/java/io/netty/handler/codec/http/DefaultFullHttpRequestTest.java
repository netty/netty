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

}
