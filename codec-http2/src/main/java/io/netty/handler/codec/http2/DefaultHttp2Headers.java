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

import static io.netty.util.internal.StringUtil.UPPER_CASE_TO_LOWER_CASE_ASCII_OFFSET;
import io.netty.handler.codec.BinaryHeaders;
import io.netty.handler.codec.DefaultBinaryHeaders;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.ByteString;
import io.netty.util.internal.PlatformDependent;

public class DefaultHttp2Headers extends DefaultBinaryHeaders implements Http2Headers {
    private static final ByteProcessor HTTP2_ASCII_UPPERCASE_PROCESSOR = new ByteProcessor() {
        @Override
        public boolean process(byte value) throws Exception {
            return value < 'A' || value > 'Z';
        }
    };

    private static final class Http2AsciiToLowerCaseConverter implements ByteProcessor {
        private final byte[] result;
        private int i;

        public Http2AsciiToLowerCaseConverter(int length) {
            result = new byte[length];
        }

        @Override
        public boolean process(byte value) throws Exception {
            result[i++] = (value >= 'A' && value <= 'Z')
                    ? (byte) (value + UPPER_CASE_TO_LOWER_CASE_ASCII_OFFSET) : value;
            return true;
        }

        public byte[] result() {
            return result;
        }
    };

    private static final NameConverter<ByteString> HTTP2_ASCII_TO_LOWER_CONVERTER = new NameConverter<ByteString>() {
        @Override
        public ByteString convertName(ByteString name) {
            if (name instanceof AsciiString) {
                return ((AsciiString) name).toLowerCase();
            }

            try {
                if (name.forEachByte(HTTP2_ASCII_UPPERCASE_PROCESSOR) == -1) {
                    return name;
                }

                Http2AsciiToLowerCaseConverter converter = new Http2AsciiToLowerCaseConverter(name.length());
                name.forEachByte(converter);
                return new ByteString(converter.result(), false);
            } catch (Exception e) {
                PlatformDependent.throwException(e);
                return null;
            }
        }
    };

    /**
     * Creates an instance that will convert all header names to lowercase.
     */
    public DefaultHttp2Headers() {
        this(true);
    }

    /**
     * Creates an instance that can be configured to either do header field name conversion to
     * lowercase, or not do any conversion at all.
     * <p>
     *
     * <strong>Note</strong> that setting {@code forceKeyToLower} to {@code false} can violate the
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.2">HTTP/2 specification</a>
     * which specifies that a request or response containing an uppercase header field MUST be treated
     * as malformed. Only set {@code forceKeyToLower} to {@code false} if you are explicitly using lowercase
     * header field names and want to avoid the conversion to lowercase.
     *
     * @param forceKeyToLower if @{code false} no header name conversion will be performed
     */
    public DefaultHttp2Headers(boolean forceKeyToLower) {
        super(forceKeyToLower ? HTTP2_ASCII_TO_LOWER_CONVERTER : IDENTITY_NAME_CONVERTER);
    }

    @Override
    public Http2Headers add(ByteString name, ByteString value) {
        super.add(name, value);
        return this;
    }

    @Override
    public Http2Headers add(ByteString name, Iterable<? extends ByteString> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers add(ByteString name, ByteString... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers addObject(ByteString name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public Http2Headers addObject(ByteString name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public Http2Headers addObject(ByteString name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public Http2Headers addBoolean(ByteString name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public Http2Headers addChar(ByteString name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public Http2Headers addByte(ByteString name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public Http2Headers addShort(ByteString name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public Http2Headers addInt(ByteString name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public Http2Headers addLong(ByteString name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public Http2Headers addFloat(ByteString name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public Http2Headers addDouble(ByteString name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public Http2Headers addTimeMillis(ByteString name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public Http2Headers add(BinaryHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public Http2Headers set(ByteString name, ByteString value) {
        super.set(name, value);
        return this;
    }

    @Override
    public Http2Headers set(ByteString name, Iterable<? extends ByteString> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers set(ByteString name, ByteString... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers setObject(ByteString name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public Http2Headers setObject(ByteString name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public Http2Headers setObject(ByteString name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public Http2Headers setBoolean(ByteString name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public Http2Headers setChar(ByteString name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public Http2Headers setByte(ByteString name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public Http2Headers setShort(ByteString name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public Http2Headers setInt(ByteString name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public Http2Headers setLong(ByteString name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public Http2Headers setFloat(ByteString name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public Http2Headers setDouble(ByteString name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public Http2Headers setTimeMillis(ByteString name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public Http2Headers set(BinaryHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public Http2Headers setAll(BinaryHeaders headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public Http2Headers clear() {
        super.clear();
        return this;
    }

    @Override
    public Http2Headers method(ByteString value) {
        set(PseudoHeaderName.METHOD.value(), value);
        return this;
    }

    @Override
    public Http2Headers scheme(ByteString value) {
        set(PseudoHeaderName.SCHEME.value(), value);
        return this;
    }

    @Override
    public Http2Headers authority(ByteString value) {
        set(PseudoHeaderName.AUTHORITY.value(), value);
        return this;
    }

    @Override
    public Http2Headers path(ByteString value) {
        set(PseudoHeaderName.PATH.value(), value);
        return this;
    }

    @Override
    public Http2Headers status(ByteString value) {
        set(PseudoHeaderName.STATUS.value(), value);
        return this;
    }

    @Override
    public ByteString method() {
        return get(PseudoHeaderName.METHOD.value());
    }

    @Override
    public ByteString scheme() {
        return get(PseudoHeaderName.SCHEME.value());
    }

    @Override
    public ByteString authority() {
        return get(PseudoHeaderName.AUTHORITY.value());
    }

    @Override
    public ByteString path() {
        return get(PseudoHeaderName.PATH.value());
    }

    @Override
    public ByteString status() {
        return get(PseudoHeaderName.STATUS.value());
    }
}
