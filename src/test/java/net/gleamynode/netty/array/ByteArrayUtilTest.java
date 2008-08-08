/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.array;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ByteArrayUtilTest {
    @Test
    public void testHashCode() {
        Map<byte[], Integer> map = new LinkedHashMap<byte[], Integer>();
        map.put(new byte[0], 1);
        map.put(new byte[] { 1 }, 32);
        map.put(new byte[] { 2 }, 33);
        map.put(new byte[] { 0, 1 }, 962);
        map.put(new byte[] { 1, 2 }, 994);
        map.put(new byte[] { 0, 1, 2, 3, 4, 5 }, 63504931);
        map.put(new byte[] { 6, 7, 8, 9, 0, 1 }, (int) 97180294697L);
        map.put(new byte[] { -1, -1, -1, (byte) 0xE1 }, 1);

        for (Entry<byte[], Integer> e: map.entrySet()) {
            assertEquals(
                    e.getValue().intValue(),
                    ByteArrayUtil.hashCode(new HeapByteArray(e.getKey())));
        }
    }

    @Test
    public void testEquals() {
        ByteArray a, b;

        // Different length.
        a = new HeapByteArray(new byte[] { 1  });
        b = new HeapByteArray(new byte[] { 1, 2 });
        assertFalse(ByteArrayUtil.equals(a, b));

        // Same content, same firstIndex, short length.
        a = new HeapByteArray(new byte[] { 1, 2, 3 });
        b = new HeapByteArray(new byte[] { 1, 2, 3 });
        assertTrue(ByteArrayUtil.equals(a, b));

        // Same content, different firstIndex, short length.
        a = new HeapByteArray(new byte[] { 1, 2, 3 });
        b = new StaticPartialByteArray(new byte[] { 0, 1, 2, 3, 4 }, 1, 3);
        assertTrue(ByteArrayUtil.equals(a, b));

        // Different content, same firstIndex, short length.
        a = new HeapByteArray(new byte[] { 1, 2, 3 });
        b = new HeapByteArray(new byte[] { 1, 2, 4 });
        assertFalse(ByteArrayUtil.equals(a, b));

        // Different content, different firstIndex, short length.
        a = new HeapByteArray(new byte[] { 1, 2, 3 });
        b = new StaticPartialByteArray(new byte[] { 0, 1, 2, 4, 5 }, 1, 3);
        assertFalse(ByteArrayUtil.equals(a, b));

        // Same content, same firstIndex, long length.
        a = new HeapByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = new HeapByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        assertTrue(ByteArrayUtil.equals(a, b));

        // Same content, different firstIndex, long length.
        a = new HeapByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = new StaticPartialByteArray(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 10);
        assertTrue(ByteArrayUtil.equals(a, b));

        // Different content, same firstIndex, long length.
        a = new HeapByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = new HeapByteArray(new byte[] { 1, 2, 3, 4, 6, 7, 8, 5, 9, 10 });
        assertFalse(ByteArrayUtil.equals(a, b));

        // Different content, different firstIndex, long length.
        a = new HeapByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = new StaticPartialByteArray(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 10);
        assertFalse(ByteArrayUtil.equals(a, b));
    }

    @Test
    public void testCompare() {
        List<ByteArray> expected = new ArrayList<ByteArray>();
        expected.add(new HeapByteArray(new byte[] { 1 }));
        expected.add(new HeapByteArray(new byte[] { 1, 2 }));
        expected.add(new HeapByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8,  9, 10 }));
        expected.add(new HeapByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8,  9, 10, 11, 12 }));
        expected.add(new HeapByteArray(new byte[] { 2 }));
        expected.add(new HeapByteArray(new byte[] { 2, 3 }));
        expected.add(new HeapByteArray(new byte[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 }));
        expected.add(new HeapByteArray(new byte[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 }));
        expected.add(new StaticPartialByteArray(new byte[] { 2, 3, 4 }, 1, 1));
        expected.add(new StaticPartialByteArray(new byte[] { 1, 2, 3, 4 }, 2, 2));
        expected.add(new StaticPartialByteArray(new byte[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 }, 1, 10));
        expected.add(new StaticPartialByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 }, 2, 12));
        expected.add(new StaticPartialByteArray(new byte[] { 2, 3, 4, 5 }, 2, 1));
        expected.add(new StaticPartialByteArray(new byte[] { 1, 2, 3, 4, 5 }, 3, 2));
        expected.add(new StaticPartialByteArray(new byte[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 }, 2, 10));
        expected.add(new StaticPartialByteArray(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 }, 3, 12));

        for (int i = 0; i < expected.size(); i ++) {
            for (int j = 0; j < expected.size(); j ++) {
                if (i == j) {
                    assertEquals(0, ByteArrayUtil.compare(expected.get(i), expected.get(j)));
                } else if (i < j) {
                    assertTrue(ByteArrayUtil.compare(expected.get(i), expected.get(j)) < 0);
                } else {
                    assertTrue(ByteArrayUtil.compare(expected.get(i), expected.get(j)) > 0);
                }
            }
        }
    }
}
