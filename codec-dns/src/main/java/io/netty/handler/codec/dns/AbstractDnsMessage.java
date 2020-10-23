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
package io.netty.handler.codec.dns;

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A skeletal implementation of {@link DnsMessage}.
 */
@UnstableApi
public abstract class AbstractDnsMessage extends AbstractReferenceCounted implements DnsMessage {

    private static final ResourceLeakDetector<DnsMessage> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(DnsMessage.class);

    private static final int SECTION_QUESTION = DnsSection.QUESTION.ordinal();
    private static final int SECTION_COUNT = 4;

    private final ResourceLeakTracker<DnsMessage> leak = leakDetector.track(this);
    private short id;
    private DnsOpCode opCode;
    private boolean recursionDesired;
    private byte z;

    // To reduce the memory footprint of a message,
    // each of the following fields is a single record or a list of records.
    private Object questions;
    private Object answers;
    private Object authorities;
    private Object additionals;

    /**
     * Creates a new instance with the specified {@code id} and {@link DnsOpCode#QUERY} opCode.
     */
    protected AbstractDnsMessage(int id) {
        this(id, DnsOpCode.QUERY);
    }

    /**
     * Creates a new instance with the specified {@code id} and {@code opCode}.
     */
    protected AbstractDnsMessage(int id, DnsOpCode opCode) {
        setId(id);
        setOpCode(opCode);
    }

    @Override
    public int id() {
        return id & 0xFFFF;
    }

    @Override
    public DnsMessage setId(int id) {
        this.id = (short) id;
        return this;
    }

    @Override
    public DnsOpCode opCode() {
        return opCode;
    }

    @Override
    public DnsMessage setOpCode(DnsOpCode opCode) {
        this.opCode = checkNotNull(opCode, "opCode");
        return this;
    }

    @Override
    public boolean isRecursionDesired() {
        return recursionDesired;
    }

    @Override
    public DnsMessage setRecursionDesired(boolean recursionDesired) {
        this.recursionDesired = recursionDesired;
        return this;
    }

    @Override
    public int z() {
        return z;
    }

    @Override
    public DnsMessage setZ(int z) {
        this.z = (byte) (z & 7);
        return this;
    }

    @Override
    public int count(DnsSection section) {
        return count(sectionOrdinal(section));
    }

    private int count(int section) {
        final Object records = sectionAt(section);
        if (records == null) {
            return 0;
        }
        if (records instanceof DnsRecord) {
            return 1;
        }

        @SuppressWarnings("unchecked")
        final List<DnsRecord> recordList = (List<DnsRecord>) records;
        return recordList.size();
    }

    @Override
    public int count() {
        int count = 0;
        for (int i = 0; i < SECTION_COUNT; i ++) {
            count += count(i);
        }
        return count;
    }

    @Override
    public <T extends DnsRecord> T recordAt(DnsSection section) {
        return recordAt(sectionOrdinal(section));
    }

    private <T extends DnsRecord> T recordAt(int section) {
        final Object records = sectionAt(section);
        if (records == null) {
            return null;
        }

        if (records instanceof DnsRecord) {
            return castRecord(records);
        }

        @SuppressWarnings("unchecked")
        final List<DnsRecord> recordList = (List<DnsRecord>) records;
        if (recordList.isEmpty()) {
            return null;
        }

        return castRecord(recordList.get(0));
    }

    @Override
    public <T extends DnsRecord> T recordAt(DnsSection section, int index) {
        return recordAt(sectionOrdinal(section), index);
    }

    private <T extends DnsRecord> T recordAt(int section, int index) {
        final Object records = sectionAt(section);
        if (records == null) {
            throw new IndexOutOfBoundsException("index: " + index + " (expected: none)");
        }

        if (records instanceof DnsRecord) {
            if (index == 0) {
                return castRecord(records);
            } else {
                throw new IndexOutOfBoundsException("index: " + index + "' (expected: 0)");
            }
        }

        @SuppressWarnings("unchecked")
        final List<DnsRecord> recordList = (List<DnsRecord>) records;
        return castRecord(recordList.get(index));
    }

    @Override
    public DnsMessage setRecord(DnsSection section, DnsRecord record) {
        setRecord(sectionOrdinal(section), record);
        return this;
    }

    private void setRecord(int section, DnsRecord record) {
        clear(section);
        setSection(section, checkQuestion(section, record));
    }

    @Override
    public <T extends DnsRecord> T setRecord(DnsSection section, int index, DnsRecord record) {
        return setRecord(sectionOrdinal(section), index, record);
    }

    private <T extends DnsRecord> T setRecord(int section, int index, DnsRecord record) {
        checkQuestion(section, record);

        final Object records = sectionAt(section);
        if (records == null) {
            throw new IndexOutOfBoundsException("index: " + index + " (expected: none)");
        }

        if (records instanceof DnsRecord) {
            if (index == 0) {
                setSection(section, record);
                return castRecord(records);
            } else {
                throw new IndexOutOfBoundsException("index: " + index + " (expected: 0)");
            }
        }

        @SuppressWarnings("unchecked")
        final List<DnsRecord> recordList = (List<DnsRecord>) records;
        return castRecord(recordList.set(index, record));
    }

    @Override
    public DnsMessage addRecord(DnsSection section, DnsRecord record) {
        addRecord(sectionOrdinal(section), record);
        return this;
    }

    private void addRecord(int section, DnsRecord record) {
        checkQuestion(section, record);

        final Object records = sectionAt(section);
        if (records == null) {
            setSection(section, record);
            return;
        }

        if (records instanceof DnsRecord) {
            final List<DnsRecord> recordList = newRecordList();
            recordList.add(castRecord(records));
            recordList.add(record);
            setSection(section, recordList);
            return;
        }

        @SuppressWarnings("unchecked")
        final List<DnsRecord> recordList = (List<DnsRecord>) records;
        recordList.add(record);
    }

    @Override
    public DnsMessage addRecord(DnsSection section, int index, DnsRecord record) {
        addRecord(sectionOrdinal(section), index, record);
        return this;
    }

    private void addRecord(int section, int index, DnsRecord record) {
        checkQuestion(section, record);

        final Object records = sectionAt(section);
        if (records == null) {
            if (index != 0) {
                throw new IndexOutOfBoundsException("index: " + index + " (expected: 0)");
            }

            setSection(section, record);
            return;
        }

        if (records instanceof DnsRecord) {
            final List<DnsRecord> recordList;
            if (index == 0) {
                recordList = newRecordList();
                recordList.add(record);
                recordList.add(castRecord(records));
            } else if (index == 1) {
                recordList = newRecordList();
                recordList.add(castRecord(records));
                recordList.add(record);
            } else {
                throw new IndexOutOfBoundsException("index: " + index + " (expected: 0 or 1)");
            }
            setSection(section, recordList);
            return;
        }

        @SuppressWarnings("unchecked")
        final List<DnsRecord> recordList = (List<DnsRecord>) records;
        recordList.add(index, record);
    }

    @Override
    public <T extends DnsRecord> T removeRecord(DnsSection section, int index) {
        return removeRecord(sectionOrdinal(section), index);
    }

    private <T extends DnsRecord> T removeRecord(int section, int index) {
        final Object records = sectionAt(section);
        if (records == null) {
            throw new IndexOutOfBoundsException("index: " + index + " (expected: none)");
        }

        if (records instanceof DnsRecord) {
            if (index != 0) {
                throw new IndexOutOfBoundsException("index: " + index + " (expected: 0)");
            }

            T record = castRecord(records);
            setSection(section, null);
            return record;
        }

        @SuppressWarnings("unchecked")
        final List<DnsRecord> recordList = (List<DnsRecord>) records;
        return castRecord(recordList.remove(index));
    }

    @Override
    public DnsMessage clear(DnsSection section) {
        clear(sectionOrdinal(section));
        return this;
    }

    @Override
    public DnsMessage clear() {
        for (int i = 0; i < SECTION_COUNT; i ++) {
            clear(i);
        }
        return this;
    }

    private void clear(int section) {
        final Object recordOrList = sectionAt(section);
        setSection(section, null);
        if (recordOrList instanceof ReferenceCounted) {
            ((ReferenceCounted) recordOrList).release();
        } else if (recordOrList instanceof List) {
            @SuppressWarnings("unchecked")
            List<DnsRecord> list = (List<DnsRecord>) recordOrList;
            if (!list.isEmpty()) {
                for (Object r : list) {
                    ReferenceCountUtil.release(r);
                }
            }
        }
    }

    @Override
    public DnsMessage touch() {
        return (DnsMessage) super.touch();
    }

    @Override
    public DnsMessage touch(Object hint) {
        if (leak != null) {
            leak.record(hint);
        }
        return this;
    }

    @Override
    public DnsMessage retain() {
        return (DnsMessage) super.retain();
    }

    @Override
    public DnsMessage retain(int increment) {
        return (DnsMessage) super.retain(increment);
    }

    @Override
    protected void deallocate() {
        clear();

        final ResourceLeakTracker<DnsMessage> leak = this.leak;
        if (leak != null) {
            boolean closed = leak.close(this);
            assert closed;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof DnsMessage)) {
            return false;
        }

        final DnsMessage that = (DnsMessage) obj;
        if (id() != that.id()) {
            return false;
        }

        if (this instanceof DnsQuery) {
            if (!(that instanceof DnsQuery)) {
                return false;
            }
        } else if (that instanceof DnsQuery) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return id() * 31 + (this instanceof DnsQuery? 0 : 1);
    }

    private Object sectionAt(int section) {
        switch (section) {
        case 0:
            return questions;
        case 1:
            return answers;
        case 2:
            return authorities;
        case 3:
            return additionals;
        }

        throw new Error(); // Should never reach here.
    }

    private void setSection(int section, Object value) {
        switch (section) {
        case 0:
            questions = value;
            return;
        case 1:
            answers = value;
            return;
        case 2:
            authorities = value;
            return;
        case 3:
            additionals = value;
            return;
        }

        throw new Error(); // Should never reach here.
    }

    private static int sectionOrdinal(DnsSection section) {
        return checkNotNull(section, "section").ordinal();
    }

    private static DnsRecord checkQuestion(int section, DnsRecord record) {
        if (section == SECTION_QUESTION && !(checkNotNull(record, "record") instanceof DnsQuestion)) {
            throw new IllegalArgumentException(
                    "record: " + record + " (expected: " + StringUtil.simpleClassName(DnsQuestion.class) + ')');
        }
        return record;
    }

    @SuppressWarnings("unchecked")
    private static <T extends DnsRecord> T castRecord(Object record) {
        return (T) record;
    }

    private static ArrayList<DnsRecord> newRecordList() {
        return new ArrayList<DnsRecord>(2);
    }
}
