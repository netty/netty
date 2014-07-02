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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.internal.StringUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private final Map<CharSequence, Callable<?>> preflightHeaders;
    private final boolean shortCurcuit;

    private CorsConfig(final Builder builder) {
        origins = new LinkedHashSet<String>(builder.origins);
        anyOrigin = builder.anyOrigin;
        enabled = builder.enabled;
        exposeHeaders = builder.exposeHeaders;
        allowCredentials = builder.allowCredentials;
        maxAge = builder.maxAge;
        allowedRequestMethods = builder.requestMethods;
        allowedRequestHeaders = builder.requestHeaders;
        allowNullOrigin = builder.allowNullOrigin;
        preflightHeaders = builder.preflightHeaders;
        shortCurcuit = builder.shortCurcuit;
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
     *
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
     *
     * By default cookies are not included in CORS requests but if isCredentialsAllowed returns
     * true cookies will be added to CORS requests. Setting this value to true will set the
     * CORS 'Access-Control-Allow-Credentials' response header to true.
     *
     * Please note that cookie support needs to be enabled on the client side as well.
     * The client needs to opt-in to send cookies by calling:
     * <pre>
     * xhr.withCredentials = true;
     * </pre>
     * The default value for 'withCredentials' is false in which case no cookies are sent.
     * Settning this to true will included cookies in cross origin requests.
     *
     * @return {@code true} if cookies are supported.
     */
    public boolean isCredentialsAllowed() {
        return allowCredentials;
    }

    /**
     * Gets the maxAge setting.
     *
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
     *
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
            return HttpHeaders.EMPTY_HEADERS;
        }
        final HttpHeaders preflightHeaders = new DefaultHttpHeaders();
        for (Entry<CharSequence, Callable<?>> entry : this.preflightHeaders.entrySet()) {
            final Object value = getValue(entry.getValue());
            if (value instanceof Iterable) {
                preflightHeaders.add(entry.getKey(), (Iterable<?>) value);
            } else {
                preflightHeaders.add(entry.getKey(), value);
            }
        }
        return preflightHeaders;
    }

    /**
     * Determines whether a CORS request should be rejected if it's invalid before being
     * further processing.
     *
     * CORS headers are set after a request is processed. This may not always be desired
     * and this setting will check that the Origin is valid and if it is not valid no
     * further processing will take place, and a error will be returned to the calling client.
     *
     * @return {@code true} if a CORS request should short-curcuit upon receiving an invalid Origin header.
     */
    public boolean isShortCurcuit() {
        return shortCurcuit;
    }

    private static <T> T getValue(final Callable<T> callable) {
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
                ", preflightHeaders=" + preflightHeaders + ']';
    }

    /**
     * Creates a Builder instance with it's origin set to '*'.
     *
     * @return Builder to support method chaining.
     */
    public static Builder withAnyOrigin() {
        return new Builder();
    }

    /**
     * Creates a {@link Builder} instance with the specified origin.
     *
     * @return {@link Builder} to support method chaining.
     */
    public static Builder withOrigin(final String origin) {
        if ("*".equals(origin)) {
            return new Builder();
        }
        return new Builder(origin);
    }

    /**
     * Creates a {@link Builder} instance with the specified origins.
     *
     * @return {@link Builder} to support method chaining.
     */
    public static Builder withOrigins(final String... origins) {
        return new Builder(origins);
    }

    /**
     * Builder used to configure and build a CorsConfig instance.
     */
    public static class Builder {

        private final Set<String> origins;
        private final boolean anyOrigin;
        private boolean allowNullOrigin;
        private boolean enabled = true;
        private boolean allowCredentials;
        private final Set<String> exposeHeaders = new HashSet<String>();
        private long maxAge;
        private final Set<HttpMethod> requestMethods = new HashSet<HttpMethod>();
        private final Set<String> requestHeaders = new HashSet<String>();
        private final Map<CharSequence, Callable<?>> preflightHeaders = new HashMap<CharSequence, Callable<?>>();
        private boolean noPreflightHeaders;
        private boolean shortCurcuit;

        /**
         * Creates a new Builder instance with the origin passed in.
         *
         * @param origins the origin to be used for this builder.
         */
        public Builder(final String... origins) {
            this.origins = new LinkedHashSet<String>(Arrays.asList(origins));
            anyOrigin = false;
        }

        /**
         * Creates a new Builder instance allowing any origin, "*" which is the
         * wildcard origin.
         *
         */
        public Builder() {
            anyOrigin = true;
            origins = Collections.emptySet();
        }

        /**
         * Web browsers may set the 'Origin' request header to 'null' if a resource is loaded
         * from the local file system. Calling this method will enable a successful CORS response
         * with a wildcard for the the CORS response header 'Access-Control-Allow-Origin'.
         *
         * @return {@link Builder} to support method chaining.
         */
        public Builder allowNullOrigin() {
            allowNullOrigin = true;
            return this;
        }

        /**
         * Disables CORS support.
         *
         * @return {@link Builder} to support method chaining.
         */
        public Builder disable() {
            enabled = false;
            return this;
        }

        /**
         * Specifies the headers to be exposed to calling clients.
         *
         * During a simple CORS request, only certain response headers are made available by the
         * browser, for example using:
         * <pre>
         * xhr.getResponseHeader("Content-Type");
         * </pre>
         *
         * The headers that are available by default are:
         * <ul>
         * <li>Cache-Control</li>
         * <li>Content-Language</li>
         * <li>Content-Type</li>
         * <li>Expires</li>
         * <li>Last-Modified</li>
         * <li>Pragma</li>
         * </ul>
         *
         * To expose other headers they need to be specified which is what this method enables by
         * adding the headers to the CORS 'Access-Control-Expose-Headers' response header.
         *
         * @param headers the values to be added to the 'Access-Control-Expose-Headers' response header
         * @return {@link Builder} to support method chaining.
         */
        public Builder exposeHeaders(final String... headers) {
            exposeHeaders.addAll(Arrays.asList(headers));
            return this;
        }

        /**
         * By default cookies are not included in CORS requests, but this method will enable cookies to
         * be added to CORS requests. Calling this method will set the CORS 'Access-Control-Allow-Credentials'
         * response header to true.
         *
         * Please note, that cookie support needs to be enabled on the client side as well.
         * The client needs to opt-in to send cookies by calling:
         * <pre>
         * xhr.withCredentials = true;
         * </pre>
         * The default value for 'withCredentials' is false in which case no cookies are sent.
         * Settning this to true will included cookies in cross origin requests.
         *
         * @return {@link Builder} to support method chaining.
         */
        public Builder allowCredentials() {
            allowCredentials = true;
            return this;
        }

        /**
         * When making a preflight request the client has to perform two request with can be inefficient.
         * This setting will set the CORS 'Access-Control-Max-Age' response header and enables the
         * caching of the preflight response for the specified time. During this time no preflight
         * request will be made.
         *
         * @param max the maximum time, in seconds, that the preflight response may be cached.
         * @return {@link Builder} to support method chaining.
         */
        public Builder maxAge(final long max) {
            maxAge = max;
            return this;
        }

        /**
         * Specifies the allowed set of HTTP Request Methods that should be returned in the
         * CORS 'Access-Control-Request-Method' response header.
         *
         * @param methods the {@link HttpMethod}s that should be allowed.
         * @return {@link Builder} to support method chaining.
         */
        public Builder allowedRequestMethods(final HttpMethod... methods) {
            requestMethods.addAll(Arrays.asList(methods));
            return this;
        }

        /**
         * Specifies the if headers that should be returned in the CORS 'Access-Control-Allow-Headers'
         * response header.
         *
         * If a client specifies headers on the request, for example by calling:
         * <pre>
         * xhr.setRequestHeader('My-Custom-Header', "SomeValue");
         * </pre>
         * the server will recieve the above header name in the 'Access-Control-Request-Headers' of the
         * preflight request. The server will then decide if it allows this header to be sent for the
         * real request (remember that a preflight is not the real request but a request asking the server
         * if it allow a request).
         *
         * @param headers the headers to be added to the preflight 'Access-Control-Allow-Headers' response header.
         * @return {@link Builder} to support method chaining.
         */
        public Builder allowedRequestHeaders(final String... headers) {
            requestHeaders.addAll(Arrays.asList(headers));
            return this;
        }

        /**
         * Returns HTTP response headers that should be added to a CORS preflight response.
         *
         * An intermediary like a load balancer might require that a CORS preflight request
         * have certain headers set. This enables such headers to be added.
         *
         * @param name the name of the HTTP header.
         * @param values the values for the HTTP header.
         * @return {@link Builder} to support method chaining.
         */
        public Builder preflightResponseHeader(final CharSequence name, final Object... values) {
            if (values.length == 1) {
                preflightHeaders.put(name, new ConstantValueGenerator(values[0]));
            } else {
                preflightResponseHeader(name, Arrays.asList(values));
            }
            return this;
        }

        /**
         * Returns HTTP response headers that should be added to a CORS preflight response.
         *
         * An intermediary like a load balancer might require that a CORS preflight request
         * have certain headers set. This enables such headers to be added.
         *
         * @param name the name of the HTTP header.
         * @param value the values for the HTTP header.
         * @param <T> the type of values that the Iterable contains.
         * @return {@link Builder} to support method chaining.
         */
        public <T> Builder preflightResponseHeader(final CharSequence name, final Iterable<T> value) {
            preflightHeaders.put(name, new ConstantValueGenerator(value));
            return this;
        }

        /**
         * Returns HTTP response headers that should be added to a CORS preflight response.
         *
         * An intermediary like a load balancer might require that a CORS preflight request
         * have certain headers set. This enables such headers to be added.
         *
         * Some values must be dynamically created when the HTTP response is created, for
         * example the 'Date' response header. This can be occomplished by using a Callable
         * which will have its 'call' method invoked when the HTTP response is created.
         *
         * @param name the name of the HTTP header.
         * @param valueGenerator a Callable which will be invoked at HTTP response creation.
         * @param <T> the type of the value that the Callable can return.
         * @return {@link Builder} to support method chaining.
         */
        public <T> Builder preflightResponseHeader(final String name, final Callable<T> valueGenerator) {
            preflightHeaders.put(name, valueGenerator);
            return this;
        }

        /**
         * Specifies that no preflight response headers should be added to a preflight response.
         *
         * @return {@link Builder} to support method chaining.
         */
        public Builder noPreflightResponseHeaders() {
            noPreflightHeaders = true;
            return this;
        }

        /**
         * Builds a {@link CorsConfig} with settings specified by previous method calls.
         *
         * @return {@link CorsConfig} the configured CorsConfig instance.
         */
        public CorsConfig build() {
            if (preflightHeaders.isEmpty() && !noPreflightHeaders) {
                preflightHeaders.put(Names.DATE, new DateValueGenerator());
                preflightHeaders.put(Names.CONTENT_LENGTH, new ConstantValueGenerator("0"));
            }
            return new CorsConfig(this);
        }

        /**
         * Specifies that a CORS request should be rejected if it's invalid before being
         * further processing.
         *
         * CORS headers are set after a request is processed. This may not always be desired
         * and this setting will check that the Origin is valid and if it is not valid no
         * further processing will take place, and a error will be returned to the calling client.
         *
         * @return {@link Builder} to support method chaining.
         */
        public Builder shortCurcuit() {
            shortCurcuit = true;
            return this;
        }
    }

    /**
     * This class is used for preflight HTTP response values that do not need to be
     * generated, but instead the value is "static" in that the same value will be returned
     * for each call.
     */
    private static final class ConstantValueGenerator implements Callable<Object> {

        private final Object value;

        /**
         * Sole constructor.
         *
         * @param value the value that will be returned when the call method is invoked.
         */
        private ConstantValueGenerator(final Object value) {
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            this.value = value;
        }

        @Override
        public Object call() {
            return value;
        }
    }

    /**
     * This callable is used for the DATE preflight HTTP response HTTP header.
     * It's value must be generated when the response is generated, hence will be
     * different for every call.
     */
    public static final class DateValueGenerator implements Callable<Date> {

        @Override
        public Date call() throws Exception {
            return new Date();
        }
    }

}
