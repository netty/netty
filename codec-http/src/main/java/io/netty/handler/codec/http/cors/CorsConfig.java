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

import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.internal.StringUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for Cross-Origin Resource Sharing (CORS).
 */
public final class CorsConfig {

    private final String origin;
    private final boolean enabled;
    private final Set<String> exposeHeaders;
    private final boolean allowCredentials;
    private final long maxAge;
    private final Set<HttpMethod> allowedRequestMethods;
    private final Set<String> allowedRequestHeaders;
    private final boolean allowNullOrigin;

    private CorsConfig(final Builder builder) {
        origin = builder.origin;
        enabled = builder.enabled;
        exposeHeaders = builder.exposeHeaders;
        allowCredentials = builder.allowCredentials;
        maxAge = builder.maxAge;
        allowedRequestMethods = builder.requestMethods;
        allowedRequestHeaders = builder.requestHeaders;
        allowNullOrigin = builder.allowNullOrigin;
    }

    /**
     * Determines if support for CORS is enabled.
     *
     * @return {@code true} if support for CORS is enabled, false otherwise.
     */
    public boolean isCorsSupportEnabled() {
        return enabled;
    }

    /**
     * Returns the allowed origin. This can either be a wildcard or an origin value.
     *
     * @return the value that will be used for the CORS response header 'Access-Control-Allow-Origin'
     */
    public String origin() {
        return origin;
    }

    /**
     * Web browsers may set the 'Origin' request header to 'null' if a resource is loaded
     * from the local file system.
     * If isNullOriginAllowed is true then the server will response with the wildcard for the
     * the CORS response header 'Access-Control-Allow-Origin'.
     *
     * @return {@code true} if a 'null' origin should be supported.
     */
    public boolean isNullOriginAllowed() {
        return allowNullOrigin;
    }

    /**
     * Returns a set of headers to be exposed to calling clients.
     *
     * During a simple CORS request only certain response headers are made available by the
     * browser, for example using:
     * <pre>
     * xhr.getResponseHeader("Content-Type");
     * </pre>
     * The headers that are available by default are:
     * <ul>
     * <li>Cache-Control</li>
     * <li>Content-Language</li>
     * <li>Content-Type</li>
     * <li>Expires</li>
     * <li>Last-Modified</li>
     * <li>Pragma</li>
     * </ul>
     * To expose other headers they need to be specified which what this method enables by adding the headers
     * to the CORS 'Access-Control-Expose-Headers' response header.
     *
     * @return {@code List<String>} a list of the headers to expose.
     */
    public Set<String> exposedHeaders() {
        return Collections.unmodifiableSet(exposeHeaders);
    }

    /**
     * Determines if cookies are supported for CORS requests.
     *
     * By default cookies are not included in CORS requests but if isCredentialsAllowed returns true cookies will
     * be added to CORS requests. Setting this value to true will set the CORS 'Access-Control-Allow-Credentials'
     * response header to true.
     *
     * @return {@code true} if cookies are supported.
     */
    public boolean isCredentialsAllowed() {
        return allowCredentials;
    }

    /**
     * Gets the maxAge setting.
     *
     * When making a preflight request the client has to perform two request with can be inefficient. This setting
     * will set the CORS 'Access-Control-Max-Age' response header and enables the caching of the preflight response
     * for the specified time. During this time no preflight request will be made.
     *
     * @return {@code long} the time in seconds that a preflight request may be cached.
     */
    public long maxAge() {
        return maxAge;
    }

    /**
     * Returns the allowed set of Request Methods. The Http methods that should be returned in the
     *
     * CORS 'Access-Control-Request-Method' response header.
     *
     * @return {@code Set} strings that represent the allowed Request Methods.
     */
    public Set<HttpMethod> allowedRequestMethods() {
        return Collections.unmodifiableSet(allowedRequestMethods);
    }

    /**
     * Returns the allowed set of Request Headers.
     *
     * The header names returned from this method will be used to set the CORS 'Access-Control-Allow-Headers'
     * response header.
     *
     * @return {@code Set} of strings that represent the allowed Request Headers.
     */
    public Set<String> allowedRequestHeaders() {
        return Collections.unmodifiableSet(allowedRequestHeaders);
    }

    public String toString() {
        return StringUtil.simpleClassName(this) + "[enabled=" + enabled +
                ", origin=" + origin +
                ", exposedHeaders=" + exposeHeaders +
                ", isCredentialsAllowed=" + allowCredentials +
                ", maxAge=" + maxAge +
                ", allowedRequestMethods=" + allowedRequestMethods +
                ", allowedRequestHeaders=" + allowedRequestHeaders + ']';
    }

    public static Builder anyOrigin() {
        return new Builder("*");
    }

    public static Builder withOrigin(final String origin) {
        return new Builder(origin);
    }

    public static class Builder {

        private final String origin;
        private boolean allowNullOrigin;
        private boolean enabled = true;
        private boolean allowCredentials;
        private final Set<String> exposeHeaders = new HashSet<String>();
        private long maxAge;
        private final Set<HttpMethod> requestMethods = new HashSet<HttpMethod>();
        private final Set<String> requestHeaders = new HashSet<String>();

        public Builder(final String origin) {
            this.origin = origin;
        }

        public Builder allowNullOrigin() {
            allowNullOrigin = true;
            return this;
        }

        public Builder disable() {
            enabled = false;
            return this;
        }

        public Builder exposeHeaders(final String... headers) {
            exposeHeaders.addAll(Arrays.asList(headers));
            return this;
        }

        public Builder allowCredentials() {
            allowCredentials = true;
            return this;
        }

        public Builder maxAge(final long max) {
            maxAge = max;
            return this;
        }

        public Builder allowedRequestMethods(final HttpMethod... methods) {
            requestMethods.addAll(Arrays.asList(methods));
            return this;
        }

        public Builder allowedRequestHeaders(final String... headers) {
            requestHeaders.addAll(Arrays.asList(headers));
            return this;
        }

        public CorsConfig build() {
            return new CorsConfig(this);
        }
    }

}
