/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import io.netty.handler.codec.AsciiStringValueConverter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.Headers;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.ByteString;
import io.netty.util.internal.PlatformDependent;

public class DefaultHttp2Headers extends DefaultHeaders<AsciiString> implements Http2Headers {
    private static final AsciiStringValueConverter ASCII_STRING_VALUE_CONVERTER = AsciiStringValueConverter.INSTANCE;

    private static final ByteProcessor HTTP2_NAME_VALIDATOR_PROCESSOR = new ByteProcessor() {
        @Override
        public boolean process(byte value) throws Exception {
            return value < 'A' || value > 'Z';
        }
    };
    private static final NameValidator<ByteString> HTTP2_NAME_VALIDATOR = new NameValidator<ByteString>() {
        @Override
        public void validateName(ByteString name) {
            final int index;
            try {
                index = name.forEachByte(HTTP2_NAME_VALIDATOR_PROCESSOR);
            } catch (Http2Exception e) {
                PlatformDependent.throwException(e);
                return;
            } catch (Throwable t) {
                PlatformDependent.throwException(connectionError(PROTOCOL_ERROR, t,
                        "unexpected error. invalid header name [%s]", name));
                return;
            }

            if (index != -1) {
                PlatformDependent.throwException(connectionError(PROTOCOL_ERROR, "invalid header name [%s]", name));
            }
        }
    };
    private HeaderEntry<AsciiString> firstNonPseudo = head;

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
        super(ASCII_STRING_VALUE_CONVERTER, validate ? HTTP2_NAME_VALIDATOR : NameValidator.NOT_NULL);
    }

    @Override
    public Http2Headers add(AsciiString name, AsciiString value) {
        super.add(name, value);
        return this;
    }

    @Override
    public Http2Headers add(AsciiString name, Iterable<? extends AsciiString> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers add(AsciiString name, AsciiString... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers addObject(AsciiString name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public Http2Headers addObject(AsciiString name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public Http2Headers addObject(AsciiString name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public Http2Headers addBoolean(AsciiString name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public Http2Headers addChar(AsciiString name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public Http2Headers addByte(AsciiString name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public Http2Headers addShort(AsciiString name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public Http2Headers addInt(AsciiString name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public Http2Headers addLong(AsciiString name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public Http2Headers addFloat(AsciiString name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public Http2Headers addDouble(AsciiString name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public Http2Headers addTimeMillis(AsciiString name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public Http2Headers add(Headers<? extends AsciiString> headers) {
        super.add(headers);
        return this;
    }

    @Override
    public Http2Headers set(AsciiString name, AsciiString value) {
        super.set(name, value);
        return this;
    }

    @Override
    public Http2Headers set(AsciiString name, Iterable<? extends AsciiString> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers set(AsciiString name, AsciiString... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers setObject(AsciiString name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public Http2Headers setObject(AsciiString name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public Http2Headers setObject(AsciiString name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public Http2Headers setBoolean(AsciiString name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public Http2Headers setChar(AsciiString name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public Http2Headers setByte(AsciiString name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public Http2Headers setShort(AsciiString name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public Http2Headers setInt(AsciiString name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public Http2Headers setLong(AsciiString name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public Http2Headers setFloat(AsciiString name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public Http2Headers setDouble(AsciiString name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public Http2Headers setTimeMillis(AsciiString name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public Http2Headers set(Headers<? extends AsciiString> headers) {
        super.set(headers);
        return this;
    }

    @Override
    public Http2Headers setAll(Headers<? extends AsciiString> headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public Http2Headers clear() {
        super.clear();
        return this;
    }

    @Override
    public Http2Headers method(AsciiString value) {
        set(PseudoHeaderName.METHOD.value(), value);
        return this;
    }

    @Override
    public Http2Headers scheme(AsciiString value) {
        set(PseudoHeaderName.SCHEME.value(), value);
        return this;
    }

    @Override
    public Http2Headers authority(AsciiString value) {
        set(PseudoHeaderName.AUTHORITY.value(), value);
        return this;
    }

    @Override
    public Http2Headers path(AsciiString value) {
        set(PseudoHeaderName.PATH.value(), value);
        return this;
    }

    @Override
    public Http2Headers status(AsciiString value) {
        set(PseudoHeaderName.STATUS.value(), value);
        return this;
    }

    @Override
    public AsciiString method() {
        return get(PseudoHeaderName.METHOD.value());
    }

    @Override
    public AsciiString scheme() {
        return get(PseudoHeaderName.SCHEME.value());
    }

    @Override
    public AsciiString authority() {
        return get(PseudoHeaderName.AUTHORITY.value());
    }

    @Override
    public AsciiString path() {
        return get(PseudoHeaderName.PATH.value());
    }

    @Override
    public AsciiString status() {
        return get(PseudoHeaderName.STATUS.value());
    }

    @Override
    protected final HeaderEntry<AsciiString> newHeaderEntry(int h, AsciiString name, AsciiString value,
                                                           HeaderEntry<AsciiString> next) {
        return new Http2HeaderEntry(h, name, value, next);
    }

    private final class Http2HeaderEntry extends HeaderEntry<AsciiString> {
        protected Http2HeaderEntry(int hash, AsciiString key, AsciiString value, HeaderEntry<AsciiString> next) {
            super(hash, key);
            this.value = value;
            this.next = next;

            // Make sure the pseudo headers fields are first in iteration order
            if (!key.isEmpty() && key.byteAt(0) == ':') {
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
