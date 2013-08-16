/*
 * Copyright 2013 The Netty Project
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

import org.junit.Assert;
import org.junit.Test;

public class UnreleaseableByteBufTest {

    @Test
    public void testCantRelease() {
        ByteBuf buf = Unpooled.unreleasableBuffer(Unpooled.copyInt(1));
        Assert.assertEquals(1, buf.refCnt());
        Assert.assertFalse(buf.release());
        Assert.assertEquals(1, buf.refCnt());
        Assert.assertFalse(buf.release());
        Assert.assertEquals(1, buf.refCnt());

        buf.retain(5);
        Assert.assertEquals(1, buf.refCnt());

        buf.retain();
        Assert.assertEquals(1, buf.refCnt());

        Assert.assertTrue(buf.unwrap().release());
        Assert.assertEquals(0, buf.refCnt());
    }
}
