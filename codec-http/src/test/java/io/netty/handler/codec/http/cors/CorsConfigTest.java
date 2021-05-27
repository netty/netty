/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http.cors;

import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static io.netty.handler.codec.http.cors.CorsConfigBuilder.forAnyOrigin;
import static io.netty.handler.codec.http.cors.CorsConfigBuilder.forOrigin;
import static io.netty.handler.codec.http.cors.CorsConfigBuilder.forOrigins;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CorsConfigTest {

    @Test
    public void disabled() {
        final CorsConfig cors = forAnyOrigin().disable().build();
        assertThat(cors.isCorsSupportEnabled(), is(false));
    }

    @Test
    public void anyOrigin() {
        final CorsConfig cors = forAnyOrigin().build();
        assertThat(cors.isAnyOriginSupported(), is(true));
        assertThat(cors.origin(), is("*"));
        assertThat(cors.origins().isEmpty(), is(true));
    }

    @Test
    public void wildcardOrigin() {
        final CorsConfig cors = forOrigin("*").build();
        assertThat(cors.isAnyOriginSupported(), is(true));
        assertThat(cors.origin(), equalTo("*"));
        assertThat(cors.origins().isEmpty(), is(true));
    }

    @Test
    public void origin() {
        final CorsConfig cors = forOrigin("http://localhost:7888").build();
        assertThat(cors.origin(), is(equalTo("http://localhost:7888")));
        assertThat(cors.isAnyOriginSupported(), is(false));
    }

    @Test
    public void origins() {
        final String[] origins = {"http://localhost:7888", "https://localhost:7888"};
        final CorsConfig cors = forOrigins(origins).build();
        assertThat(cors.origins(), hasItems(origins));
        assertThat(cors.isAnyOriginSupported(), is(false));
    }

    @Test
    public void exposeHeaders() {
        final CorsConfig cors = forAnyOrigin().exposeHeaders("custom-header1", "custom-header2").build();
        assertThat(cors.exposedHeaders(), hasItems("custom-header1", "custom-header2"));
    }

    @Test
    public void allowCredentials() {
        final CorsConfig cors = forAnyOrigin().allowCredentials().build();
        assertThat(cors.isCredentialsAllowed(), is(true));
    }

    @Test
    public void maxAge() {
        final CorsConfig cors = forAnyOrigin().maxAge(3000).build();
        assertThat(cors.maxAge(), is(3000L));
    }

    @Test
    public void requestMethods() {
        final CorsConfig cors = forAnyOrigin().allowedRequestMethods(HttpMethod.POST, HttpMethod.GET).build();
        assertThat(cors.allowedRequestMethods(), hasItems(HttpMethod.POST, HttpMethod.GET));
    }

    @Test
    public void requestHeaders() {
        final CorsConfig cors = forAnyOrigin().allowedRequestHeaders("preflight-header1", "preflight-header2").build();
        assertThat(cors.allowedRequestHeaders(), hasItems("preflight-header1", "preflight-header2"));
    }

    @Test
    public void preflightResponseHeadersSingleValue() {
        final CorsConfig cors = forAnyOrigin().preflightResponseHeader("SingleValue", "value").build();
        assertThat(cors.preflightResponseHeaders().get(of("SingleValue")), equalTo("value"));
    }

    @Test
    public void preflightResponseHeadersMultipleValues() {
        final CorsConfig cors = forAnyOrigin().preflightResponseHeader("MultipleValues", "value1", "value2").build();
        assertThat(cors.preflightResponseHeaders().getAll(of("MultipleValues")), hasItems("value1", "value2"));
    }

    @Test
    public void defaultPreflightResponseHeaders() {
        final CorsConfig cors = forAnyOrigin().build();
        assertThat(cors.preflightResponseHeaders().get(HttpHeaderNames.DATE), is(notNullValue()));
        assertThat(cors.preflightResponseHeaders().get(HttpHeaderNames.CONTENT_LENGTH), is("0"));
    }

    @Test
    public void emptyPreflightResponseHeaders() {
        final CorsConfig cors = forAnyOrigin().noPreflightResponseHeaders().build();
        assertThat(cors.preflightResponseHeaders(), equalTo((HttpHeaders) EmptyHttpHeaders.INSTANCE));
    }

    @Test
    public void shouldThrowIfValueIsNull() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                forOrigin("*").preflightResponseHeader("HeaderName", new Object[]{null}).build();
            }
        });
    }

    @Test
    public void shortCircuit() {
        final CorsConfig cors = forOrigin("http://localhost:8080").shortCircuit().build();
        assertThat(cors.isShortCircuit(), is(true));
    }

}
