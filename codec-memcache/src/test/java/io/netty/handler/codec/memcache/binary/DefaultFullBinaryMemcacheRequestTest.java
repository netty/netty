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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class DefaultFullBinaryMemcacheRequestTest {

    private DefaultFullBinaryMemcacheRequest request;

    @BeforeEach
    public void setUp() {
        request = new DefaultFullBinaryMemcacheRequest(
                Unpooled.copiedBuffer("key", CharsetUtil.UTF_8),
                Unpooled.wrappedBuffer(new byte[]{1, 3, 4, 9}),
                Unpooled.copiedBuffer("some value", CharsetUtil.UTF_8));
        request.setReserved((short) 534);
        request.setMagic((byte) 0x03);
        request.setOpcode((byte) 0x02);
        request.setKeyLength((short) 32);
        request.setExtrasLength((byte) 34);
        request.setDataType((byte) 43);
        request.setTotalBodyLength(345);
        request.setOpaque(3);
        request.setCas(345345L);
    }

    @Test
    public void fullCopy() {
        FullBinaryMemcacheRequest newInstance = request.copy();
        try {
            assertCopy(request, request.content(), newInstance);
        } finally {
            request.release();
            newInstance.release();
        }
    }

    @Test
    public void fullDuplicate() {
        FullBinaryMemcacheRequest newInstance = request.duplicate();
        try {
            assertCopy(request, request.content(), newInstance);
        } finally {
            request.release();
        }
    }

    @Test
    public void fullReplace() {
        ByteBuf newContent = Unpooled.copiedBuffer("new value", CharsetUtil.UTF_8);
        FullBinaryMemcacheRequest newInstance = request.replace(newContent);
        try {
            assertCopy(request, newContent, newInstance);
        } finally {
            request.release();
            newInstance.release();
        }
    }

    private void assertCopy(FullBinaryMemcacheRequest expected, ByteBuf expectedContent,
                            FullBinaryMemcacheRequest actual) {
        assertNotSame(expected, actual);

        assertEquals(expected.key(), actual.key());
        assertEquals(expected.extras(), actual.extras());
        assertEquals(expectedContent, actual.content());

        assertEquals(expected.reserved(), actual.reserved());
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
