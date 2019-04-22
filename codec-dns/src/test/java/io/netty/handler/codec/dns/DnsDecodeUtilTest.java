/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.util.DnsDecodeUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class DnsDecodeUtilTest {
    @Test
    public void testDecodeDomainName() {
        testDecodeDomainName0("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0
        }));
    }

    @Test
    public void testDecodeDomainNameWithoutTerminator() {
        testDecodeDomainName0("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o'
        }));
    }

    @Test
    public void testDecodeDomainNameWithExtraTerminator() {
        // Should not be decoded as 'netty.io..'
        testDecodeDomainName0("netty.io.", Unpooled.wrappedBuffer(new byte[] {
                5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, 0
        }));
    }

    @Test
    public void testDecodeEmptyDomainName() {
        testDecodeDomainName0(".", Unpooled.buffer().writeByte(0));
    }

    @Test
    public void testDecodeEmptyDomainNameFromEmptyBuffer() {
        testDecodeDomainName0(".", Unpooled.EMPTY_BUFFER);
    }

    @Test
    public void testDecodeEmptyDomainNameFromExtraZeroes() {
        testDecodeDomainName0(".", Unpooled.wrappedBuffer(new byte[] { 0, 0 }));
    }

    @Test
    public void testDecodeCompressedDomainName() {
        byte[] compressedDomainName = {
                1, 'F', 3, 'I', 'S', 'I', 4, 'A', 'R', 'P', 'A', 0,
                3, 'F', 'O', 'O',
                (byte) 0xC0, 0, // this is 20 in the example
                (byte) 0xC0, 6, // this is 26 in the example
        };
        ByteBuf raw = Unpooled.wrappedBuffer(compressedDomainName);
        testDecodeDomainName0("F.ISI.ARPA.", raw.retainedDuplicate());
        testDecodeDomainName0("FOO.F.ISI.ARPA.", raw.retainedDuplicate().setIndex(12, raw.writerIndex()));
        raw.release();
    }

    private static void testDecodeDomainName0(String expected, ByteBuf buffer) {
        try {
            assertEquals(expected, DnsDecodeUtil.decodeDomainName(buffer));
        } finally {
            buffer.release();
        }
    }
}
