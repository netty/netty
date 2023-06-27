/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HpackStaticTableTest {

    @Test
    public void testEmptyHeaderName() {
        assertEquals(-1, HpackStaticTable.getIndex(""));
    }

    @Test
    public void testMissingHeaderName() {
        assertEquals(-1, HpackStaticTable.getIndex("missing"));
    }

    @Test
    public void testExistingHeaderName() {
        assertEquals(6, HpackStaticTable.getIndex(":scheme"));
    }

    @Test
    public void testMissingHeaderNameAndValue() {
        assertEquals(-1, HpackStaticTable.getIndexInsensitive("missing", "value"));
    }

    @Test
    public void testMissingHeaderNameButValueExists() {
        assertEquals(-1, HpackStaticTable.getIndexInsensitive("missing", "https"));
    }

    @Test
    public void testExistingHeaderNameAndValueFirstMatch() {
        assertEquals(6, HpackStaticTable.getIndexInsensitive(":scheme", "http"));
    }

    @Test
    public void testExistingHeaderNameAndValueSecondMatch() {
        assertEquals(7, HpackStaticTable.getIndexInsensitive(
          AsciiString.cached(":scheme"), AsciiString.cached("https")));
    }

    @Test
    public void testExistingHeaderNameAndEmptyValueMismatch() {
        assertEquals(-1, HpackStaticTable.getIndexInsensitive(":scheme", ""));
    }

    @Test
    public void testExistingHeaderNameAndEmptyValueMatch() {
        assertEquals(27, HpackStaticTable.getIndexInsensitive("content-language", ""));
    }

    @Test
    public void testExistingHeaderNameButMissingValue() {
        assertEquals(-1, HpackStaticTable.getIndexInsensitive(":scheme", "missing"));
    }

}
