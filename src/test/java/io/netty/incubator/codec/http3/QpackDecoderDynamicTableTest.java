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

import org.junit.Test;

import static org.junit.Assert.*;

public class QpackDecoderDynamicTableTest {

    private final QpackHeaderField fooBar = new QpackHeaderField("foo", "bar");

    @Test
    public void length() throws Exception {
        QpackDecoderDynamicTable table = newTable(100);
        assertEquals(0, table.length());
        table.add(fooBar);
        assertEquals(1, table.length());
        table.clear();
        assertEquals(0, table.length());
    }

    @Test
    public void size() throws Exception {
        QpackDecoderDynamicTable table = newTable(100);
        assertEquals(0, table.size());
        QpackHeaderField entry = new QpackHeaderField("foo", "bar");
        table.add(entry);
        assertEquals(entry.size(), table.size());
        table.clear();
        assertEquals(0, table.size());
    }

    @Test
    public void getEntry() throws Exception {
        QpackDecoderDynamicTable table = newTable(100);
        QpackHeaderField entry = new QpackHeaderField("foo", "bar");
        table.add(entry);
        assertEquals(entry, table.getEntry(0));
        table.clear();
        try {
            table.getEntry(0);
            fail();
        } catch (QpackException e) {
            //success
        }
    }

    @Test(expected = QpackException.class)
    public void getEntryExceptionally() throws Exception {
        QpackDecoderDynamicTable table = newTable(1);
        table.getEntry(0);
    }

    @Test
    public void setCapacity() throws Exception {
        QpackHeaderField entry1 = new QpackHeaderField("foo", "bar");
        QpackHeaderField entry2 = new QpackHeaderField("hello", "world");
        final long size1 = entry1.size();
        final long size2 = entry2.size();
        QpackDecoderDynamicTable table = newTable(size1 + size2);
        table.add(entry1);
        table.add(entry2);
        assertEquals(2, table.length());
        assertEquals(size1 + size2, table.size());
        assertEquals(entry1, table.getEntry(0));
        assertEquals(entry2, table.getEntry(1));

        table.setCapacity((size1 + size2) * 2); //larger capacity
        assertEquals(2, table.length());
        assertEquals(size1 + size2, table.size());

        table.setCapacity(size2); //smaller capacity
        //entry1 will be removed
        assertEquals(1, table.length());
        assertEquals(size2, table.size());
        assertEquals(entry2, table.getEntry(0));
        table.setCapacity(0); //clear all
        assertEquals(0, table.length());
        assertEquals(0, table.size());
    }

    @Test
    public void add() throws Exception {
        QpackDecoderDynamicTable table = newTable(100);
        assertEquals(0, table.size());
        QpackHeaderField entry1 = new QpackHeaderField("foo", "bar"); //size:3+3+32=38
        QpackHeaderField entry2 = new QpackHeaderField("hello", "world");
        table.add(entry1); //success
        assertEquals(entry1.size(), table.size());
        assertEquals(entry1, table.getEntry(0));
        table.setCapacity(32); //entry1 is removed from table
        assertEquals(0, table.size());
        assertEquals(0, table.length());

        table.setCapacity(64);
        table.add(entry1); //success
        assertEquals(entry1.size(), table.size());
        assertEquals(1, table.length());
        assertEquals(entry1, table.getEntry(0));
        table.add(entry2); //entry2 is added, but entry1 is removed from table
        assertEquals(entry2.size(), table.size());
        assertEquals(1, table.length());
        assertEquals(entry2, table.getEntry(1));

        table.setCapacity(128);
        table.add(entry1); //success
        assertEquals(entry2, table.getEntry(0));
    }

    private static QpackDecoderDynamicTable newTable(long capacity) throws QpackException {
        QpackDecoderDynamicTable table = new QpackDecoderDynamicTable();
        table.setCapacity(capacity);
        return table;
    }
}
