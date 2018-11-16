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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class RequestCachingPolicyTest {

    private RequestCachingPolicy requestPolicy;

    @Before
    public void setUp() {
        requestPolicy = new RequestCachingPolicy();
    }

    @Test
    public void getRequestCanBeServedFromCache() {
        final DefaultFullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "uri", true);
        assertThat(requestPolicy.canBeServedFromCache(request), is(true));
    }

    @Test
    public void headRequestCanBeServedFromCache() {
        final DefaultFullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "uri", true);
        assertThat(requestPolicy.canBeServedFromCache(request), is(true));
    }

    @Test
    public void postRequestCanNotBeServedFromCache() {
        final DefaultFullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "uri", true);
        assertThat(requestPolicy.canBeServedFromCache(request), is(false));
    }

    @Test
    public void http1RequestCanNotBeServedFromCache() {
        final DefaultFullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "uri", true);
        assertThat(requestPolicy.canBeServedFromCache(request), is(false));
    }

    @Test
    public void cacheControlNoStoreRequestCanNotBeServedFromCache() {
        final ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(true, CACHE_CONTROL, NO_STORE);
        final ByteBuf content = unreleasableBuffer(copiedBuffer("Hello World", CharsetUtil.UTF_8));
        final DefaultFullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "uri", content, headers,
                                           new ReadOnlyHttpHeaders(true));
        assertThat(requestPolicy.canBeServedFromCache(request), is(false));
    }
}
