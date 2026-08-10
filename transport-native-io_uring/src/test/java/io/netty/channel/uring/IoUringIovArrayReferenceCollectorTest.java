/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.unix.IovArray;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringIovArrayReferenceCollectorTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void capturesOnlyBuffersAddedToIovArray() throws Exception {
        IovArray iovArray = new IovArray(2);
        ByteBuf first = Unpooled.directBuffer().writeZero(1);
        ByteBuf second = Unpooled.directBuffer().writeZero(1);
        ByteBuf third = Unpooled.directBuffer().writeZero(1);
        try {
            iovArray.maxCount(2);
            AbstractIoUringStreamChannel.IovArrayReferenceCollector collector =
                    new AbstractIoUringStreamChannel.IovArrayReferenceCollector();
            collector.reset(iovArray);

            assertTrue(collector.processMessage(first));
            assertTrue(collector.processMessage(second));
            assertFalse(collector.processMessage(third));

            assertEquals(2, collector.referencesCount());
            ReferenceCounted[] references = collector.referencesArray();
            assertSame(first, references[0]);
            assertSame(second, references[1]);
        } finally {
            first.release();
            second.release();
            third.release();
            iovArray.release();
        }
    }

    @Test
    public void capturesPartiallyAddedCompositeBuffer() throws Exception {
        IovArray iovArray = new IovArray(1);
        CompositeByteBuf buffer = Unpooled.compositeBuffer(2);
        buffer.addComponents(true, Unpooled.directBuffer().writeZero(1), Unpooled.directBuffer().writeZero(1));
        try {
            AbstractIoUringStreamChannel.IovArrayReferenceCollector collector =
                    new AbstractIoUringStreamChannel.IovArrayReferenceCollector();
            collector.reset(iovArray);

            assertFalse(collector.processMessage(buffer));

            assertEquals(1, collector.referencesCount());
            assertSame(buffer, collector.referencesArray()[0]);
        } finally {
            buffer.release();
            iovArray.release();
        }
    }

    @Test
    public void capturesFirstBufferWhenItExceedsMaxBytes() throws Exception {
        IovArray iovArray = new IovArray(2);
        ByteBuf first = Unpooled.directBuffer().writeZero(2);
        ByteBuf second = Unpooled.directBuffer().writeZero(1);
        try {
            iovArray.maxBytes(1);
            AbstractIoUringStreamChannel.IovArrayReferenceCollector collector =
                    new AbstractIoUringStreamChannel.IovArrayReferenceCollector();
            collector.reset(iovArray);

            assertTrue(collector.processMessage(first));
            assertFalse(collector.processMessage(second));

            assertEquals(1, collector.referencesCount());
            assertSame(first, collector.referencesArray()[0]);
        } finally {
            first.release();
            second.release();
            iovArray.release();
        }
    }

    @Test
    public void reusingCollectorDropsPreviousWritesReferences() throws Exception {
        IovArray firstIovArray = new IovArray(3);
        IovArray secondIovArray = new IovArray(1);
        ByteBuf first = Unpooled.directBuffer().writeZero(1);
        ByteBuf second = Unpooled.directBuffer().writeZero(1);
        ByteBuf third = Unpooled.directBuffer().writeZero(1);
        ByteBuf fourth = Unpooled.directBuffer().writeZero(1);
        try {
            AbstractIoUringStreamChannel.IovArrayReferenceCollector collector =
                    new AbstractIoUringStreamChannel.IovArrayReferenceCollector();

            collector.reset(firstIovArray);
            assertTrue(collector.processMessage(first));
            assertTrue(collector.processMessage(second));
            assertTrue(collector.processMessage(third));
            assertEquals(3, collector.referencesCount());

            collector.reset(secondIovArray);
            assertTrue(collector.processMessage(fourth));

            assertEquals(1, collector.referencesCount());
            assertSame(fourth, collector.referencesArray()[0]);
        } finally {
            first.release();
            second.release();
            third.release();
            fourth.release();
            firstIovArray.release();
            secondIovArray.release();
        }
    }
}
