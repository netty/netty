/*
 * Copyright 2016 The Netty Project
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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultDnsRecordEncoderTest {

    @Test
    public void testEncodeName() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io.");
    }

    @Test
    public void testEncodeNameWithoutTerminator() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io");
    }

    @Test
    public void testEncodeNameWithExtraTerminator() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io..");
    }

    // Test for https://github.com/netty/netty/issues/5014
    @Test
    public void testEncodeEmptyName() throws Exception {
        testEncodeName(new byte[] { 0 }, StringUtil.EMPTY_STRING);
    }

    @Test
    public void testEncodeRootName() throws Exception {
        testEncodeName(new byte[] { 0 }, ".");
    }

    private static void testEncodeName(byte[] expected, String name) throws Exception {
        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        ByteBuf out = Unpooled.buffer();
        ByteBuf expectedBuf = Unpooled.wrappedBuffer(expected);
        try {
            encoder.encodeName(name, out);
            assertEquals(expectedBuf, out);
        } finally {
            out.release();
            expectedBuf.release();
        }
    }
}
