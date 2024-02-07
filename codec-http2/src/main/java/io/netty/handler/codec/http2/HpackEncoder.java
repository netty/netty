/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.HpackUtil.IndexType;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import java.util.Map;

import static io.netty.handler.codec.http2.HpackUtil.equalsConstantTime;
import static io.netty.handler.codec.http2.HpackUtil.equalsVariableTime;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.headerListSizeExceeded;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.MathUtil.findNextPositivePowerOfTwo;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * An HPACK encoder.
 *
 * <p>Implementation note:  This class is security sensitive, and depends on users correctly identifying their headers
 * as security sensitive or not.  If a header is considered not sensitive, methods names "insensitive" are used which
 * are fast, but don't provide any security guarantees.
 */
final class HpackEncoder {
    static final int NOT_FOUND = -1;
    static final int HUFF_CODE_THRESHOLD = 512;
    // a hash map of header fields keyed by header name
    private final NameEntry[] nameEntries;

    // a hash map of header fields keyed by header name and value
    private final NameValueEntry[] nameValueEntries;

    private final NameValueEntry head = new NameValueEntry(-1, AsciiString.EMPTY_STRING,
      AsciiString.EMPTY_STRING, Integer.MAX_VALUE, null);

    private NameValueEntry latest = head;

    private final HpackHuffmanEncoder hpackHuffmanEncoder = new HpackHuffmanEncoder();
    private final byte hashMask;
    private final boolean ignoreMaxHeaderListSize;
    private final int huffCodeThreshold;
    private long size;
    private long maxHeaderTableSize;
    private long maxHeaderListSize;

    /**
     * Creates a new encoder.
     */
    HpackEncoder() {
        this(false);
    }

    /**
     * Creates a new encoder.
     */
    HpackEncoder(boolean ignoreMaxHeaderListSize) {
        this(ignoreMaxHeaderListSize, 64, HUFF_CODE_THRESHOLD);
    }

    /**
     * Creates a new encoder.
     */
    HpackEncoder(boolean ignoreMaxHeaderListSize, int arraySizeHint, int huffCodeThreshold) {
        this.ignoreMaxHeaderListSize = ignoreMaxHeaderListSize;
        maxHeaderTableSize = DEFAULT_HEADER_TABLE_SIZE;
        maxHeaderListSize = MAX_HEADER_LIST_SIZE;
        // Enforce a bound of [2, 128] because hashMask is a byte. The max possible value of hashMask is one less
        // than the length of this array, and we want the mask to be > 0.
        nameEntries = new NameEntry[findNextPositivePowerOfTwo(max(2, min(arraySizeHint, 128)))];
        nameValueEntries = new NameValueEntry[nameEntries.length];
        hashMask = (byte) (nameEntries.length - 1);
        this.huffCodeThreshold = huffCodeThreshold;
    }

    /**
     * Encode the header field into the header block.
     * <p>
     * <strong>The given {@link CharSequence}s must be immutable!</strong>
     */
    public void encodeHeaders(int streamId, ByteBuf out, Http2Headers headers, SensitivityDetector sensitivityDetector)
      throws Http2Exception {
        if (ignoreMaxHeaderListSize) {
            encodeHeadersIgnoreMaxHeaderListSize(out, headers, sensitivityDetector);
        } else {
            encodeHeadersEnforceMaxHeaderListSize(streamId, out, headers, sensitivityDetector);
        }
    }

    private void encodeHeadersEnforceMaxHeaderListSize(int streamId, ByteBuf out, Http2Headers headers,
                                                       SensitivityDetector sensitivityDetector)
      throws Http2Exception {
        long headerSize = 0;
        // To ensure we stay consistent with our peer check the size is valid before we potentially modify HPACK state.
        for (Map.Entry<CharSequence, CharSequence> header : headers) {
            CharSequence name = header.getKey();
            CharSequence value = header.getValue();
            // OK to increment now and check for bounds after because this value is limited to unsigned int and will not
            // overflow.
            headerSize += HpackHeaderField.sizeOf(name, value);
            if (headerSize > maxHeaderListSize) {
                headerListSizeExceeded(streamId, maxHeaderListSize, false);
            }
        }
        encodeHeadersIgnoreMaxHeaderListSize(out, headers, sensitivityDetector);
    }

    private void encodeHeadersIgnoreMaxHeaderListSize(ByteBuf out, Http2Headers headers,
                                                      SensitivityDetector sensitivityDetector) {
        for (Map.Entry<CharSequence, CharSequence> header : headers) {
            CharSequence name = header.getKey();
            CharSequence value = header.getValue();
            encodeHeader(out, name, value, sensitivityDetector.isSensitive(name, value),
              HpackHeaderField.sizeOf(name, value));
        }
    }

    /**
     * Encode the header field into the header block.
     * <p>
     * <strong>The given {@link CharSequence}s must be immutable!</strong>
     */
    private void encodeHeader(ByteBuf out, CharSequence name, CharSequence value, boolean sensitive, long headerSize) {
        // If the header value is sensitive then it must never be indexed
        if (sensitive) {
            int nameIndex = getNameIndex(name);
            encodeLiteral(out, name, value, IndexType.NEVER, nameIndex);
            return;
        }

        // If the peer will only use the static table
        if (maxHeaderTableSize == 0) {
            int staticTableIndex = HpackStaticTable.getIndexInsensitive(name, value);
            if (staticTableIndex == HpackStaticTable.NOT_FOUND) {
                int nameIndex = HpackStaticTable.getIndex(name);
                encodeLiteral(out, name, value, IndexType.NONE, nameIndex);
            } else {
                encodeInteger(out, 0x80, 7, staticTableIndex);
            }
            return;
        }

        // If the headerSize is greater than the max table size then it must be encoded literally
        if (headerSize > maxHeaderTableSize) {
            int nameIndex = getNameIndex(name);
            encodeLiteral(out, name, value, IndexType.NONE, nameIndex);
            return;
        }

        int nameHash = AsciiString.hashCode(name);
        int valueHash = AsciiString.hashCode(value);
        NameValueEntry headerField = getEntryInsensitive(name, nameHash, value, valueHash);
        if (headerField != null) {
            // Section 6.1. Indexed Header Field Representation
            encodeInteger(out, 0x80, 7, getIndexPlusOffset(headerField.counter));
        } else {
            int staticTableIndex = HpackStaticTable.getIndexInsensitive(name, value);
            if (staticTableIndex != HpackStaticTable.NOT_FOUND) {
                // Section 6.1. Indexed Header Field Representation
                encodeInteger(out, 0x80, 7, staticTableIndex);
            } else {
                ensureCapacity(headerSize);
                encodeAndAddEntries(out, name, nameHash, value, valueHash);
                size += headerSize;
            }
        }
    }

    private void encodeAndAddEntries(ByteBuf out, CharSequence name, int nameHash, CharSequence value, int valueHash) {
        int staticTableIndex = HpackStaticTable.getIndex(name);
        int nextCounter = latestCounter() - 1;
        if (staticTableIndex == HpackStaticTable.NOT_FOUND) {
            NameEntry e = getEntry(name, nameHash);
            if (e == null) {
                encodeLiteral(out, name, value, IndexType.INCREMENTAL, NOT_FOUND);
                addNameEntry(name, nameHash, nextCounter);
                addNameValueEntry(name, value, nameHash, valueHash, nextCounter);
            } else {
                encodeLiteral(out, name, value, IndexType.INCREMENTAL, getIndexPlusOffset(e.counter));
                addNameValueEntry(e.name, value, nameHash, valueHash, nextCounter);

                // The name entry should always point to the latest counter.
                e.counter = nextCounter;
            }
        } else {
            encodeLiteral(out, name, value, IndexType.INCREMENTAL, staticTableIndex);
            // use the name from the static table to optimize memory usage.
            addNameValueEntry(
              HpackStaticTable.getEntry(staticTableIndex).name, value, nameHash, valueHash, nextCounter);
        }
    }

    /**
     * Set the maximum table size.
     */
    public void setMaxHeaderTableSize(ByteBuf out, long maxHeaderTableSize) throws Http2Exception {
        if (maxHeaderTableSize < MIN_HEADER_TABLE_SIZE || maxHeaderTableSize > MAX_HEADER_TABLE_SIZE) {
            throw connectionError(PROTOCOL_ERROR, "Header Table Size must be >= %d and <= %d but was %d",
              MIN_HEADER_TABLE_SIZE, MAX_HEADER_TABLE_SIZE, maxHeaderTableSize);
        }
        if (this.maxHeaderTableSize == maxHeaderTableSize) {
            return;
        }
        this.maxHeaderTableSize = maxHeaderTableSize;
        ensureCapacity(0);
        // Casting to integer is safe as we verified the maxHeaderTableSize is a valid unsigned int.
        encodeInteger(out, 0x20, 5, maxHeaderTableSize);
    }

    /**
     * Return the maximum table size.
     */
    public long getMaxHeaderTableSize() {
        return maxHeaderTableSize;
    }

    public void setMaxHeaderListSize(long maxHeaderListSize) throws Http2Exception {
        if (maxHeaderListSize < MIN_HEADER_LIST_SIZE || maxHeaderListSize > MAX_HEADER_LIST_SIZE) {
            throw connectionError(PROTOCOL_ERROR, "Header List Size must be >= %d and <= %d but was %d",
              MIN_HEADER_LIST_SIZE, MAX_HEADER_LIST_SIZE, maxHeaderListSize);
        }
        this.maxHeaderListSize = maxHeaderListSize;
    }

    public long getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    /**
     * Encode integer according to <a href="https://tools.ietf.org/html/rfc7541#section-5.1">Section 5.1</a>.
     */
    private static void encodeInteger(ByteBuf out, int mask, int n, int i) {
        encodeInteger(out, mask, n, (long) i);
    }

    /**
     * Encode integer according to <a href="https://tools.ietf.org/html/rfc7541#section-5.1">Section 5.1</a>.
     */
    private static void encodeInteger(ByteBuf out, int mask, int n, long i) {
        assert n >= 0 && n <= 8 : "N: " + n;
        int nbits = 0xFF >>> 8 - n;
        if (i < nbits) {
            out.writeByte((int) (mask | i));
        } else {
            out.writeByte(mask | nbits);
            long length = i - nbits;
            for (; (length & ~0x7F) != 0; length >>>= 7) {
                out.writeByte((int) (length & 0x7F | 0x80));
            }
            out.writeByte((int) length);
        }
    }

    /**
     * Encode string literal according to Section 5.2.
     */
    private void encodeStringLiteral(ByteBuf out, CharSequence string) {
        int huffmanLength;
        if (string.length() >= huffCodeThreshold
          && (huffmanLength = hpackHuffmanEncoder.getEncodedLength(string)) < string.length()) {
            encodeInteger(out, 0x80, 7, huffmanLength);
            hpackHuffmanEncoder.encode(out, string);
        } else {
            encodeInteger(out, 0x00, 7, string.length());
            if (string instanceof AsciiString) {
                // Fast-path
                AsciiString asciiString = (AsciiString) string;
                out.writeBytes(asciiString.array(), asciiString.arrayOffset(), asciiString.length());
            } else {
                // Only ASCII is allowed in http2 headers, so it is fine to use this.
                // https://tools.ietf.org/html/rfc7540#section-8.1.2
                out.writeCharSequence(string, CharsetUtil.ISO_8859_1);
            }
        }
    }

    /**
     * Encode literal header field according to Section 6.2.
     */
    private void encodeLiteral(ByteBuf out, CharSequence name, CharSequence value, IndexType indexType,
                               int nameIndex) {
        boolean nameIndexValid = nameIndex != NOT_FOUND;
        switch (indexType) {
            case INCREMENTAL:
                encodeInteger(out, 0x40, 6, nameIndexValid ? nameIndex : 0);
                break;
            case NONE:
                encodeInteger(out, 0x00, 4, nameIndexValid ? nameIndex : 0);
                break;
            case NEVER:
                encodeInteger(out, 0x10, 4, nameIndexValid ? nameIndex : 0);
                break;
            default:
                throw new Error("should not reach here");
        }
        if (!nameIndexValid) {
            encodeStringLiteral(out, name);
        }
        encodeStringLiteral(out, value);
    }

    private int getNameIndex(CharSequence name) {
        int index = HpackStaticTable.getIndex(name);
        if (index != HpackStaticTable.NOT_FOUND) {
            return index;
        }
        NameEntry e = getEntry(name, AsciiString.hashCode(name));
        return e == null ? NOT_FOUND : getIndexPlusOffset(e.counter);
    }

    /**
     * Ensure that the dynamic table has enough room to hold 'headerSize' more bytes. Removes the
     * oldest entry from the dynamic table until sufficient space is available.
     */
    private void ensureCapacity(long headerSize) {
        while (maxHeaderTableSize - size < headerSize) {
            remove();
        }
    }

    /**
     * Return the number of header fields in the dynamic table. Exposed for testing.
     */
    int length() {
        return isEmpty() ? 0 : getIndex(head.after.counter);
    }

    /**
     * Return the size of the dynamic table. Exposed for testing.
     */
    long size() {
        return size;
    }

    /**
     * Return the header field at the given index. Exposed for testing.
     */
    HpackHeaderField getHeaderField(int index) {
        NameValueEntry entry = head;
        while (index++ < length()) {
            entry = entry.after;
        }
        return entry;
    }

    /**
     * Returns the header entry with the lowest index value for the header field. Returns null if
     * header field is not in the dynamic table.
     */
    private NameValueEntry getEntryInsensitive(CharSequence name, int nameHash, CharSequence value, int valueHash) {
        int h = hash(nameHash, valueHash);
        for (NameValueEntry e = nameValueEntries[bucket(h)]; e != null; e = e.next) {
            if (e.hash == h && equalsVariableTime(value, e.value) && equalsVariableTime(name, e.name)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Returns the lowest index value for the header field name in the dynamic table. Returns -1 if
     * the header field name is not in the dynamic table.
     */
    private NameEntry getEntry(CharSequence name, int nameHash) {
        for (NameEntry e = nameEntries[bucket(nameHash)]; e != null; e = e.next) {
            if (e.hash == nameHash && equalsConstantTime(name, e.name) != 0) {
                return e;
            }
        }
        return null;
    }

    private int getIndexPlusOffset(int counter) {
        return getIndex(counter) + HpackStaticTable.length;
    }

    /**
     * Compute the index into the dynamic table given the counter in the header entry.
     */
    private int getIndex(int counter) {
        return counter - latestCounter() + 1;
    }

    private int latestCounter() {
        return latest.counter;
    }

    private void addNameEntry(CharSequence name, int nameHash, int nextCounter) {
        int bucket = bucket(nameHash);
        nameEntries[bucket] = new NameEntry(nameHash, name, nextCounter, nameEntries[bucket]);
    }

    private void addNameValueEntry(CharSequence name, CharSequence value,
                                   int nameHash, int valueHash, int nextCounter) {
        int hash = hash(nameHash, valueHash);
        int bucket = bucket(hash);
        NameValueEntry e = new NameValueEntry(hash, name, value, nextCounter, nameValueEntries[bucket]);
        nameValueEntries[bucket] = e;
        latest.after = e;
        latest = e;
    }

    /**
     * Remove the oldest header field from the dynamic table.
     */
    private void remove() {
        NameValueEntry eldest = head.after;
        removeNameValueEntry(eldest);
        removeNameEntryMatchingCounter(eldest.name, eldest.counter);
        head.after = eldest.after;
        eldest.unlink();
        size -= eldest.size();
        if (isEmpty()) {
            latest = head;
        }
    }

    private boolean isEmpty() {
        return size == 0;
    }

    private void removeNameValueEntry(NameValueEntry eldest) {
        int bucket = bucket(eldest.hash);
        NameValueEntry e = nameValueEntries[bucket];
        if (e == eldest) {
            nameValueEntries[bucket] = eldest.next;
        } else {
            while (e.next != eldest) {
                e = e.next;
            }
            e.next = eldest.next;
        }
    }

    private void removeNameEntryMatchingCounter(CharSequence name, int counter) {
        int hash = AsciiString.hashCode(name);
        int bucket = bucket(hash);
        NameEntry e = nameEntries[bucket];
        if (e == null) {
            return;
        }
        if (counter == e.counter) {
            nameEntries[bucket] = e.next;
            e.unlink();
        } else {
            NameEntry prev = e;
            e = e.next;
            while (e != null) {
                if (counter == e.counter) {
                    prev.next = e.next;
                    e.unlink();
                    break;
                }
                prev = e;
                e = e.next;
            }
        }
    }

    /**
     * Returns the bucket of the hash table for the hash code h.
     */
    private int bucket(int h) {
        return h & hashMask;
    }

    private static int hash(int nameHash, int valueHash) {
        return 31 * nameHash + valueHash;
    }

    private static final class NameEntry {
        NameEntry next;

        final CharSequence name;

        final int hash;

        // This is used to compute the index in the dynamic table.
        int counter;

        NameEntry(int hash, CharSequence name, int counter, NameEntry next) {
            this.hash = hash;
            this.name = name;
            this.counter = counter;
            this.next = next;
        }

        void unlink() {
            next = null; // null references to prevent nepotism in generational GC.
        }
    }

    private static final class NameValueEntry extends HpackHeaderField {
        // This field comprises the linked list used for implementing the eviction policy.
        NameValueEntry after;

        NameValueEntry next;

        // hash of both name and value
        final int hash;

        // This is used to compute the index in the dynamic table.
        final int counter;

        NameValueEntry(int hash, CharSequence name, CharSequence value, int counter, NameValueEntry next) {
            super(name, value);
            this.next = next;
            this.hash = hash;
            this.counter = counter;
        }

        void unlink() {
            after = null; // null references to prevent nepotism in generational GC.
            next = null;
        }
    }
}
