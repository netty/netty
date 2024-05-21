/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.PlatformDependent;

import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.hasPseudoHeaderFormat;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.isPseudoHeader;
import static io.netty.util.AsciiString.CASE_INSENSITIVE_HASHER;
import static io.netty.util.AsciiString.CASE_SENSITIVE_HASHER;
import static io.netty.util.AsciiString.isUpperCase;

public class DefaultHttp2Headers
        extends DefaultHeaders<CharSequence, CharSequence, Http2Headers> implements Http2Headers {
    private static final ByteProcessor HTTP2_NAME_VALIDATOR_PROCESSOR = new ByteProcessor() {
        @Override
        public boolean process(byte value) {
            return !isUpperCase(value);
        }
    };
    static final NameValidator<CharSequence> HTTP2_NAME_VALIDATOR = new NameValidator<CharSequence>() {
        @Override
        public void validateName(CharSequence name) {
            if (name == null || name.length() == 0) {
                PlatformDependent.throwException(connectionError(PROTOCOL_ERROR,
                        "empty headers are not allowed [%s]", name));
            }

            if (hasPseudoHeaderFormat(name)) {
                if (!isPseudoHeader(name)) {
                    PlatformDependent.throwException(connectionError(
                            PROTOCOL_ERROR, "Invalid HTTP/2 pseudo-header '%s' encountered.", name));
                }
                // no need for lower-case validation, we trust our own pseudo header constants
                return;
            }

            if (name instanceof AsciiString) {
                final int index;
                try {
                    index = ((AsciiString) name).forEachByte(HTTP2_NAME_VALIDATOR_PROCESSOR);
                } catch (Http2Exception e) {
                    PlatformDependent.throwException(e);
                    return;
                } catch (Throwable t) {
                    PlatformDependent.throwException(connectionError(PROTOCOL_ERROR, t,
                            "unexpected error. invalid header name [%s]", name));
                    return;
                }

                if (index != -1) {
                    PlatformDependent.throwException(connectionError(PROTOCOL_ERROR,
                            "invalid header name [%s]", name));
                }
            } else {
                for (int i = 0; i < name.length(); ++i) {
                    if (isUpperCase(name.charAt(i))) {
                        PlatformDependent.throwException(connectionError(PROTOCOL_ERROR,
                                "invalid header name [%s]", name));
                    }
                }
            }
        }
    };

    private static final ValueValidator<CharSequence> VALUE_VALIDATOR = new ValueValidator<CharSequence>() {
        @Override
        public void validate(CharSequence value) {
            int index = HttpHeaderValidationUtil.validateValidHeaderValue(value);
            if (index != -1) {
                throw new IllegalArgumentException("a header value contains prohibited character 0x" +
                        Integer.toHexString(value.charAt(index)) + " at index " + index + '.');
            }
        }
    };

    private HeaderEntry<CharSequence, CharSequence> firstNonPseudo = head;

    /**
     * Create a new instance.
     * <p>
     * Header names will be validated according to
     * <a href="https://tools.ietf.org/html/rfc7540">rfc7540</a>.
     */
    public DefaultHttp2Headers() {
        this(true);
    }

    /**
     * Create a new instance.
     * @param validate {@code true} to validate header names according to
     * <a href="https://tools.ietf.org/html/rfc7540">rfc7540</a>. {@code false} to not validate header names.
     */
    @SuppressWarnings("unchecked")
    public DefaultHttp2Headers(boolean validate) {
        // Case sensitive compare is used because it is cheaper, and header validation can be used to catch invalid
        // headers.
        super(CASE_SENSITIVE_HASHER,
              CharSequenceValueConverter.INSTANCE,
              validate ? HTTP2_NAME_VALIDATOR : NameValidator.NOT_NULL);
    }

    /**
     * Create a new instance.
     * @param validate {@code true} to validate header names according to
     * <a href="https://tools.ietf.org/html/rfc7540">rfc7540</a>. {@code false} to not validate header names.
     * @param arraySizeHint A hint as to how large the hash data structure should be.
     * The next positive power of two will be used. An upper bound may be enforced.
     * @see DefaultHttp2Headers#DefaultHttp2Headers(boolean, boolean, int)
     */
    @SuppressWarnings("unchecked")
    public DefaultHttp2Headers(boolean validate, int arraySizeHint) {
        // Case sensitive compare is used because it is cheaper, and header validation can be used to catch invalid
        // headers.
        super(CASE_SENSITIVE_HASHER,
              CharSequenceValueConverter.INSTANCE,
              validate ? HTTP2_NAME_VALIDATOR : NameValidator.NOT_NULL,
              arraySizeHint);
    }

    /**
     * Create a new instance.
     * @param validate {@code true} to validate header names according to
     * <a href="https://tools.ietf.org/html/rfc7540">rfc7540</a>. {@code false} to not validate header names.
     * @param validateValues {@code true} to validate header values according to
     * <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-3.2">rfc7230</a> and
     * <a href="https://datatracker.ietf.org/doc/html/rfc5234#appendix-B.1">rfc5234</a>. Otherwise, {@code false}
     * (the default) to not validate values.
     * @param arraySizeHint A hint as to how large the hash data structure should be.
     * The next positive power of two will be used. An upper bound may be enforced.
     */
    @SuppressWarnings("unchecked")
    public DefaultHttp2Headers(boolean validate, boolean validateValues, int arraySizeHint) {
        // Case sensitive compare is used because it is cheaper, and header validation can be used to catch invalid
        // headers.
        super(CASE_SENSITIVE_HASHER,
                CharSequenceValueConverter.INSTANCE,
                validate ? HTTP2_NAME_VALIDATOR : NameValidator.NOT_NULL,
                arraySizeHint,
                validateValues ? VALUE_VALIDATOR : (ValueValidator<CharSequence>) ValueValidator.NO_VALIDATION);
    }

    @Override
    protected void validateName(NameValidator<CharSequence> validator, boolean forAdd, CharSequence name) {
        super.validateName(validator, forAdd, name);
        if (nameValidator() == HTTP2_NAME_VALIDATOR && forAdd && hasPseudoHeaderFormat(name)) {
            if (contains(name)) {
                PlatformDependent.throwException(connectionError(
                        PROTOCOL_ERROR, "Duplicate HTTP/2 pseudo-header '%s' encountered.", name));
            }
        }
    }

    @Override
    protected void validateValue(ValueValidator<CharSequence> validator, CharSequence name, CharSequence value) {
        // This method has a noop override for backward compatibility, see https://github.com/netty/netty/pull/12975
        super.validateValue(validator, name, value);
        // https://datatracker.ietf.org/doc/html/rfc9113#section-8.3.1
        // pseudo headers must not be empty
        if (nameValidator() == HTTP2_NAME_VALIDATOR && (value == null || value.length() == 0) &&
                hasPseudoHeaderFormat(name)) {
            PlatformDependent.throwException(connectionError(
                    PROTOCOL_ERROR, "HTTP/2 pseudo-header '%s' must not be empty.", name));
        }
    }

    @Override
    public Http2Headers clear() {
        firstNonPseudo = head;
        return super.clear();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Http2Headers && equals((Http2Headers) o, CASE_SENSITIVE_HASHER);
    }

    @Override
    public int hashCode() {
        return hashCode(CASE_SENSITIVE_HASHER);
    }

    @Override
    public Http2Headers method(CharSequence value) {
        set(PseudoHeaderName.METHOD.value(), value);
        return this;
    }

    @Override
    public Http2Headers scheme(CharSequence value) {
        set(PseudoHeaderName.SCHEME.value(), value);
        return this;
    }

    @Override
    public Http2Headers authority(CharSequence value) {
        set(PseudoHeaderName.AUTHORITY.value(), value);
        return this;
    }

    @Override
    public Http2Headers path(CharSequence value) {
        set(PseudoHeaderName.PATH.value(), value);
        return this;
    }

    @Override
    public Http2Headers status(CharSequence value) {
        set(PseudoHeaderName.STATUS.value(), value);
        return this;
    }

    @Override
    public CharSequence method() {
        return get(PseudoHeaderName.METHOD.value());
    }

    @Override
    public CharSequence scheme() {
        return get(PseudoHeaderName.SCHEME.value());
    }

    @Override
    public CharSequence authority() {
        return get(PseudoHeaderName.AUTHORITY.value());
    }

    @Override
    public CharSequence path() {
        return get(PseudoHeaderName.PATH.value());
    }

    @Override
    public CharSequence status() {
        return get(PseudoHeaderName.STATUS.value());
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return contains(name, value, false);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
        return contains(name, value, caseInsensitive ? CASE_INSENSITIVE_HASHER : CASE_SENSITIVE_HASHER);
    }

    @Override
    protected final HeaderEntry<CharSequence, CharSequence> newHeaderEntry(int h, CharSequence name, CharSequence value,
                                                           HeaderEntry<CharSequence, CharSequence> next) {
        return new Http2HeaderEntry(h, name, value, next);
    }

    private final class Http2HeaderEntry extends HeaderEntry<CharSequence, CharSequence> {
        Http2HeaderEntry(int hash, CharSequence key, CharSequence value,
                HeaderEntry<CharSequence, CharSequence> next) {
            super(hash, key);
            this.value = value;
            this.next = next;

            // Make sure the pseudo headers fields are first in iteration order
            if (hasPseudoHeaderFormat(key)) {
                after = firstNonPseudo;
                before = firstNonPseudo.before();
            } else {
                after = head;
                before = head.before();
                if (firstNonPseudo == head) {
                    firstNonPseudo = this;
                }
            }
            pointNeighborsToThis();
        }

        @Override
        protected void remove() {
            if (this == firstNonPseudo) {
                firstNonPseudo = firstNonPseudo.after();
            }
            super.remove();
        }
    }
}
