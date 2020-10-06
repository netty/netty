/*
 * Copyright 2020 The Netty Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class HpackDynamicTableTest {

    @Test
    public void testLength() {
        HpackDynamicTable table = new HpackDynamicTable(100);
        assertEquals(0, table.length());
        HpackHeaderField entry = new HpackHeaderField("foo", "bar");
        table.add(entry);
        assertEquals(1, table.length());
        table.clear();
        assertEquals(0, table.length());
    }

    @Test
    public void testSize() {
        HpackDynamicTable table = new HpackDynamicTable(100);
        assertEquals(0, table.size());
        HpackHeaderField entry = new HpackHeaderField("foo", "bar");
        table.add(entry);
        assertEquals(entry.size(), table.size());
        table.clear();
        assertEquals(0, table.size());
    }

    @Test
    public void testGetEntry() {
        HpackDynamicTable table = new HpackDynamicTable(100);
        HpackHeaderField entry = new HpackHeaderField("foo", "bar");
        table.add(entry);
        assertEquals(entry, table.getEntry(1));
        table.clear();
        try {
            table.getEntry(1);
            fail();
        } catch (IndexOutOfBoundsException e) {
            //success
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetEntryExceptionally() {
        HpackDynamicTable table = new HpackDynamicTable(1);
        table.getEntry(1);
    }

    @Test
    public void testRemove() {
        HpackDynamicTable table = new HpackDynamicTable(100);
        assertNull(table.remove());
        HpackHeaderField entry1 = new HpackHeaderField("foo", "bar");
        HpackHeaderField entry2 = new HpackHeaderField("hello", "world");
        table.add(entry1);
        table.add(entry2);
        assertEquals(entry1, table.remove());
        assertEquals(entry2, table.getEntry(1));
        assertEquals(1, table.length());
        assertEquals(entry2.size(), table.size());
    }

    @Test
    public void testSetCapacity() {
        HpackHeaderField entry1 = new HpackHeaderField("foo", "bar");
        HpackHeaderField entry2 = new HpackHeaderField("hello", "world");
        final int size1 = entry1.size();
        final int size2 = entry2.size();
        HpackDynamicTable table = new HpackDynamicTable(size1 + size2);
        table.add(entry1);
        table.add(entry2);
        assertEquals(2, table.length());
        assertEquals(size1 + size2, table.size());
        table.setCapacity((size1 + size2) * 2); //larger capacity
        assertEquals(2, table.length());
        assertEquals(size1 + size2, table.size());
        table.setCapacity(size2); //smaller capacity
        //entry1 will be removed
        assertEquals(1, table.length());
        assertEquals(size2, table.size());
        assertEquals(entry2, table.getEntry(1));
        table.setCapacity(0); //clear all
        assertEquals(0, table.length());
        assertEquals(0, table.size());
    }

    @Test
    public void testAdd() {
        HpackDynamicTable table = new HpackDynamicTable(100);
        assertEquals(0, table.size());
        HpackHeaderField entry1 = new HpackHeaderField("foo", "bar"); //size:3+3+32=38
        HpackHeaderField entry2 = new HpackHeaderField("hello", "world");
        table.add(entry1); //success
        assertEquals(entry1.size(), table.size());
        table.setCapacity(32); //entry1 is removed from table
        assertEquals(0, table.size());
        assertEquals(0, table.length());
        table.add(entry1); //fail quietly
        assertEquals(0, table.size());
        assertEquals(0, table.length());
        table.setCapacity(64);
        table.add(entry1); //success
        assertEquals(entry1.size(), table.size());
        assertEquals(1, table.length());
        table.add(entry2); //entry2 is added, but entry1 is removed from table
        assertEquals(entry2.size(), table.size());
        assertEquals(1, table.length());
        assertEquals(entry2, table.getEntry(1));
    }
}
