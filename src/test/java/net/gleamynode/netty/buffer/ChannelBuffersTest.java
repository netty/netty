/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package net.gleamynode.netty.buffer;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ChannelBuffersTest {

    @Test
    public void testCompositeWrappedBuffer() {
        ChannelBuffer header = ChannelBuffers.dynamicBuffer(12);
        ChannelBuffer payload = ChannelBuffers.dynamicBuffer(512);

        header.writeBytes(new byte[12]);
        payload.writeBytes(new byte[512]);

        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(header, payload);

        assertTrue(header.readableBytes() == 12);
        assertTrue(payload.readableBytes() == 512);

        assertEquals(12 + 512, buffer.readableBytes());

        assertEquals(12 + 512, buffer.toByteBuffer(0, 12 + 512).remaining());
    }

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
                    ChannelBuffers.hashCode(ChannelBuffers.wrappedBuffer(e.getKey())));
        }
    }

    @Test
    public void testEquals() {
        ChannelBuffer a, b;

        // Different length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1  });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2 });
        assertFalse(ChannelBuffers.equals(a, b));

        // Same content, same firstIndex, short length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3 });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3 });
        assertTrue(ChannelBuffers.equals(a, b));

        // Same content, different firstIndex, short length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3 });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 1, 3);
        assertTrue(ChannelBuffers.equals(a, b));

        // Different content, same firstIndex, short length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3 });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 4 });
        assertFalse(ChannelBuffers.equals(a, b));

        // Different content, different firstIndex, short length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3 });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 1, 3);
        assertFalse(ChannelBuffers.equals(a, b));

        // Same content, same firstIndex, long length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        assertTrue(ChannelBuffers.equals(a, b));

        // Same content, different firstIndex, long length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 10);
        assertTrue(ChannelBuffers.equals(a, b));

        // Different content, same firstIndex, long length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 6, 7, 8, 5, 9, 10 });
        assertFalse(ChannelBuffers.equals(a, b));

        // Different content, different firstIndex, long length.
        a = ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        b = ChannelBuffers.wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 10);
        assertFalse(ChannelBuffers.equals(a, b));
    }

    @Test
    public void testCompare() {
        List<ChannelBuffer> expected = new ArrayList<ChannelBuffer>();
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 1 }));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 1, 2 }));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8,  9, 10 }));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8,  9, 10, 11, 12 }));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 2 }));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 2, 3 }));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 }));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 }));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 2, 3, 4 }, 1, 1));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4 }, 2, 2));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 }, 1, 10));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 }, 2, 12));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 2, 3, 4, 5 }, 2, 1));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5 }, 3, 2));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 }, 2, 10));
        expected.add(ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 }, 3, 12));

        for (int i = 0; i < expected.size(); i ++) {
            for (int j = 0; j < expected.size(); j ++) {
                if (i == j) {
                    assertEquals(0, ChannelBuffers.compare(expected.get(i), expected.get(j)));
                } else if (i < j) {
                    assertTrue(ChannelBuffers.compare(expected.get(i), expected.get(j)) < 0);
                } else {
                    assertTrue(ChannelBuffers.compare(expected.get(i), expected.get(j)) > 0);
                }
            }
        }
    }
}
