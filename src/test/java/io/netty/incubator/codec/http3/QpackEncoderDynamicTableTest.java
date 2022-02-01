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

import org.junit.jupiter.api.Test;

import static io.netty.incubator.codec.http3.QpackUtil.MAX_HEADER_TABLE_SIZE;
import static java.lang.Math.toIntExact;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QpackEncoderDynamicTableTest {
    private static final QpackHeaderField emptyHeader = new QpackHeaderField("", "");
    private static final QpackHeaderField fooBarHeader = new QpackHeaderField("foo", "bar");
    private static final QpackHeaderField fooBar2Header = new QpackHeaderField("foo", "bar2");
    private static final QpackHeaderField fooBar3Header = new QpackHeaderField("foo", "bar3");

    private int insertCount;
    private long maxCapacity;

    @Test
    public void zeroCapacityIsAllowed() throws Exception {
        QpackEncoderDynamicTable table = newDynamicTable(0);

        assertThat("Header addition passed.", addHeader(table, emptyHeader),
                lessThan(0));
    }

    @Test
    public void maxCapacityIsAllowed() throws Exception {
        final QpackEncoderDynamicTable table = newDynamicTable(MAX_HEADER_TABLE_SIZE);
        addAndValidateHeader(table, emptyHeader);
    }

    @Test
    public void negativeCapacityIsDisallowed() {
        assertThrows(QpackException.class, () -> newDynamicTable(-1));
    }

    @Test
    public void capacityTooLarge() {
        assertThrows(QpackException.class, () -> newDynamicTable(Long.MAX_VALUE));
    }

    @Test
    public void delayAck() throws Exception {
        QpackEncoderDynamicTable table = newDynamicTable(16, 50, 128);

        addAndValidateHeader(table, emptyHeader);
        addAndValidateHeader(table, fooBarHeader);
        final int idx2 = addAndValidateHeader(table, fooBar2Header);

        assertThat("Header addition passed.", addHeader(table, fooBarHeader), lessThan(0));

        table.incrementKnownReceivedCount(3);
        assertThat("Unexpected entry index.", getEntryIndex(table, emptyHeader), lessThan(0));
        assertThat("Unexpected entry index.", getEntryIndex(table, fooBarHeader), lessThan(0));
        assertThat("Unexpected entry index.", getEntryIndex(table, fooBar2Header), is(idx2));

        final int idx1 = addAndValidateHeader(table, emptyHeader);
        assertThat("Unexpected entry index.", getEntryIndex(table, emptyHeader), is(idx1));
        assertThat("Unexpected entry index.", getEntryIndex(table, fooBar2Header), lessThan(0));
    }

    @Test
    public void addAndGet() throws Exception {
        QpackEncoderDynamicTable table = newDynamicTable(128);

        final int idx1 = addValidateAndAckHeader(table, emptyHeader);
        assertThat("Unexpected entry index.", getEntryIndex(table, emptyHeader), is(idx1));

        final int idx2 = addValidateAndAckHeader(table, fooBarHeader);
        assertThat("Unexpected entry index.", getEntryIndex(table, fooBarHeader), is(idx2));

        assertThat("Unexpected entry index.", getEntryIndex(table, emptyHeader), is(idx1));
    }

    @Test
    public void nameOnlyMatch() throws Exception {
        final QpackEncoderDynamicTable table = newDynamicTable(128);
        addValidateAndAckHeader(table, fooBarHeader);
        final int lastIdx = addValidateAndAckHeader(table, fooBar2Header);

        final int idx = table.getEntryIndex("foo", "baz");
        assertThat("Unexpected index.", idx, lessThan(0));
        assertThat("Unexpected index.", idx, is(-lastIdx - 1));
    }

    @Test
    public void addDuplicateEntries() throws Exception {
        QpackEncoderDynamicTable table = newDynamicTable(128);

        final int idx1 = addValidateAndAckHeader(table, emptyHeader);
        assertThat("Unexpected entry index.", getEntryIndex(table, emptyHeader), is(idx1));

        final int idx2 = addValidateAndAckHeader(table, fooBarHeader);
        assertThat("Unexpected entry index.", getEntryIndex(table, fooBarHeader), is(idx2));

        final int idx3 = addValidateAndAckHeader(table, emptyHeader);
        // Return the most recent entry
        assertThat("Unexpected entry index.", getEntryIndex(table, emptyHeader), is(idx3));
    }

    @Test
    public void hashCollisionThenRemove() throws Exception {
        // expected max size: 0.9*128 = 115
        QpackEncoderDynamicTable table = newDynamicTable(16, 10, 128);
        addValidateAndAckHeader(table, fooBarHeader); // size = 38
        addValidateAndAckHeader(table, fooBar2Header); // size = 77

        addValidateAndAckHeader(table, fooBar3Header); // size = 116, exceeds max threshold, should evict eldest

        assertThat("Entry found.", getEntryIndex(table, fooBarHeader), lessThan(0));
        assertThat("Entry not found.", getEntryIndex(table, fooBar2Header), greaterThanOrEqualTo(0));
        assertThat("Entry not found.", getEntryIndex(table, fooBar3Header), greaterThanOrEqualTo(0));
    }

    @Test
    public void requiredInsertCountWrapsAround() throws Exception {
        // maxIndex = 2 * maxEntries = 2 * 64/32 = 4
        QpackEncoderDynamicTable table = newDynamicTable(64);

        addValidateAndAckHeader(table, emptyHeader);
        addValidateAndAckHeader(table, emptyHeader);
        addValidateAndAckHeader(table, emptyHeader);
        addValidateAndAckHeader(table, emptyHeader);
        addValidateAndAckHeader(table, emptyHeader);
    }

    @Test
    public void indexWrapsAroundForSingleEntryCapacity() throws Exception {
        // maxIndex = 2 * maxEntries = 2 * 39/32 = 2
        QpackEncoderDynamicTable table = newDynamicTable(fooBar2Header.size());
        addValidateAndAckHeader(table, fooBar2Header);
        verifyTableEmpty(table);
        addValidateAndAckHeader(table, fooBar2Header);
    }

    @Test
    public void sectionAck() throws Exception {
        QpackEncoderDynamicTable table = newDynamicTable(128);

        final int idx = addAndValidateHeader(table, fooBarHeader);
        table.addReferenceToEntry(fooBarHeader.name, fooBarHeader.value, idx);
        table.acknowledgeInsertCount(idx);

        assertThat("Unexpected known received count.", table.knownReceivedCount(), is(2));
    }

    @Test
    public void sectionAckOutOfOrder() throws Exception {
        QpackEncoderDynamicTable table = newDynamicTable(128);

        final int idx1 = addAndValidateHeader(table, fooBarHeader);
        table.addReferenceToEntry(fooBarHeader.name, fooBarHeader.value, idx1);

        final int idx2 = addAndValidateHeader(table, fooBarHeader);
        table.addReferenceToEntry(fooBarHeader.name, fooBarHeader.value, idx2);

        table.acknowledgeInsertCount(idx2);
        assertThat("Unexpected known received count.", table.knownReceivedCount(), is(3));

        table.acknowledgeInsertCount(idx1);
        assertThat("Unexpected known received count.", table.knownReceivedCount(), is(3)); // already acked
    }

    @Test
    public void multipleReferences() throws Exception {
        // maxIndex = 2 * maxEntries = 2 * 39/32 = 2
        QpackEncoderDynamicTable table = newDynamicTable(fooBar3Header.size());

        final int idx1 = addAndValidateHeader(table, fooBar3Header);
        table.addReferenceToEntry(fooBar3Header.name, fooBar3Header.value, idx1);
        table.addReferenceToEntry(fooBar3Header.name, fooBar3Header.value, idx1);

        table.acknowledgeInsertCount(idx1);

        // first entry still active
        assertThat("Header added", addHeader(table, fooBar2Header), lessThan(0));

        table.acknowledgeInsertCount(idx1);
        verifyTableEmpty(table);
        addAndValidateHeader(table, fooBarHeader);
    }

    private void verifyTableEmpty(QpackEncoderDynamicTable table) {
        assertThat(table.insertCount(), is(0));
        insertCount = 0;
    }

    private int getEntryIndex(QpackEncoderDynamicTable table, QpackHeaderField emptyHeader) {
        return table.getEntryIndex(emptyHeader.name, emptyHeader.value);
    }

    private int addHeader(QpackEncoderDynamicTable table, QpackHeaderField header) {
        final int idx = table.add(header.name, header.value, header.size());
        if (idx >= 0) {
            insertCount++;
        }
        return idx;
    }

    private int addAndValidateHeader(QpackEncoderDynamicTable table, QpackHeaderField header) {
        final int addedIdx = addHeader(table, header);
        assertThat("Header addition failed.", addedIdx, greaterThanOrEqualTo(0));
        verifyInsertCount(table);
        return addedIdx;
    }

    private int addValidateAndAckHeader(QpackEncoderDynamicTable table, QpackHeaderField header) throws Exception {
        final int addedIdx = addAndValidateHeader(table, header);
        table.addReferenceToEntry(header.name, header.value, addedIdx);
        table.acknowledgeInsertCount(addedIdx);
        return addedIdx;
    }

    private QpackEncoderDynamicTable newDynamicTable(int arraySizeHint, int expectedFreeCapacityPercentage,
                                                     long maxCapacity) throws Exception {
        return setMaxTableCapacity(maxCapacity,
                new QpackEncoderDynamicTable(arraySizeHint, expectedFreeCapacityPercentage));
    }

    private QpackEncoderDynamicTable newDynamicTable(long maxCapacity) throws Exception {
        return setMaxTableCapacity(maxCapacity, new QpackEncoderDynamicTable());
    }

    private QpackEncoderDynamicTable setMaxTableCapacity(long maxCapacity, QpackEncoderDynamicTable table)
            throws Exception {
        table.maxTableCapacity(maxCapacity);
        this.maxCapacity = maxCapacity;
        return table;
    }

    private void verifyInsertCount(QpackEncoderDynamicTable table) {
        assertThat("Unexpected required insert count.", table.requiredInsertCount(), is(expectedInsertCount()));
    }

    private int expectedInsertCount() {
        return insertCount == 0 ? 0 : toIntExact((insertCount % (2 * Math.floorDiv(maxCapacity, 32))) + 1);
    }
}
