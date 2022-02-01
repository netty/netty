/*
 * Copyright 2020 The Netty Project
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


public class QpackStaticTableTest {
    @Test
    public void testFieldNotFound() {
        assertEquals(QpackStaticTable.NOT_FOUND, QpackStaticTable.findFieldIndex("x-netty-quic", "incubating"));
    }

    @Test
    public void testFieldNameAndValueMatch() {
        // first in range
        assertEquals(15, QpackStaticTable.findFieldIndex(":method", "CONNECT"));
        // last in range
        assertEquals(21, QpackStaticTable.findFieldIndex(":method", "PUT"));
        // non-consequent range
        assertEquals(24, QpackStaticTable.findFieldIndex(":status", "103"));
        assertEquals(69, QpackStaticTable.findFieldIndex(":status", "421"));
    }

    @Test
    public void testFieldNameRefForEmptyField() {
        int nameIndex1 = QpackStaticTable.findFieldIndex("cookie", "netty.io");
        int nameIndex2 = QpackStaticTable.findFieldIndex("cookie", "quic.io");

        // should give the same name ref for any values
        assertNotEquals(QpackStaticTable.NOT_FOUND, nameIndex1);
        assertNotEquals(QpackStaticTable.NOT_FOUND, nameIndex2);
        assertEquals(nameIndex1, nameIndex2);

        // index should be masked
        assertEquals(nameIndex1 & QpackStaticTable.MASK_NAME_REF, QpackStaticTable.MASK_NAME_REF);
        assertEquals(5, nameIndex1 ^ QpackStaticTable.MASK_NAME_REF);
    }

    @Test
    public void testFieldNameRefForSingleMatch() {
        // note the value differs from static table ("1" rather than "0")
        int nameIndex = QpackStaticTable.findFieldIndex("age", "1");
        assertEquals(2, nameIndex ^ QpackStaticTable.MASK_NAME_REF);
    }

    @Test
    public void testFieldNameRefForMultipleMatches() {
        int nameIndex = QpackStaticTable.findFieldIndex(":method", "ALLTHETHINGS");
        assertEquals(15, nameIndex ^ QpackStaticTable.MASK_NAME_REF);
    }
}
