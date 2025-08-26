/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;

import java.util.function.BiConsumer;

import static io.netty.handler.codec.http3.Http3Headers.PseudoHeaderName.AUTHORITY;
import static io.netty.handler.codec.http3.Http3Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http3.Http3Headers.PseudoHeaderName.PATH;
import static io.netty.handler.codec.http3.Http3Headers.PseudoHeaderName.SCHEME;
import static io.netty.handler.codec.http3.Http3Headers.PseudoHeaderName.STATUS;
import static io.netty.handler.codec.http3.Http3Headers.PseudoHeaderName.getPseudoHeader;
import static io.netty.handler.codec.http3.Http3Headers.PseudoHeaderName.hasPseudoHeaderFormat;

/**
 * {@link BiConsumer} that does add header names and values to
 * {@link Http3Headers} while also validate these.
 */
final class Http3HeadersSink implements BiConsumer<CharSequence, CharSequence> {
    private final Http3Headers headers;
    private final long maxHeaderListSize;
    private final boolean validate;
    private final boolean trailer;
    private long headersLength;
    private boolean exceededMaxLength;
    private Http3HeadersValidationException validationException;
    private HeaderType previousType;
    private boolean request;
    private int receivedPseudoHeaders;

    Http3HeadersSink(Http3Headers headers, long maxHeaderListSize, boolean validate, boolean trailer) {
        this.headers = headers;
        this.maxHeaderListSize = maxHeaderListSize;
        this.validate = validate;
        this.trailer = trailer;
    }

    /**
     * This method must be called after the sink is used.
     */
    void finish() throws Http3HeadersValidationException, Http3Exception {
        if (exceededMaxLength) {
            throw new Http3Exception(Http3ErrorCode.H3_EXCESSIVE_LOAD,
                    String.format("Header size exceeded max allowed size (%d)", maxHeaderListSize));
        }
        if (validationException != null) {
            throw validationException;
        }
        if (validate) {
            if (trailer) {
                if (receivedPseudoHeaders != 0) {
                    // Trailers must not have pseudo headers.
                    throw new Http3HeadersValidationException("Pseudo-header(s) included in trailers.");
                }
                return;
            }

            // Validate that all mandatory pseudo-headers are included.
            if (request) {
                CharSequence method = headers.method();
                // fast-path
                if (HttpMethod.CONNECT.asciiName().contentEqualsIgnoreCase(method)) {
                    // For CONNECT we must only include:
                    // - :method
                    // - :authority
                    final int requiredPseudoHeaders = METHOD.getFlag() | AUTHORITY.getFlag();
                    if (receivedPseudoHeaders != requiredPseudoHeaders) {
                        throw new Http3HeadersValidationException("Not all mandatory pseudo-headers included.");
                    }
                } else if (HttpMethod.OPTIONS.asciiName().contentEqualsIgnoreCase(method)) {
                    // See:
                    //
                    // https://www.rfc-editor.org/rfc/rfc9114.html#section-4.3.1
                    // https://www.rfc-editor.org/rfc/rfc9110#section-7.1
                    // - :method
                    // - :scheme
                    // - :authority
                    // - :path
                    final int requiredPseudoHeaders = METHOD.getFlag() | SCHEME.getFlag() | PATH.getFlag();
                    if ((receivedPseudoHeaders & requiredPseudoHeaders) != requiredPseudoHeaders ||
                            (!authorityOrHostHeaderReceived() && !"*".contentEquals(headers.path()))) {
                        throw new Http3HeadersValidationException("Not all mandatory pseudo-headers included.");
                    }
                } else {
                    // For other requests we must include:
                    // - :method
                    // - :scheme
                    // - :authority
                    // - :path
                    final int requiredPseudoHeaders = METHOD.getFlag() | SCHEME.getFlag() | PATH.getFlag();
                    if ((receivedPseudoHeaders & requiredPseudoHeaders) != requiredPseudoHeaders ||
                        !authorityOrHostHeaderReceived()) {
                        throw new Http3HeadersValidationException("Not all mandatory pseudo-headers included.");
                    }
                }
            } else {
                // For responses we must include:
                // - :status
                if (receivedPseudoHeaders != STATUS.getFlag()) {
                    throw new Http3HeadersValidationException("Not all mandatory pseudo-headers included.");
                }
            }
        }
    }

    /**
     * Find host header field in case the :authority pseudo header is not specified.
     * See:
     * https://www.rfc-editor.org/rfc/rfc9110#section-7.2
     */
    private boolean authorityOrHostHeaderReceived() {
        return (receivedPseudoHeaders & AUTHORITY.getFlag()) == AUTHORITY.getFlag() ||
                headers.contains(HttpHeaderNames.HOST);
    }

    @Override
    public void accept(CharSequence name, CharSequence value) {
        headersLength += QpackHeaderField.sizeOf(name, value);
        exceededMaxLength |= headersLength > maxHeaderListSize;

        if (exceededMaxLength || validationException != null) {
            // We don't store the header since we've already failed validation requirements.
            return;
        }

        if (validate) {
            try {
                 validate(headers, name);
            } catch (Http3HeadersValidationException ex) {
                validationException = ex;
                return;
            }
        }

        headers.add(name, value);
    }

    private void validate(Http3Headers headers, CharSequence name) {
        if (hasPseudoHeaderFormat(name)) {
            if (previousType == HeaderType.REGULAR_HEADER) {
                throw new Http3HeadersValidationException(
                        String.format("Pseudo-header field '%s' found after regular header.", name));
            }

            final Http3Headers.PseudoHeaderName pseudoHeader = getPseudoHeader(name);
            if (pseudoHeader == null) {
                throw new Http3HeadersValidationException(
                        String.format("Invalid HTTP/3 pseudo-header '%s' encountered.", name));
            }
            if ((receivedPseudoHeaders & pseudoHeader.getFlag()) != 0) {
                // There can't be any duplicates for pseudy header names.
                throw new Http3HeadersValidationException(
                        String.format("Pseudo-header field '%s' exists already.", name));
            }
            receivedPseudoHeaders |= pseudoHeader.getFlag();

            final HeaderType currentHeaderType = pseudoHeader.isRequestOnly() ?
                    HeaderType.REQUEST_PSEUDO_HEADER : HeaderType.RESPONSE_PSEUDO_HEADER;
            request = pseudoHeader.isRequestOnly();
            previousType = currentHeaderType;
        } else {
            previousType = HeaderType.REGULAR_HEADER;
        }
    }

    private enum HeaderType {
        REGULAR_HEADER,
        REQUEST_PSEUDO_HEADER,
        RESPONSE_PSEUDO_HEADER
    }
}
