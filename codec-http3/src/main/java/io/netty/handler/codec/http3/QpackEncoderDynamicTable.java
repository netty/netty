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
package io.netty.handler.codec.http3;

import io.netty.util.AsciiString;
import org.jetbrains.annotations.Nullable;

import static io.netty.handler.codec.http3.QpackHeaderField.ENTRY_OVERHEAD;
import static io.netty.handler.codec.http3.QpackUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http3.QpackUtil.MIN_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http3.QpackUtil.equalsVariableTime;
import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.internal.MathUtil.findNextPositivePowerOfTwo;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;

final class QpackEncoderDynamicTable {
    private static final QpackException INVALID_KNOW_RECEIVED_COUNT_INCREMENT =
            QpackException.newStatic(QpackDecoder.class, "incrementKnownReceivedCount(...)",
                    "QPACK - invalid known received count increment.");
    private static final QpackException INVALID_REQUIRED_INSERT_COUNT_INCREMENT =
            QpackException.newStatic(QpackDecoder.class, "acknowledgeInsertCount(...)",
                    "QPACK - invalid required insert count acknowledgment.");
    private static final QpackException INVALID_TABLE_CAPACITY =
            QpackException.newStatic(QpackDecoder.class, "validateCapacity(...)",
                    "QPACK - dynamic table capacity is invalid.");
    private static final QpackException CAPACITY_ALREADY_SET =
            QpackException.newStatic(QpackDecoder.class, "maxTableCapacity(...)",
                    "QPACK - dynamic table capacity is already set.");
    /**
     * Special return value of {@link #getEntryIndex(CharSequence, CharSequence)} when the entry is not found.
     */
    public static final int NOT_FOUND = Integer.MIN_VALUE;

    /**
     * A hashmap of header entries.
     */
    private final HeaderEntry[] fields;

    /**
     * Percentage of capacity that we expect to be free after eviction of old entries.
     */
    private final int expectedFreeCapacityPercentage;

    /**
     * Hash mask for all entries in the hashmap.
     */
    private final byte hashMask;

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-dynamic-table-size">Current size of the table</a>.
     */
    private long size;

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-maximum-dynamic-table-capac">
     *     Maximum capacity of the table</a>. This is set once based on the
     *     {@link Http3SettingsFrame#HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY} received by the remote peer.
     */
    private long maxTableCapacity = -1;

    /*
     * The below indexes follow the suggested heuristics in Section 2.1.1.1 Avoiding Prohibited insertions
     * https://www.rfc-editor.org/rfc/rfc9204.html#name-avoiding-prohibited-inserti
     *
     *                Tail                             Drain       Head
     *                 |                                |           |
     *                 v                                v           v
     *       +--------+---------------------------------+----------+
     *       | Unused |          Referenceable          | Draining |
     *       | Space  |             Entries             | Entries  |
     *       +--------+---------------------------------+----------+
     *                ^                                 ^          ^
     *                |                                 |          |
     *          Insertion Index                 Draining Index  Dropping Index
     */

    /**
     * Head of the entries, such that {@link HeaderEntry#index} is the {@code droppingIndex}.
     */
    private final HeaderEntry head;

    /**
     * Pointer before which entries are marked for eviction post {@link #incrementKnownReceivedCount(int)}.
     * {@link HeaderEntry#index} is the {@code drainingIndex}.
     */
    private HeaderEntry drain;

     /**
     * Pointer to the entry representing the <a
     * href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-known-received-count">
     * known received count</a>.
     */
    private HeaderEntry knownReceived;

    /**
     * Tail of the entries, such that {@link HeaderEntry#index} is the {@code insertionIndex}.
     */
    private HeaderEntry tail;

    QpackEncoderDynamicTable() {
        this(16, 10);
    }

    QpackEncoderDynamicTable(int arraySizeHint, int expectedFreeCapacityPercentage) {
        // Enforce a bound of [2, 128] because hashMask is a byte. The max possible value of hashMask is one less
        // than the length of this array, and we want the mask to be > 0.
        fields = new HeaderEntry[findNextPositivePowerOfTwo(max(2, min(arraySizeHint, 128)))];
        hashMask = (byte) (fields.length - 1);
        // Start with index -1 so the first added header will have the index of 0.
        // See https://www.rfc-editor.org/rfc/rfc9204.html#name-absolute-indexing
        head = new HeaderEntry(-1, EMPTY_STRING, EMPTY_STRING, -1, null);
        this.expectedFreeCapacityPercentage = expectedFreeCapacityPercentage;
        resetIndicesToHead();
    }

    /**
     * Add a name - value pair to the dynamic table and returns the index.
     *
     * @param name          the name.
     * @param value         the value.
     * @param headerSize    the size of the header.
     * @return              the absolute index or {@code -1) if it could not be added.
     */
    int add(CharSequence name, CharSequence value, long headerSize) {
        if (maxTableCapacity - size < headerSize) {
            return -1;
        }

        if (tail.index == Integer.MAX_VALUE) {
            // Wait for all entries to evict before we restart indexing from zero
            evictUnreferencedEntries();
            return -1;
        }
        int h = AsciiString.hashCode(name);
        int i = index(h);
        HeaderEntry old = fields[i];
        HeaderEntry e = new HeaderEntry(h, name, value, tail.index + 1, old);
        fields[i] = e;
        e.addNextTo(tail);
        tail = e;
        size += headerSize;

        ensureFreeCapacity();
        return e.index;
    }

    /**
     * Callback when a header block which had a {@link #insertCount()}} greater than {@code 0} is
     * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-section-acknowledgment">acknowledged</a>
     * by the decoder.
     *
     * @param entryIndex For the entry corresponding to the {@link #insertCount()}.
     * @throws QpackException If the count is invalid.
     */
    void acknowledgeInsertCountOnAck(int entryIndex) throws QpackException {
        acknowledgeInsertCount(entryIndex, true);
    }

    /**
     * Callback when a header block which had a {@link #insertCount()}} greater than {@code 0} is still not processed
     * and the stream is <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-stream-cancellation">cancelled</a>
     * by the decoder.
     *
     * @param entryIndex For the entry corresponding to the {@link #insertCount()}.
     * @throws QpackException If the count is invalid.
     */
    void acknowledgeInsertCountOnCancellation(int entryIndex) throws QpackException {
        acknowledgeInsertCount(entryIndex, false);
    }

    private void acknowledgeInsertCount(int entryIndex, boolean updateKnownReceived) throws QpackException {
        if (entryIndex < 0) {
            throw INVALID_REQUIRED_INSERT_COUNT_INCREMENT;
        }
        for (HeaderEntry e = head.next; e != null; e = e.next) {
            if (e.index == entryIndex) {
                assert e.refCount > 0;
                e.refCount--;
                if (updateKnownReceived && e.index > knownReceived.index) {
                    // https://www.rfc-editor.org/rfc/rfc9204.html#name-known-received-count
                    // If the Required Insert Count of the acknowledged field section is greater than the current Known
                    // Received Count, Known Received Count is updated to that Required Insert Count value.
                    knownReceived = e;
                }
                evictUnreferencedEntries();
                return;
            }
        }
        // We have reached the end of the linked list so the index was invalid and hence the connection should
        // be closed.
        // https://www.rfc-editor.org/rfc/rfc9204.html#section-4.4
        throw INVALID_REQUIRED_INSERT_COUNT_INCREMENT;
    }

    /**
     * Callback when a decoder <a
     * href="https://www.rfc-editor.org/rfc/rfc9204.html#name-insert-count-increment">increments its
     * insert count.</a>
     *
     * @param knownReceivedCountIncr Increment count.
     * @throws QpackException If the increment count is invalid.
     */
    void incrementKnownReceivedCount(int knownReceivedCountIncr) throws QpackException {
        if (knownReceivedCountIncr <= 0) {
            throw INVALID_KNOW_RECEIVED_COUNT_INCREMENT;
        }
        while (knownReceived.next != null && knownReceivedCountIncr > 0) {
            knownReceived = knownReceived.next;
            knownReceivedCountIncr--;
        }
        if (knownReceivedCountIncr == 0) {
            evictUnreferencedEntries();
            return;
        }
        // We have reached the end of the linked list so the index was invalid and hence the connection should be
        // closed.
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-decoder-instructions
        throw INVALID_KNOW_RECEIVED_COUNT_INCREMENT;
    }

    /**
     * Returns the number of entries inserted to this dynamic table.
     *
     * @return number the added entries.
     */
    int insertCount() {
        return tail.index + 1;
    }

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-required-insert-count">
     *     Encodes the required insert count.</a>
     * @param reqInsertCount    the required insert count.
     * @return                  the encoded count.
     */
    int encodedRequiredInsertCount(int reqInsertCount) {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-required-insert-count
        // if ReqInsertCount == 0:
        //      EncInsertCount = 0
        // else:
        //      EncInsertCount = (ReqInsertCount mod (2 * MaxEntries)) + 1
        //
        return reqInsertCount == 0 ? 0 : reqInsertCount % toIntExact(2 * QpackUtil.maxEntries(maxTableCapacity)) + 1;
    }

    // Visible for tests
    int encodedKnownReceivedCount() {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-known-received-count
        return encodedRequiredInsertCount(knownReceived.index + 1);
    }

    /**
     * Set the maximum capacity of the dynamic table. This can only be set once.
     * @param capacity          the capacity
     * @throws QpackException   if capacity was set before.
     */
    void maxTableCapacity(long capacity) throws QpackException {
        validateCapacity(capacity);
        if (this.maxTableCapacity >= 0) {
            throw CAPACITY_ALREADY_SET;
        }
        this.maxTableCapacity = capacity;
    }

    /**
     * Transforms the passed {@code entryIndex} as a <a
     * href="https://www.rfc-editor.org/rfc/rfc9204.html#name-relative-indexing">relative index for
     * encoder instructions</a>.
     *
     * @param entryIndex to transform.
     * @return Relative index for the passed {@code entryIndex}.
     */
    int relativeIndexForEncoderInstructions(int entryIndex) {
        assert entryIndex >= 0;
        assert entryIndex <= tail.index;
        return tail.index - entryIndex;
    }

    /**
     * Finds an entry with the passed {@code name} and {@code value} in this dynamic table.
     *
     * @param name of the entry to find.
     * @param value of the entry to find.
     * @return {@link #NOT_FOUND} if the entry does not exist. If an entry with matching {@code name} and {@code value}
     * exists, then the index is returned. If an entry with only matching name exists then {@code -index-1} is
     * returned.
     */
    int getEntryIndex(@Nullable CharSequence name, @Nullable CharSequence value) {
        if (tail != head && name != null && value != null) {
            int h = AsciiString.hashCode(name);
            int i = index(h);
            HeaderEntry firstNameMatch = null;
            HeaderEntry entry = null;
            for (HeaderEntry e = fields[i]; e != null; e = e.nextSibling) {
                if (e.hash == h && equalsVariableTime(value, e.value)) {
                    if (equalsVariableTime(name, e.name)) {
                        entry = e;
                        break;
                    }
                } else if (firstNameMatch == null && equalsVariableTime(name, e.name)) {
                    firstNameMatch = e;
                }
            }
            if (entry != null) {
                return entry.index;
            }
            if (firstNameMatch != null) {
                return -firstNameMatch.index - 1;
            }
        }
        return NOT_FOUND;
    }

    /**
     * Adds a reference to an entry at the passed {@code idx}.
     *
     * @param name of the entry for lookups, not verified for the entry at the pased {@code idx}
     * @param value of the entry for lookups, not verified for the entry at the pased {@code idx}
     * @param idx of the entry.
     * @return <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-required-insert-count">Required
     * insert count</a> if the passed entry has to be referenced in a header block.
     */
    int addReferenceToEntry(@Nullable CharSequence name, @Nullable CharSequence value, int idx) {
        if (tail != head && name != null && value != null) {
            int h = AsciiString.hashCode(name);
            int i = index(h);
            for (HeaderEntry e = fields[i]; e != null; e = e.nextSibling) {
                if (e.hash == h && idx == e.index) {
                    e.refCount++;
                    return e.index + 1;
                }
            }
        }
        throw new IllegalArgumentException("Index " + idx + " not found");
    }

    boolean requiresDuplication(int idx, long size) {
        assert head != tail;

        if (this.size + size > maxTableCapacity || head == drain) {
            return false;
        }
        return idx >= head.next.index && idx <= drain.index;
    }

    private void evictUnreferencedEntries() {
        if (head == knownReceived || head == drain) {
            return;
        }

        while (head.next != null && head.next != knownReceived.next && head.next != drain.next) {
            if (!removeIfUnreferenced()) {
                return;
            }
        }
    }

    private boolean removeIfUnreferenced() {
        final HeaderEntry toRemove = head.next;
        if (toRemove.refCount != 0) {
            return false;
        }
        size -= toRemove.size();

        // Remove from the hash map
        final int i = index(toRemove.hash);
        HeaderEntry e = fields[i];
        HeaderEntry prev = null;
        while (e != null && e != toRemove) {
            prev = e;
            e = e.nextSibling;
        }
        if (e == toRemove) {
            if (prev == null) {
                fields[i] = e.nextSibling;
            } else {
                prev.nextSibling = e.nextSibling;
            }
        }

        // Remove from the linked list
        toRemove.remove(head);
        if (toRemove == tail) {
            resetIndicesToHead();
        }
        if (toRemove == drain) {
            drain = head;
        }
        if (toRemove == knownReceived) {
            knownReceived = head;
        }
        return true;
    }

    private void resetIndicesToHead() {
        tail = head;
        drain = head;
        knownReceived = head;
    }

    private void ensureFreeCapacity() {
        long maxDesiredSize = max(ENTRY_OVERHEAD, ((100 - expectedFreeCapacityPercentage) * maxTableCapacity) / 100);
        long cSize = size;
        HeaderEntry nDrain;
        for (nDrain = head; nDrain.next != null && cSize > maxDesiredSize; nDrain = nDrain.next) {
            cSize -= nDrain.next.size();
        }
        if (cSize != size) {
            drain = nDrain;
            evictUnreferencedEntries();
        }
    }

    private int index(int h) {
        return h & hashMask;
    }

    private static void validateCapacity(long capacity) throws QpackException {
        if (capacity < MIN_HEADER_TABLE_SIZE || capacity > MAX_HEADER_TABLE_SIZE) {
            throw INVALID_TABLE_CAPACITY;
        }
    }

    /**
     * An entry for the {@link #fields} HashMap. This entry provides insertion order iteration using {@link #next}.
     */
    private static final class HeaderEntry extends QpackHeaderField {
        /**
         * Pointer to the next entry in insertion order with a different {@link #hash} than this entry.
         */
        HeaderEntry next;

        /**
         * Pointer to the next entry in insertion order with the same {@link #hash} as this entry, a.k.a hash collisions
         */
        HeaderEntry nextSibling;

        /**
         * Number of header blocks that refer to this entry as the value for its <a
         * href="https://www.rfc-editor.org/rfc/rfc9204.html#name-required-insert-count">
         * required insert count</a>
         */
        int refCount;

        /**
         * Hashcode for this entry.
         */
        final int hash;

        /**
         * Insertion index for this entry.
         */
        final int index;

        HeaderEntry(int hash, CharSequence name, CharSequence value, int index, @Nullable HeaderEntry nextSibling) {
            super(name, value);
            this.index = index;
            this.hash = hash;
            this.nextSibling = nextSibling;
        }

        void remove(HeaderEntry prev) {
            assert prev != this;
            prev.next = next;
            next = null; // null references to prevent nepotism in generational GC.
            nextSibling = null;
        }

        void addNextTo(HeaderEntry prev) {
            assert prev != this;
            this.next = prev.next;
            prev.next = this;
        }
    }
}
