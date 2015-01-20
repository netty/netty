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

import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.BinaryHeaders;
import io.netty.handler.codec.DefaultBinaryHeaders;

public class DefaultHttp2Headers extends DefaultBinaryHeaders implements Http2Headers {

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
        super(forceKeyToLower);
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
    public Http2Headers add(BinaryHeaders headers) {
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
}
