/*
 * Copyright 2021 The Netty Project
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

import java.util.Arrays;

import static io.netty.incubator.codec.http3.QpackHeaderField.ENTRY_OVERHEAD;
import static io.netty.incubator.codec.http3.QpackUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty.incubator.codec.http3.QpackUtil.MIN_HEADER_TABLE_SIZE;
import static io.netty.incubator.codec.http3.QpackUtil.toIntOrThrow;
import static java.lang.Math.floorDiv;

final class QpackDecoderDynamicTable {
    private static final QpackException GET_ENTRY_ILLEGAL_INDEX_VALUE =
            QpackException.newStatic(QpackDecoderDynamicTable.class, "getEntry(...)",
                    "QPACK - illegal decoder dynamic table index value");
    private static final QpackException HEADER_TOO_LARGE =
            QpackException.newStatic(QpackDecoderDynamicTable.class, "add(...)", "QPACK - header entry too large.");

    // a circular queue of header fields
    private QpackHeaderField[] fields;
    private int head;
    private int tail;
    private long size;
    private long capacity = -1; // ensure setCapacity creates the array
    private int insertCount;

    int length() {
        return head < tail ? fields.length - tail + head : head - tail;
    }

    long size() {
        return size;
    }

    int insertCount() {
        return insertCount;
    }

    QpackHeaderField getEntry(int index) throws QpackException {
        if (index < 0 || fields == null || index >= fields.length) {
            throw GET_ENTRY_ILLEGAL_INDEX_VALUE;
        }
        QpackHeaderField entry = fields[index];
        if (entry == null) {
            throw GET_ENTRY_ILLEGAL_INDEX_VALUE;
        }
        return entry;
    }

    QpackHeaderField getEntryRelativeEncodedField(int index) throws QpackException {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-relative-indexing
        return getEntry(moduloIndex(index));
    }

    QpackHeaderField getEntryRelativeEncoderInstructions(int index) throws QpackException {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-relative-indexing
        // Name index is the relative index, relative to the last added entry.
        return getEntry(index > tail ? fields.length - index + tail : tail - index);
    }

    void add(QpackHeaderField header) throws QpackException {
        long headerSize = header.size();
        if (headerSize > capacity) {
            throw HEADER_TOO_LARGE;
        }
        while (capacity - size < headerSize) {
            remove();
        }
        insertCount++;
        fields[getAndIncrementHead()] = header;
        size += headerSize;
    }

    private void remove() {
        QpackHeaderField removed = fields[tail];
        if (removed == null) {
            return;
        }
        size -= removed.size();
        fields[getAndIncrementTail()] = null;
    }

    void clear() {
        if (fields != null) {
            Arrays.fill(fields, null);
        }
        head = 0;
        tail = 0;
        size = 0;
    }

    void setCapacity(long capacity) throws QpackException {
        if (capacity < MIN_HEADER_TABLE_SIZE || capacity > MAX_HEADER_TABLE_SIZE) {
            throw new IllegalArgumentException("capacity is invalid: " + capacity);
        }
        // initially capacity will be -1 so init won't return here
        if (this.capacity == capacity) {
            return;
        }
        this.capacity = capacity;

        if (capacity == 0) {
            clear();
        } else {
            // initially size will be 0 so remove won't be called
            while (size > capacity) {
                remove();
            }
        }

        int maxEntries = toIntOrThrow(2 * floorDiv(capacity, ENTRY_OVERHEAD));

        // check if capacity change requires us to reallocate the array
        if (fields != null && fields.length == maxEntries) {
            return;
        }

        QpackHeaderField[] tmp = new QpackHeaderField[maxEntries];

        // initially length will be 0 so there will be no copy
        int len = length();
        if (fields != null && tail != head) {
            if (head > tail) {
                System.arraycopy(fields, tail, tmp, 0, head - tail);
            } else {
                System.arraycopy(fields, 0, tmp, 0, head);
                System.arraycopy(fields, tail, tmp, head, fields.length - tail);
            }
        }

        tail = 0;
        head = tail + len;
        fields = tmp;
    }

    private int getAndIncrementHead() {
        int val = this.head;
        this.head = safeIncrementIndex(val);
        return val;
    }

    private int getAndIncrementTail() {
        int val = this.tail;
        this.tail = safeIncrementIndex(val);
        return val;
    }

    private int safeIncrementIndex(int index) {
        return ++index % fields.length;
    }

    private int moduloIndex(int index) {
        return fields == null ? index : index % fields.length;
    }
}
