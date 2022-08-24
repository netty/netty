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

import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.internal.StringUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Configuration for Cross-Origin Resource Sharing (CORS).
 */
public final class CorsConfig {

    private final Set<String> origins;
    private final boolean anyOrigin;
    private final boolean enabled;
    private final Set<String> exposeHeaders;
    private final boolean allowCredentials;
    private final long maxAge;
    private final Set<HttpMethod> allowedRequestMethods;
    private final Set<String> allowedRequestHeaders;
    private final boolean allowNullOrigin;
    private final Map<CharSequence, List<Callable<CharSequence>>> preflightHeaders;
    private final boolean shortCircuit;
    private final boolean allowPrivateNetwork;

    CorsConfig(final CorsConfigBuilder builder) {
        origins = new LinkedHashSet<>(builder.origins);
        anyOrigin = builder.anyOrigin;
        enabled = builder.enabled;
        exposeHeaders = builder.exposeHeaders;
        allowCredentials = builder.allowCredentials;
        maxAge = builder.maxAge;
        allowedRequestMethods = builder.requestMethods;
        allowedRequestHeaders = builder.requestHeaders;
        allowNullOrigin = builder.allowNullOrigin;
        preflightHeaders = builder.preflightHeaders;
        shortCircuit = builder.shortCircuit;
        allowPrivateNetwork = builder.allowPrivateNetwork;
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
     * Determines whether a wildcard origin, '*', is supported.
     *
     * @return {@code boolean} true if any origin is allowed.
     */
    public boolean isAnyOriginSupported() {
        return anyOrigin;
    }

    /**
     * Returns the allowed origin. This can either be a wildcard or an origin value.
     *
     * @return the value that will be used for the CORS response header 'Access-Control-Allow-Origin'
     */
    public String origin() {
        return origins.isEmpty() ? "*" : origins.iterator().next();
    }

    /**
     * Returns the set of allowed origins.
     *
     * @return {@code Set} the allowed origins.
     */
    public Set<String> origins() {
        return origins;
    }

    /**
     * Web browsers may set the 'Origin' request header to 'null' if a resource is loaded
     * from the local file system.
     * <p>
     * If isNullOriginAllowed is true then the server will response with the wildcard for the
     * the CORS response header 'Access-Control-Allow-Origin'.
     *
     * @return {@code true} if a 'null' origin should be supported.
     */
    public boolean isNullOriginAllowed() {
        return allowNullOrigin;
    }

    /**
     * Web browsers may set the 'Access-Control-Request-Private-Network' request header if a resource is loaded
     * from a local network.
     * By default direct access to private network endpoints from public websites is not allowed.
     * <p>
     * If isPrivateNetworkAllowed is true the server will response with the CORS response header
     * 'Access-Control-Request-Private-Network'.
     *
     * @return {@code true} if private network access should be allowed.
     */
    public boolean isPrivateNetworkAllowed() {
        return allowPrivateNetwork;
    }

    /**
     * Returns a set of headers to be exposed to calling clients.
     * <p>
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
     * To expose other headers they need to be specified, which is what this method enables by
     * adding the headers names to the CORS 'Access-Control-Expose-Headers' response header.
     *
     * @return {@code List<String>} a list of the headers to expose.
     */
    public Set<String> exposedHeaders() {
        return Collections.unmodifiableSet(exposeHeaders);
    }

    /**
     * Determines if cookies are supported for CORS requests.
     * <p>
     * By default cookies are not included in CORS requests but if isCredentialsAllowed returns
     * true cookies will be added to CORS requests. Setting this value to true will set the
     * CORS 'Access-Control-Allow-Credentials' response header to true.
     * <p>
     * Please note that cookie support needs to be enabled on the client side as well.
     * The client needs to opt-in to send cookies by calling:
     * <pre>
     * xhr.withCredentials = true;
     * </pre>
     * The default value for 'withCredentials' is false in which case no cookies are sent.
     * Setting this to true will included cookies in cross origin requests.
     *
     * @return {@code true} if cookies are supported.
     */
    public boolean isCredentialsAllowed() {
        return allowCredentials;
    }

    /**
     * Gets the maxAge setting.
     * <p>
     * When making a preflight request the client has to perform two request with can be inefficient.
     * This setting will set the CORS 'Access-Control-Max-Age' response header and enables the
     * caching of the preflight response for the specified time. During this time no preflight
     * request will be made.
     *
     * @return {@code long} the time in seconds that a preflight request may be cached.
     */
    public long maxAge() {
        return maxAge;
    }

    /**
     * Returns the allowed set of Request Methods. The Http methods that should be returned in the
     * CORS 'Access-Control-Request-Method' response header.
     *
     * @return {@code Set} of {@link HttpMethod}s that represent the allowed Request Methods.
     */
    public Set<HttpMethod> allowedRequestMethods() {
        return Collections.unmodifiableSet(allowedRequestMethods);
    }

    /**
     * Returns the allowed set of Request Headers.
     * <p>
     * The header names returned from this method will be used to set the CORS
     * 'Access-Control-Allow-Headers' response header.
     *
     * @return {@code Set<String>} of strings that represent the allowed Request Headers.
     */
    public Set<String> allowedRequestHeaders() {
        return Collections.unmodifiableSet(allowedRequestHeaders);
    }

    /**
     * Returns HTTP response headers that should be added to a CORS preflight response.
     *
     * @return {@link HttpHeaders} the HTTP response headers to be added.
     */
    public HttpHeaders preflightResponseHeaders() {
        if (preflightHeaders.isEmpty()) {
            return HttpHeaders.emptyHeaders();
        }
        final HttpHeaders preflightHeaders = HttpHeaders.newHeaders();
        for (Entry<CharSequence, List<Callable<CharSequence>>> entry : this.preflightHeaders.entrySet()) {
            for (Callable<CharSequence> generator : entry.getValue()) {
                preflightHeaders.add(entry.getKey(), getValue(generator));
            }
        }
        return preflightHeaders;
    }

    /**
     * Determines whether a CORS request should be rejected if it's invalid before being
     * further processing.
     * <p>
     * CORS headers are set after a request is processed. This may not always be desired
     * and this setting will check that the Origin is valid and if it is not valid no
     * further processing will take place, and an error will be returned to the calling client.
     *
     * @return {@code true} if a CORS request should short-circuit upon receiving an invalid Origin header.
     */
    public boolean isShortCircuit() {
        return shortCircuit;
    }

    private static CharSequence getValue(final Callable<CharSequence> callable) {
        try {
            return callable.call();
        } catch (final Exception e) {
            throw new IllegalStateException("Could not generate value for callable [" + callable + ']', e);
        }
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[enabled=" + enabled +
                ", origins=" + origins +
                ", anyOrigin=" + anyOrigin +
                ", exposedHeaders=" + exposeHeaders +
                ", isCredentialsAllowed=" + allowCredentials +
                ", maxAge=" + maxAge +
                ", allowedRequestMethods=" + allowedRequestMethods +
                ", allowedRequestHeaders=" + allowedRequestHeaders +
                ", preflightHeaders=" + preflightHeaders +
                ", isPrivateNetworkAllowed=" + allowPrivateNetwork + ']';
    }
}
