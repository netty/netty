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
package io.netty.handler.codec.http.cors;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Test;

import static io.netty.handler.codec.http.cors.CorsConfig.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

public class CorsConfigTest {

    @Test
    public void disabled() {
        final CorsConfig cors = withAnyOrigin().disable().build();
        assertThat(cors.isCorsSupportEnabled(), is(false));
    }

    @Test
    public void anyOrigin() {
        final CorsConfig cors = withAnyOrigin().build();
        assertThat(cors.isAnyOriginSupported(), is(true));
        assertThat(cors.origin(), is("*"));
        assertThat(cors.origins().isEmpty(), is(true));
    }

    @Test
    public void wildcardOrigin() {
        final CorsConfig cors = withOrigin("*").build();
        assertThat(cors.isAnyOriginSupported(), is(true));
        assertThat(cors.origin(), equalTo("*"));
        assertThat(cors.origins().isEmpty(), is(true));
    }

    @Test
    public void origin() {
        final CorsConfig cors = withOrigin("http://localhost:7888").build();
        assertThat(cors.origin(), is(equalTo("http://localhost:7888")));
        assertThat(cors.isAnyOriginSupported(), is(false));
    }

    @Test
    public void origins() {
        final String[] origins = {"http://localhost:7888", "https://localhost:7888"};
        final CorsConfig cors = withOrigins(origins).build();
        assertThat(cors.origins(), hasItems(origins));
        assertThat(cors.isAnyOriginSupported(), is(false));
    }

    @Test
    public void exposeHeaders() {
        final CorsConfig cors = withAnyOrigin().exposeHeaders("custom-header1", "custom-header2").build();
        assertThat(cors.exposedHeaders(), hasItems("custom-header1", "custom-header2"));
    }

    @Test
    public void allowCredentials() {
        final CorsConfig cors = withAnyOrigin().allowCredentials().build();
        assertThat(cors.isCredentialsAllowed(), is(true));
    }

    @Test
    public void maxAge() {
        final CorsConfig cors = withAnyOrigin().maxAge(3000).build();
        assertThat(cors.maxAge(), is(3000L));
    }

    @Test
    public void requestMethods() {
        final CorsConfig cors = withAnyOrigin().allowedRequestMethods(HttpMethod.POST, HttpMethod.GET).build();
        assertThat(cors.allowedRequestMethods(), hasItems(HttpMethod.POST, HttpMethod.GET));
    }

    @Test
    public void requestHeaders() {
        final CorsConfig cors = withAnyOrigin().allowedRequestHeaders("preflight-header1", "preflight-header2").build();
        assertThat(cors.allowedRequestHeaders(), hasItems("preflight-header1", "preflight-header2"));
    }

    @Test
    public void preflightResponseHeadersSingleValue() {
        final CorsConfig cors = withAnyOrigin().preflightResponseHeader("SingleValue", "value").build();
        assertThat(cors.preflightResponseHeaders().get("SingleValue"), equalTo("value"));
    }

    @Test
    public void preflightResponseHeadersMultipleValues() {
        final CorsConfig cors = withAnyOrigin().preflightResponseHeader("MultipleValues", "value1", "value2").build();
        assertThat(cors.preflightResponseHeaders().getAll("MultipleValues"), hasItems("value1", "value2"));
    }

    @Test
    public void defaultPreflightResponseHeaders() {
        final CorsConfig cors = withAnyOrigin().build();
        assertThat(cors.preflightResponseHeaders().get(Names.DATE), is(notNullValue()));
        assertThat(cors.preflightResponseHeaders().get(Names.CONTENT_LENGTH), is("0"));
    }

    @Test
    public void emptyPreflightResponseHeaders() {
        final CorsConfig cors = withAnyOrigin().noPreflightResponseHeaders().build();
        assertThat(cors.preflightResponseHeaders(), equalTo(HttpHeaders.EMPTY_HEADERS));
    }

    @Test (expected = IllegalArgumentException.class)
    public void shouldThrowIfValueIsNull() {
        withOrigin("*").preflightResponseHeader("HeaderName", new Object[]{null}).build();
    }

    @Test
    public void shortCurcuit() {
        final CorsConfig cors = withOrigin("http://localhost:8080").shortCurcuit().build();
        assertThat(cors.isShortCurcuit(), is(true));
    }

}
