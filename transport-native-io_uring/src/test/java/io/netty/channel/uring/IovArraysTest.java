/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.unix.IovArray;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class IovArraysTest {

    @Test
    public void test() {
        IOUring.ensureAvailability();
        ByteBuf buf = Unpooled.directBuffer(1).writeZero(1);
        IovArrays arrays = new IovArrays(2);
        try {
            IovArray next = arrays.next();
            assertNotNull(next);
            assertSame(next, arrays.next());

            while (next.add(buf, 0, buf.readableBytes())) {
                // loop until we filled it.
            }

            IovArray next2 = arrays.next();
            assertNotSame(next, next2);

            while (next2.add(buf, 0, buf.readableBytes())) {
                // loop until we filled it.
            }

            assertNull(arrays.next());

            arrays.clear();

            // We should start again from idx 0
            assertSame(next, arrays.next());
        } finally {
            arrays.release();
            buf.release();
        }
    }
}
