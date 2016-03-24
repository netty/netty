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
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultDnsRecordEncoderTest {

    // Test for https://github.com/netty/netty/issues/5014
    @Test
    public void testEncodeEmptyName() throws Exception {
        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        ByteBuf out = Unpooled.buffer();
        try {
            encoder.encodeName(StringUtil.EMPTY_STRING, out);
            assertEquals(2, out.readableBytes());
            assertEquals(0, out.readByte());
            assertEquals(0, out.readByte());
        } finally {
            out.release();
        }
    }
}
