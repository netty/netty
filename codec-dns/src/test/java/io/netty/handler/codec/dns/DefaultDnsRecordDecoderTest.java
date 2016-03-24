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
import org.junit.Assert;
import org.junit.Test;

public class DefaultDnsRecordDecoderTest {

    @Test
    public void testDecodeEmptyName() {
        testDecodeEmptyName0(Unpooled.buffer().writeByte('0'));
    }

    @Test
    public void testDecodeEmptyNameNonRFC() {
        testDecodeEmptyName0(Unpooled.EMPTY_BUFFER);
    }

    private static void testDecodeEmptyName0(ByteBuf buffer) {
        try {
            DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
            Assert.assertEquals(StringUtil.EMPTY_STRING, decoder.decodeName(buffer));
        } finally {
            buffer.release();
        }
    }
}
