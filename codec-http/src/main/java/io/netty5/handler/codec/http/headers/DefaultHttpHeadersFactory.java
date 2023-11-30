/*
 * Copyright 2023 The Netty Project
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
package io.netty5.handler.codec.http.headers;

import io.netty5.handler.codec.http.HttpHeaderNames;
import org.jetbrains.annotations.Nullable;

/**
 * A builder of {@link HttpHeadersFactory} instances, that itself implements {@link HttpHeadersFactory}.
 * The builder is immutable, and every {@code with-} method produce a new, modified instance.
 * <p>
 * The default builder you most likely want to start with is {@link DefaultHttpHeadersFactory#headersFactory()}.
 */
public final class DefaultHttpHeadersFactory implements HttpHeadersFactory {
    private static final int SIZE_HINT = 16;
    private static final DefaultHttpHeadersFactory FOR_HEADER =
            new DefaultHttpHeadersFactory(SIZE_HINT, true, true, true, false);
    private static final DefaultHttpHeadersFactory FOR_TRAILER =
            new DefaultHttpHeadersFactory(SIZE_HINT, true, true, true, true);
    private static final int MIN_SIZE_HINT = 2;

    private final int sizeHint;
    private final boolean validateNames;
    private final boolean validateValues;
    private final boolean validateCookies;
    private final boolean validateAsTrailer;

    /**
     * Create a header factory with the given settings.
     *
     * @param sizeHint A hint as to how large the hash data structure should be.
     * The next positive power of two will be used. An upper bound may be enforced.
     * @param validateNames {@code true} to validate header names.
     * @param validateValues {@code true} to validate header values.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     * @param validateAsTrailer {@code true} to do the name validation in a way that's specific to trailers.
     */
    private DefaultHttpHeadersFactory(int sizeHint, boolean validateNames, boolean validateValues,
                                      boolean validateCookies, boolean validateAsTrailer) {
        this.sizeHint = Math.max(MIN_SIZE_HINT, sizeHint); // Size hint should be at least 2, always.
        this.validateNames = validateNames;
        this.validateValues = validateValues;
        this.validateCookies = validateCookies;
        this.validateAsTrailer = validateAsTrailer;
    }

    /**
     * Get the default implementation of {@link HttpHeadersFactory} for creating headers.
     * <p>
     * This {@link DefaultHttpHeadersFactory} creates {@link HttpHeaders} instances that has the
     * recommended header validation enabled.
     */
    public static DefaultHttpHeadersFactory headersFactory() {
        return FOR_HEADER;
    }

    /**
     * Get the implementation of {@link HttpHeadersFactory} for creating trailers for
     * {@link io.netty5.handler.codec.http.LastHttpContent}.
     * <p>
     * This {@link DefaultHttpHeadersFactory} creates {@link HttpHeaders} instances that has the
     * recommended header validation enabled.
     */
    public static DefaultHttpHeadersFactory trailersFactory() {
        return FOR_TRAILER;
    }

    @Override
    public HttpHeaders newHeaders() {
        if (validateAsTrailer) {
            return new TrailingHttpHeaders(sizeHint, validateNames, validateCookies, validateValues);
        }
        return HttpHeaders.newHeaders(sizeHint, validateNames, validateCookies, validateValues);
    }

    @Override
    public HttpHeaders newEmptyHeaders() {
        if (validateAsTrailer) {
            return new TrailingHttpHeaders(MIN_SIZE_HINT, validateNames, validateCookies, validateValues);
        }
        return HttpHeaders.newHeaders(MIN_SIZE_HINT, validateNames, validateCookies, validateValues);
    }
    /**
     * Create a new factory that has HTTP header name validation enabled or disabled.
     * <p>
     * <b>Warning!</b> Setting {@code checkNames} to {@code false} will mean that Netty won't
     * validate & protect against user-supplied headers that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validateNames If validation should be enabled or disabled.
     * @return The new factory.
     */
    public DefaultHttpHeadersFactory withNameValidation(boolean validateNames) {
        if (sizeHint == SIZE_HINT && validateNames && validateValues && validateCookies) {
            return validateAsTrailer ? FOR_TRAILER : FOR_HEADER;
        }
        return new DefaultHttpHeadersFactory(
                sizeHint, validateNames, validateValues, validateCookies, validateAsTrailer);
    }

    /**
     * Create a new factory that has HTTP header value validation enabled or disabled.
     * <p>
     * <b>Warning!</b> Setting {@code checkValues} to {@code false} will mean that Netty won't
     * validate & protect against user-supplied headers that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validateValues If validation should be enabled or disabled.
     * @return The new factory.
     */
    public DefaultHttpHeadersFactory withValueValidation(boolean validateValues) {
        if (sizeHint == SIZE_HINT && validateNames && validateValues && validateCookies) {
            return validateAsTrailer ? FOR_TRAILER : FOR_HEADER;
        }
        return new DefaultHttpHeadersFactory(
                sizeHint, validateNames, validateValues, validateCookies, validateAsTrailer);
    }

    /**
     * Create a new factory that has HTTP header value validation enabled or disabled.
     * <p>
     * <b>Warning!</b> Setting {@code checkCookies} to {@code false} will mean that Netty won't
     * validate & protect against user-supplied headers that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validateCookies If validation should be enabled or disabled.
     * @return The new factory.
     */
    public DefaultHttpHeadersFactory withCookieValidation(boolean validateCookies) {
        if (sizeHint == SIZE_HINT && validateNames && validateValues && validateCookies) {
            return validateAsTrailer ? FOR_TRAILER : FOR_HEADER;
        }
        return new DefaultHttpHeadersFactory(
                sizeHint, validateNames, validateValues, validateCookies, validateAsTrailer);
    }

    /**
     * Create a new factory that has the given size hint.
     *
     * @param sizeHint A hint about the anticipated number of header entries.
     * @return The new factory.
     */
    public DefaultHttpHeadersFactory withSizeHint(int sizeHint) {
        if (sizeHint == SIZE_HINT && validateNames && validateValues && validateCookies) {
            return validateAsTrailer ? FOR_TRAILER : FOR_HEADER;
        }
        return new DefaultHttpHeadersFactory(
                sizeHint, validateNames, validateValues, validateCookies, validateAsTrailer);
    }

    @Override
    public int getSizeHint() {
        return sizeHint;
    }

    @Override
    public boolean isValidatingNames() {
        return validateNames;
    }

    @Override
    public boolean isValidatingValues() {
        return validateValues;
    }

    @Override
    public boolean isValidatingCookies() {
        return validateCookies;
    }

    private static final class TrailingHttpHeaders extends DefaultHttpHeaders {
        TrailingHttpHeaders(
                int arraySizeHint, boolean validateNames, boolean validateCookies, boolean validateValues) {
            super(arraySizeHint, validateNames, validateCookies, validateValues);
        }

        @Override
        protected CharSequence validateKey(@Nullable CharSequence name, boolean forAdd) {
            if (HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(name)
                    || HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(name)
                    || HttpHeaderNames.TRAILER.contentEqualsIgnoreCase(name)) {
                throw new IllegalArgumentException("Prohibited trailing header: " + name);
            }
            return super.validateKey(name, forAdd);
        }
    }
}
