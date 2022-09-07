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
package io.netty5.handler.codec.http.cors;

import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

import static io.netty5.handler.codec.http.cors.CorsConfigBuilder.forAnyOrigin;
import static io.netty5.handler.codec.http.cors.CorsConfigBuilder.forOrigin;
import static io.netty5.handler.codec.http.cors.CorsConfigBuilder.forOrigins;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CorsConfigTest {

    @Test
    public void disabled() {
        final CorsConfig cors = forAnyOrigin().disable().build();
        assertFalse(cors.isCorsSupportEnabled());
    }

    @Test
    public void anyOrigin() {
        final CorsConfig cors = forAnyOrigin().build();
        assertTrue(cors.isAnyOriginSupported());
        assertThat(cors.origin()).isEqualTo("*");
        assertThat(cors.origins()).isEmpty();
    }

    @Test
    public void wildcardOrigin() {
        final CorsConfig cors = forOrigin("*").build();
        assertTrue(cors.isAnyOriginSupported());
        assertThat(cors.origin()).isEqualTo("*");
        assertThat(cors.origins()).isEmpty();
    }

    @Test
    public void origin() {
        final CorsConfig cors = forOrigin("http://localhost:7888").build();
        assertThat(cors.origin()).isEqualTo("http://localhost:7888");
        assertFalse(cors.isAnyOriginSupported());
    }

    @Test
    public void origins() {
        final String[] origins = {"http://localhost:7888", "https://localhost:7888"};
        final CorsConfig cors = forOrigins(origins).build();
        assertThat(cors.origins()).contains(origins);
        assertFalse(cors.isAnyOriginSupported());
    }

    @Test
    public void exposeHeaders() {
        final CorsConfig cors = forAnyOrigin().exposeHeaders("custom-header1", "custom-header2").build();
        assertThat(cors.exposedHeaders()).contains("custom-header1", "custom-header2");
    }

    @Test
    public void allowCredentials() {
        final CorsConfig cors = forAnyOrigin().allowCredentials().build();
        assertTrue(cors.isCredentialsAllowed());
    }

    @Test
    public void maxAge() {
        final CorsConfig cors = forAnyOrigin().maxAge(3000).build();
        assertThat(cors.maxAge()).isEqualTo(3000);
    }

    @Test
    public void requestMethods() {
        final CorsConfig cors = forAnyOrigin().allowedRequestMethods(HttpMethod.POST, HttpMethod.GET).build();
        assertThat(cors.allowedRequestMethods()).contains(HttpMethod.POST, HttpMethod.GET);
    }

    @Test
    public void requestHeaders() {
        final CorsConfig cors = forAnyOrigin().allowedRequestHeaders("preflight-header1", "preflight-header2").build();
        assertThat(cors.allowedRequestHeaders()).contains("preflight-header1", "preflight-header2");
    }

    @Test
    public void preflightResponseHeadersSingleValue() {
        final CorsConfig cors = forAnyOrigin().preflightResponseHeader("SingleValue", "value").build();
        assertThat(cors.preflightResponseHeaders().get("SingleValue")).isEqualTo("value");
    }

    @Test
    public void preflightResponseHeadersMultipleValues() {
        final CorsConfig cors = forAnyOrigin().preflightResponseHeader("MultipleValues", "value1", "value2").build();
        assertThat(cors.preflightResponseHeaders().values("MultipleValues")).contains("value1", "value2");
    }

    @Test
    public void defaultPreflightResponseHeaders() {
        final CorsConfig cors = forAnyOrigin().build();
        assertThat(cors.preflightResponseHeaders().get(HttpHeaderNames.DATE)).isNotNull();
        assertThat(cors.preflightResponseHeaders().get(HttpHeaderNames.CONTENT_LENGTH)).isEqualToIgnoringCase(
                HttpHeaderValues.ZERO);
    }

    @Test
    public void emptyPreflightResponseHeaders() {
        final CorsConfig cors = forAnyOrigin().noPreflightResponseHeaders().build();
        assertThat(cors.preflightResponseHeaders()).isEmpty();
    }

    @Test
    public void shouldThrowIfValueIsNull() {
        assertThrows(IllegalArgumentException.class,
            () -> forOrigin("*").preflightResponseHeader("HeaderName", new CharSequence[]{null}).build());
    }

    @Test
    public void shortCircuit() {
        final CorsConfig cors = forOrigin("http://localhost:8080").shortCircuit().build();
        assertTrue(cors.isShortCircuit());
    }

}
