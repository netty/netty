/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static io.netty.handler.codec.compression.SnappyChecksumUtil.*;
import static org.junit.Assert.*;

public class SnappyChecksumUtilTest {
    @Test
    public void testCalculateChecksum() {
        ByteBuf input = Unpooled.wrappedBuffer(new byte[] {
            'n', 'e', 't', 't', 'y'
        });
        assertEquals(maskChecksum(0xddaa8ce6), calculateChecksum(input));
    }

    @Test
    public void testValidateChecksumMatches() {
        ByteBuf input = Unpooled.wrappedBuffer(new byte[] {
            'y', 't', 't', 'e', 'n'
        });

        validateChecksum(input, maskChecksum(0x37c55159));
    }

    @Test(expected = CompressionException.class)
    public void testValidateChecksumFails() {
        ByteBuf input = Unpooled.wrappedBuffer(new byte[] {
            'y', 't', 't', 'e', 'n'
        });

        validateChecksum(input, maskChecksum(0xddaa8ce6));
    }
}
