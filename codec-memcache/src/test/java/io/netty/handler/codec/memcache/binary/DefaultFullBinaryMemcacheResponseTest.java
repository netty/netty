/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.memcache.binary;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class DefaultFullBinaryMemcacheResponseTest {

    private DefaultFullBinaryMemcacheResponse response;

    @Before
    public void setUp() {
        response = new DefaultFullBinaryMemcacheResponse(
                Unpooled.copiedBuffer("key", CharsetUtil.UTF_8),
                Unpooled.wrappedBuffer(new byte[]{1, 3, 4, 9}),
                Unpooled.copiedBuffer("some value", CharsetUtil.UTF_8));
        response.setStatus((short) 1);
        response.setMagic((byte) 0x03);
        response.setOpcode((byte) 0x02);
        response.setKeyLength((short) 32);
        response.setExtrasLength((byte) 34);
        response.setDataType((byte) 43);
        response.setTotalBodyLength(345);
        response.setOpaque(3);
        response.setCas(345345L);
    }

    @Test
    public void fullCopy() {
        FullBinaryMemcacheResponse newInstance = response.copy();
        try {
            assertResponseEquals(response, response.content(), newInstance);
        } finally {
            response.release();
            newInstance.release();
        }
    }

    @Test
    public void fullDuplicate() {
        try {
            assertResponseEquals(response, response.content(), response.duplicate());
        } finally {
            response.release();
        }
    }

    @Test
    public void fullReplace() {
        ByteBuf newContent = Unpooled.copiedBuffer("new value", CharsetUtil.UTF_8);
        FullBinaryMemcacheResponse newInstance = response.replace(newContent);
        try {
            assertResponseEquals(response, newContent, newInstance);
        } finally {
            response.release();
            newInstance.release();
        }
    }

    private void assertResponseEquals(FullBinaryMemcacheResponse expected, ByteBuf expectedContent,
                                      FullBinaryMemcacheResponse actual) {
        assertNotSame(expected, actual);

        assertEquals(expected.key(), actual.key());
        assertEquals(expected.extras(), actual.extras());
        assertEquals(expectedContent, actual.content());

        assertEquals(expected.status(), actual.status());
        assertEquals(expected.magic(), actual.magic());
        assertEquals(expected.opcode(), actual.opcode());
        assertEquals(expected.keyLength(), actual.keyLength());
        assertEquals(expected.extrasLength(), actual.extrasLength());
        assertEquals(expected.dataType(), actual.dataType());
        assertEquals(expected.totalBodyLength(), actual.totalBodyLength());
        assertEquals(expected.opaque(), actual.opaque());
        assertEquals(expected.cas(), actual.cas());
    }
}
