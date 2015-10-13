/*
 * Copyright 2014 The Netty Project
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
package io.netty.buffer;

import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;

public class ByteBufUtilTest {

    @Test
    public void testWriteUsAscii() {
        String usAscii = "NettyRocks";
        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(16));
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = ReferenceCountUtil.releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeAscii(buf2, usAscii);

        Assert.assertEquals(buf, buf2);
    }

    @Test
    public void testWriteUtf8() {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(16));
        buf.writeBytes(usAscii.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = ReferenceCountUtil.releaseLater(Unpooled.buffer(16));
        ByteBufUtil.writeUtf8(buf2, usAscii);

        Assert.assertEquals(buf, buf2);
    }

    @Test
    public void testDecodeUsAscii() {
        testDecodeString("This is a test", CharsetUtil.US_ASCII);
    }

    @Test
    public void testDecodeUtf8() {
        testDecodeString("Some UTF-8 like äÄ∏ŒŒ", CharsetUtil.UTF_8);
    }

    private static void testDecodeString(String text, Charset charset) {
        ByteBuf buffer = Unpooled.copiedBuffer(text, charset);
        Assert.assertEquals(text, ByteBufUtil.decodeString(buffer, 0, buffer.readableBytes(), charset));
        buffer.release();
    }
}
