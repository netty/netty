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
package io.netty.incubator.codec.http3;

import static io.netty.incubator.codec.http3.Http3Headers.PseudoHeaderName.getPseudoHeader;
import static io.netty.incubator.codec.http3.Http3Headers.PseudoHeaderName.hasPseudoHeaderFormat;

/**
 * {@link io.netty.incubator.codec.http3.QpackDecoder.Sink} that does add header names and values to
 * {@link Http3Headers} while also validate these.
 */
final class Http3HeadersSink implements QpackDecoder.Sink {
    private final Http3Headers headers;
    private final long maxHeaderListSize;
    private final boolean validate;
    private long headersLength;
    private boolean exceededMaxLength;
    private Http3Exception validationException;
    private HeaderType previousType;

    Http3HeadersSink(Http3Headers headers, long maxHeaderListSize, boolean validate) {
        this.headers = headers;
        this.maxHeaderListSize = maxHeaderListSize;
        this.validate = validate;
    }

    /**
     * This method must be called after the sink is used.
     */
    void finish() throws Http3Exception {
        if (exceededMaxLength) {
            throw new Http3Exception(
                    String.format("Header size exceeded max allowed size (%d)", maxHeaderListSize));
        } else if (validationException != null) {
            throw validationException;
        }
    }

    @Override
    public void appendToHeaderList(CharSequence name, CharSequence value) {
        headersLength += QpackHeaderField.sizeOf(name, value);
        exceededMaxLength |= headersLength > maxHeaderListSize;

        if (exceededMaxLength || validationException != null) {
            // We don't store the header since we've already failed validation requirements.
            return;
        }

        if (validate) {
            try {
                previousType = validate(name, previousType);
            } catch (Http3Exception ex) {
                validationException = ex;
                return;
            }
        }

        headers.add(name, value);
    }

    private static HeaderType validate(CharSequence name, HeaderType previousHeaderType) throws Http3Exception {
        if (hasPseudoHeaderFormat(name)) {
            if (previousHeaderType == HeaderType.REGULAR_HEADER) {
                throw new Http3Exception(String.format("Pseudo-header field '%s' found after regular header.", name));
            }

            final Http3Headers.PseudoHeaderName pseudoHeader = getPseudoHeader(name);
            if (pseudoHeader == null) {
                throw new Http3Exception(String.format("Invalid HTTP/3 pseudo-header '%s' encountered.", name));
            }

            final HeaderType currentHeaderType = pseudoHeader.isRequestOnly() ?
                    HeaderType.REQUEST_PSEUDO_HEADER : HeaderType.RESPONSE_PSEUDO_HEADER;
            if (previousHeaderType != null && currentHeaderType != previousHeaderType) {
                throw new Http3Exception(String.format("Mix of request and response pseudo-headers."));
            }

            return currentHeaderType;
        }

        return HeaderType.REGULAR_HEADER;
    }

    private enum HeaderType {
        REGULAR_HEADER,
        REQUEST_PSEUDO_HEADER,
        RESPONSE_PSEUDO_HEADER
    }
}
