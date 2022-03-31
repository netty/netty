/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.handler.codec.Headers;
import io.netty.util.AsciiString;
import io.netty.util.HashingStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.netty.handler.codec.CharSequenceValueConverter.*;
import static io.netty.handler.codec.http2.DefaultHttp2Headers.*;
import static io.netty.util.AsciiString.*;
import static io.netty.util.internal.EmptyArrays.*;
import static io.netty.util.internal.ObjectUtil.checkNotNullArrayParam;

/**
 * A variant of {@link Http2Headers} which only supports read-only methods.
 * <p>
 * Any array passed to this class may be used directly in the underlying data structures of this class. If these
 * arrays may be modified it is the caller's responsibility to supply this class with a copy of the array.
 * <p>
 * This may be a good alternative to {@link DefaultHttp2Headers} if your have a fixed set of headers which will not
 * change.
 */
public final class ReadOnlyHttp2Headers implements Http2Headers {
    private static final byte PSEUDO_HEADER_TOKEN = (byte) ':';
    private final AsciiString[] pseudoHeaders;
    private final AsciiString[] otherHeaders;

    /**
     * Used to create read only object designed to represent trailers.
     * <p>
     * If this is used for a purpose other than trailers you may violate the header serialization ordering defined by
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.1">RFC 7540, 8.1.2.1</a>.
     * @param validateHeaders {@code true} will run validation on each header name/value pair to ensure protocol
     *                        compliance.
     * @param otherHeaders An array of key:value pairs. Must not contain any
     *                     <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.1">pseudo headers</a>
     *                     or {@code null} names/values.
     *                     A copy will <strong>NOT</strong> be made of this array. If the contents of this array
     *                     may be modified externally you are responsible for passing in a copy.
     * @return A read only representation of the headers.
     */
    public static ReadOnlyHttp2Headers trailers(boolean validateHeaders, AsciiString... otherHeaders) {
        return new ReadOnlyHttp2Headers(validateHeaders, EMPTY_ASCII_STRINGS, otherHeaders);
    }

    /**
     * Create a new read only representation of headers used by clients.
     * @param validateHeaders {@code true} will run validation on each header name/value pair to ensure protocol
     *                        compliance.
     * @param method The value for {@link PseudoHeaderName#METHOD}.
     * @param path The value for {@link PseudoHeaderName#PATH}.
     * @param scheme The value for {@link PseudoHeaderName#SCHEME}.
     * @param authority The value for {@link PseudoHeaderName#AUTHORITY}.
     * @param otherHeaders An array of key:value pairs. Must not contain any
     *                     <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.1">pseudo headers</a>
     *                     or {@code null} names/values.
     *                     A copy will <strong>NOT</strong> be made of this array. If the contents of this array
     *                     may be modified externally you are responsible for passing in a copy.
     * @return a new read only representation of headers used by clients.
     */
    public static ReadOnlyHttp2Headers clientHeaders(boolean validateHeaders,
                                                     AsciiString method, AsciiString path,
                                                     AsciiString scheme, AsciiString authority,
                                                     AsciiString... otherHeaders) {
        return new ReadOnlyHttp2Headers(validateHeaders,
                new AsciiString[] {
                  PseudoHeaderName.METHOD.value(), method, PseudoHeaderName.PATH.value(), path,
                  PseudoHeaderName.SCHEME.value(), scheme, PseudoHeaderName.AUTHORITY.value(), authority
                },
                otherHeaders);
    }

    /**
     * Create a new read only representation of headers used by servers.
     * @param validateHeaders {@code true} will run validation on each header name/value pair to ensure protocol
     *                        compliance.
     * @param status The value for {@link PseudoHeaderName#STATUS}.
     * @param otherHeaders An array of key:value pairs. Must not contain any
     *                     <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.1">pseudo headers</a>
     *                     or {@code null} names/values.
     *                     A copy will <strong>NOT</strong> be made of this array. If the contents of this array
     *                     may be modified externally you are responsible for passing in a copy.
     * @return a new read only representation of headers used by servers.
     */
    public static ReadOnlyHttp2Headers serverHeaders(boolean validateHeaders,
                                                     AsciiString status,
                                                     AsciiString... otherHeaders) {
        return new ReadOnlyHttp2Headers(validateHeaders,
                                        new AsciiString[] { PseudoHeaderName.STATUS.value(), status },
                                        otherHeaders);
    }

    private ReadOnlyHttp2Headers(boolean validateHeaders, AsciiString[] pseudoHeaders, AsciiString... otherHeaders) {
        assert (pseudoHeaders.length & 1) == 0; // pseudoHeaders are only set internally so assert should be enough.
        if ((otherHeaders.length & 1) != 0) {
            throw newInvalidArraySizeException();
        }
        if (validateHeaders) {
            validateHeaders(pseudoHeaders, otherHeaders);
        }
        this.pseudoHeaders = pseudoHeaders;
        this.otherHeaders = otherHeaders;
    }

    private static IllegalArgumentException newInvalidArraySizeException() {
        return new IllegalArgumentException("pseudoHeaders and otherHeaders must be arrays of [name, value] pairs");
    }

    private static void validateHeaders(AsciiString[] pseudoHeaders, AsciiString... otherHeaders) {
        // We are only validating values... so start at 1 and go until end.
        for (int i = 1; i < pseudoHeaders.length; i += 2) {
            // pseudoHeaders names are only set internally so they are assumed to be valid.
            checkNotNullArrayParam(pseudoHeaders[i], i, "pseudoHeaders");
        }

        boolean seenNonPseudoHeader = false;
        final int otherHeadersEnd = otherHeaders.length - 1;
        for (int i = 0; i < otherHeadersEnd; i += 2) {
            AsciiString name = otherHeaders[i];
            HTTP2_NAME_VALIDATOR.validateName(name);
            if (!seenNonPseudoHeader && !name.isEmpty() && name.byteAt(0) != PSEUDO_HEADER_TOKEN) {
                seenNonPseudoHeader = true;
            } else if (seenNonPseudoHeader && !name.isEmpty() && name.byteAt(0) == PSEUDO_HEADER_TOKEN) {
                throw new IllegalArgumentException(
                     "otherHeaders name at index " + i + " is a pseudo header that appears after non-pseudo headers.");
            }
            checkNotNullArrayParam(otherHeaders[i + 1], i + 1, "otherHeaders");
        }
    }

    private AsciiString get0(CharSequence name) {
        final int nameHash = AsciiString.hashCode(name);

        final int pseudoHeadersEnd = pseudoHeaders.length - 1;
        for (int i = 0; i < pseudoHeadersEnd; i += 2) {
            AsciiString roName = pseudoHeaders[i];
            if (roName.hashCode() == nameHash && roName.contentEqualsIgnoreCase(name)) {
                return pseudoHeaders[i + 1];
            }
        }

        final int otherHeadersEnd = otherHeaders.length - 1;
        for (int i = 0; i < otherHeadersEnd; i += 2) {
            AsciiString roName = otherHeaders[i];
            if (roName.hashCode() == nameHash && roName.contentEqualsIgnoreCase(name)) {
                return otherHeaders[i + 1];
            }
        }
        return null;
    }

    @Override
    public CharSequence get(CharSequence name) {
        return get0(name);
    }

    @Override
    public CharSequence get(CharSequence name, CharSequence defaultValue) {
        CharSequence value = get(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public CharSequence getAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public CharSequence getAndRemove(CharSequence name, CharSequence defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public List<CharSequence> getAll(CharSequence name) {
        final int nameHash = AsciiString.hashCode(name);
        List<CharSequence> values = new ArrayList<CharSequence>();

        final int pseudoHeadersEnd = pseudoHeaders.length - 1;
        for (int i = 0; i < pseudoHeadersEnd; i += 2) {
            AsciiString roName = pseudoHeaders[i];
            if (roName.hashCode() == nameHash && roName.contentEqualsIgnoreCase(name)) {
                values.add(pseudoHeaders[i + 1]);
            }
        }

        final int otherHeadersEnd = otherHeaders.length - 1;
        for (int i = 0; i < otherHeadersEnd; i += 2) {
            AsciiString roName = otherHeaders[i];
            if (roName.hashCode() == nameHash && roName.contentEqualsIgnoreCase(name)) {
                values.add(otherHeaders[i + 1]);
            }
        }

        return values;
    }

    @Override
    public List<CharSequence> getAllAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Boolean getBoolean(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToBoolean(value) : null;
    }

    @Override
    public boolean getBoolean(CharSequence name, boolean defaultValue) {
        Boolean value = getBoolean(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Byte getByte(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToByte(value) : null;
    }

    @Override
    public byte getByte(CharSequence name, byte defaultValue) {
        Byte value = getByte(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Character getChar(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToChar(value) : null;
    }

    @Override
    public char getChar(CharSequence name, char defaultValue) {
        Character value = getChar(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Short getShort(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToShort(value) : null;
    }

    @Override
    public short getShort(CharSequence name, short defaultValue) {
        Short value = getShort(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Integer getInt(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToInt(value) : null;
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        Integer value = getInt(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Long getLong(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToLong(value) : null;
    }

    @Override
    public long getLong(CharSequence name, long defaultValue) {
        Long value = getLong(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Float getFloat(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToFloat(value) : null;
    }

    @Override
    public float getFloat(CharSequence name, float defaultValue) {
        Float value = getFloat(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Double getDouble(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToDouble(value) : null;
    }

    @Override
    public double getDouble(CharSequence name, double defaultValue) {
        Double value = getDouble(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Long getTimeMillis(CharSequence name) {
        AsciiString value = get0(name);
        return value != null ? INSTANCE.convertToTimeMillis(value) : null;
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        Long value = getTimeMillis(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public Boolean getBooleanAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public boolean getBooleanAndRemove(CharSequence name, boolean defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Byte getByteAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public byte getByteAndRemove(CharSequence name, byte defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Character getCharAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public char getCharAndRemove(CharSequence name, char defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Short getShortAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public short getShortAndRemove(CharSequence name, short defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Integer getIntAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int getIntAndRemove(CharSequence name, int defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Long getLongAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public long getLongAndRemove(CharSequence name, long defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Float getFloatAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public float getFloatAndRemove(CharSequence name, float defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Double getDoubleAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public double getDoubleAndRemove(CharSequence name, double defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Long getTimeMillisAndRemove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public long getTimeMillisAndRemove(CharSequence name, long defaultValue) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public boolean contains(CharSequence name) {
        return get(name) != null;
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return contains(name, value, false);
    }

    @Override
    public boolean containsObject(CharSequence name, Object value) {
        if (value instanceof CharSequence) {
            return contains(name, (CharSequence) value);
        }
        return contains(name, value.toString());
    }

    @Override
    public boolean containsBoolean(CharSequence name, boolean value) {
        return contains(name, String.valueOf(value));
    }

    @Override
    public boolean containsByte(CharSequence name, byte value) {
        return contains(name, String.valueOf(value));
    }

    @Override
    public boolean containsChar(CharSequence name, char value) {
        return contains(name, String.valueOf(value));
    }

    @Override
    public boolean containsShort(CharSequence name, short value) {
        return contains(name, String.valueOf(value));
    }

    @Override
    public boolean containsInt(CharSequence name, int value) {
        return contains(name, String.valueOf(value));
    }

    @Override
    public boolean containsLong(CharSequence name, long value) {
        return contains(name, String.valueOf(value));
    }

    @Override
    public boolean containsFloat(CharSequence name, float value) {
        return false;
    }

    @Override
    public boolean containsDouble(CharSequence name, double value) {
        return contains(name, String.valueOf(value));
    }

    @Override
    public boolean containsTimeMillis(CharSequence name, long value) {
        return contains(name, String.valueOf(value));
    }

    @Override
    public int size() {
        return pseudoHeaders.length + otherHeaders.length >>> 1;
    }

    @Override
    public boolean isEmpty() {
        return pseudoHeaders.length == 0 && otherHeaders.length == 0;
    }

    @Override
    public Set<CharSequence> names() {
        if (isEmpty()) {
            return Collections.emptySet();
        }
        Set<CharSequence> names = new LinkedHashSet<CharSequence>(size());
        final int pseudoHeadersEnd = pseudoHeaders.length - 1;
        for (int i = 0; i < pseudoHeadersEnd; i += 2) {
            names.add(pseudoHeaders[i]);
        }

        final int otherHeadersEnd = otherHeaders.length - 1;
        for (int i = 0; i < otherHeadersEnd; i += 2) {
            names.add(otherHeaders[i]);
        }
        return names;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers add(CharSequence name, Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addObject(CharSequence name, Object value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addObject(CharSequence name, Iterable<?> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addObject(CharSequence name, Object... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addBoolean(CharSequence name, boolean value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addByte(CharSequence name, byte value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addChar(CharSequence name, char value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addShort(CharSequence name, short value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addInt(CharSequence name, int value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addLong(CharSequence name, long value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addFloat(CharSequence name, float value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addDouble(CharSequence name, double value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers addTimeMillis(CharSequence name, long value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers add(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers set(CharSequence name, Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setObject(CharSequence name, Object value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setObject(CharSequence name, Iterable<?> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setObject(CharSequence name, Object... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setBoolean(CharSequence name, boolean value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setByte(CharSequence name, byte value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setChar(CharSequence name, char value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setShort(CharSequence name, short value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setInt(CharSequence name, int value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setLong(CharSequence name, long value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setFloat(CharSequence name, float value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setDouble(CharSequence name, double value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setTimeMillis(CharSequence name, long value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers set(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers setAll(Headers<? extends CharSequence, ? extends CharSequence, ?> headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public boolean remove(CharSequence name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers clear() {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return new ReadOnlyIterator();
    }

    @Override
    public Iterator<CharSequence> valueIterator(CharSequence name) {
        return new ReadOnlyValueIterator(name);
    }

    @Override
    public Http2Headers method(CharSequence value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers scheme(CharSequence value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers authority(CharSequence value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers path(CharSequence value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Http2Headers status(CharSequence value) {
        throw new UnsupportedOperationException("read only");
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
    public boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
        final int nameHash = AsciiString.hashCode(name);
        final HashingStrategy<CharSequence> strategy =
                caseInsensitive ? CASE_INSENSITIVE_HASHER : CASE_SENSITIVE_HASHER;
        final int valueHash = strategy.hashCode(value);

        return contains(name, nameHash, value, valueHash, strategy, otherHeaders)
                || contains(name, nameHash, value, valueHash, strategy, pseudoHeaders);
    }

    private static boolean contains(CharSequence name, int nameHash, CharSequence value, int valueHash,
                                    HashingStrategy<CharSequence> hashingStrategy, AsciiString[] headers) {
        final int headersEnd = headers.length - 1;
        for (int i = 0; i < headersEnd; i += 2) {
            AsciiString roName = headers[i];
            AsciiString roValue = headers[i + 1];
            if (roName.hashCode() == nameHash && roValue.hashCode() == valueHash &&
                roName.contentEqualsIgnoreCase(name) && hashingStrategy.equals(roValue, value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append('[');
        String separator = "";
        for (Map.Entry<CharSequence, CharSequence> entry : this) {
            builder.append(separator);
            builder.append(entry.getKey()).append(": ").append(entry.getValue());
            separator = ", ";
        }
        return builder.append(']').toString();
    }

    private final class ReadOnlyValueIterator implements Iterator<CharSequence> {
        private int i;
        private final int nameHash;
        private final CharSequence name;
        private AsciiString[] current = pseudoHeaders.length != 0 ? pseudoHeaders : otherHeaders;
        private AsciiString next;

        ReadOnlyValueIterator(CharSequence name) {
            nameHash = AsciiString.hashCode(name);
            this.name = name;
            calculateNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public CharSequence next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            CharSequence current = next;
            calculateNext();
            return current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read only");
        }

        private void calculateNext() {
            for (; i < current.length; i += 2) {
                AsciiString roName = current[i];
                if (roName.hashCode() == nameHash && roName.contentEqualsIgnoreCase(name)) {
                    if (i + 1 < current.length) {
                        next = current[i + 1];
                        i += 2;
                    }
                    return;
                }
            }
            if (current == pseudoHeaders) {
                i = 0;
                current = otherHeaders;
                calculateNext();
            } else {
                next = null;
            }
        }
    }

    private final class ReadOnlyIterator implements Map.Entry<CharSequence, CharSequence>,
                                                    Iterator<Map.Entry<CharSequence, CharSequence>> {
        private int i;
        private AsciiString[] current = pseudoHeaders.length != 0 ? pseudoHeaders : otherHeaders;
        private AsciiString key;
        private AsciiString value;

        @Override
        public boolean hasNext() {
            return i != current.length;
        }

        @Override
        public Map.Entry<CharSequence, CharSequence> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            key = current[i];
            value = current[i + 1];
            i += 2;
            if (i == current.length && current == pseudoHeaders) {
                current = otherHeaders;
                i = 0;
            }
            return this;
        }

        @Override
        public CharSequence getKey() {
            return key;
        }

        @Override
        public CharSequence getValue() {
            return value;
        }

        @Override
        public CharSequence setValue(CharSequence value) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public String toString() {
            return key.toString() + '=' + value.toString();
        }
    }
}
