/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

public final class SockJsTestUtil {

    private SockJsTestUtil() {
    }

    public static void verifyDefaultResponseHeaders(final HttpResponse response, final String contentType) {
        final HttpHeaders headers = response.headers();
        assertThat(headers.get(HttpHeaders.Names.CONTENT_TYPE), equalTo(contentType));
        verifyNoCacheHeaders(response);
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN), equalTo("*"));
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
        assertThat(headers.get(HttpHeaders.Names.SET_COOKIE), is(notNullValue()));
    }

    public static void verifyContentType(final HttpResponse response, final String contentType) {
        assertThat(response.headers().get(HttpHeaders.Names.CONTENT_TYPE), equalTo(contentType));
    }

    public static void verifyNoCacheHeaders(final HttpResponse response) {
        final HttpHeaders headers = response.headers();
        assertThat(headers.get(HttpHeaders.Names.CACHE_CONTROL), containsString("no-store"));
        assertThat(headers.get(HttpHeaders.Names.CACHE_CONTROL), containsString("no-cache"));
        assertThat(headers.get(HttpHeaders.Names.CACHE_CONTROL), containsString("must-revalidate"));
        assertThat(headers.get(HttpHeaders.Names.CACHE_CONTROL), containsString("max-age=0"));
    }

    public static void assertCORSHeaders(final HttpResponse response, final String origin) {
        final HttpHeaders headers = response.headers();
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN), equalTo(origin));
        assertThat(headers.get(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS), equalTo("true"));
    }

}
