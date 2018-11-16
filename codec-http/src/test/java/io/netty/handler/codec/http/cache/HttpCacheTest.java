/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FailedFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SucceededFuture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Date;

import static io.netty.buffer.Unpooled.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class HttpCacheTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private HttpCacheStorage storage;

    @Mock
    private CacheKeyGenerator cacheKeyGenerator;

    private EventExecutor eventExecutor;

    private HttpCache cache;

    private static DefaultFullHttpRequest request(final HttpMethod httpMethod) {
        return request(HttpVersion.HTTP_1_1, httpMethod);
    }

    private static DefaultFullHttpRequest request(final HttpVersion httpVersion, final HttpMethod httpMethod) {
        return new DefaultFullHttpRequest(httpVersion, httpMethod, "/test", EMPTY_BUFFER);
    }

    private static DefaultFullHttpResponse response(final HttpResponseStatus partialContent,
                                                    CharSequence... headerNameValuePairs) {
        final DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, partialContent, EMPTY_BUFFER,
                                            new DefaultHttpHeaders(false), new ReadOnlyHttpHeaders(false));
        response.headers().add(HttpHeaderNames.DATE, DateFormatter.format(new Date()));

        for (int i = 0; i < headerNameValuePairs.length; i += 2) {
            response.headers().add(headerNameValuePairs[i], headerNameValuePairs[i + 1]);
        }

        return response;
    }

    @Before
    public void setUp() throws Exception {
        eventExecutor = ImmediateEventExecutor.INSTANCE;
        cache = new HttpCache(storage, cacheKeyGenerator, eventExecutor);
    }

    @Test
    public void cacheShouldCallStoragePutWithCacheKey() {
        when(cacheKeyGenerator.generateKey(any(HttpRequest.class))).thenReturn("key");
        when(storage.put(anyString(), any(HttpCacheEntry.class), any(Promise.class)))
                .thenReturn(new SucceededFuture(eventExecutor, null));

        cache.cache(request(HttpMethod.GET), response(HttpResponseStatus.OK), new Date(), new Date());

        verify(storage).put(eq("key"), any(HttpCacheEntry.class), any(Promise.class));
    }

    @Test
    public void cacheShouldReturnNullIfPuttingEntryInCacheFails() throws Exception {
        when(cacheKeyGenerator.generateKey(any(HttpRequest.class))).thenReturn("key");

        when(storage.put(anyString(), any(HttpCacheEntry.class), any(Promise.class)))
                .thenReturn(new FailedFuture(eventExecutor, new RuntimeException()));

        final Future<HttpCacheEntry> cacheEntry =
                cache.cache(request(HttpMethod.GET), response(HttpResponseStatus.OK), new Date(), new Date());

        assertThat(cacheEntry.isSuccess(), is(false));
        assertThat(cacheEntry.cause(), instanceOf(RuntimeException.class));
    }

    @Test
    public void getCacheEntryShouldReturnNullIfNotInCache() {
        when(storage.get(anyString(), any(Promise.class))).thenReturn(null);

        assertThat(cache.getCacheEntry(request(HttpMethod.GET),
                                       ImmediateEventExecutor.INSTANCE.<HttpCacheEntry>newPromise()), is(nullValue()));
    }

    @Test
    public void getCacheEntryShouldReturnEntryIfInCache() throws Exception {
        final HttpCacheEntry cacheEntry = mock(HttpCacheEntry.class);
        when(cacheKeyGenerator.generateKey(any(HttpRequest.class))).thenReturn("key");
        when(storage.get(anyString(), any(Promise.class)))
                .thenReturn(new SucceededFuture<HttpCacheEntry>(ImmediateEventExecutor.INSTANCE, cacheEntry));

        final Future<HttpCacheEntry> cacheEntryFuture =
                cache.getCacheEntry(request(HttpMethod.GET),
                                    ImmediateEventExecutor.INSTANCE.<HttpCacheEntry>newPromise());
        assertThat(cacheEntryFuture.get(), is(cacheEntry));
    }

}
