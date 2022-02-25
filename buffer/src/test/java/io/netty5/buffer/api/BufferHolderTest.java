/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.api;

import org.junit.jupiter.api.Test;

import static io.netty5.buffer.api.BufferAllocator.offHeapUnpooled;
import static io.netty5.buffer.api.BufferAllocator.onHeapUnpooled;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BufferHolderTest {

    @Test
    public void testEqualsAndHashCode() {
        byte[] bytes = "data".getBytes(UTF_8);
        try (BufferRef first = new BufferRef(onHeapUnpooled().copyOf(bytes).send());
             BufferRef secnd = new BufferRef(offHeapUnpooled().copyOf(bytes).send())) {
            assertEquals(first, secnd);
            assertEquals(first.hashCode(), secnd.hashCode());
        }
    }

    @SuppressWarnings({ "SimplifiableJUnitAssertion", "rawtypes", "EqualsBetweenInconvertibleTypes" })
    @Test
    public void testDifferentClassesAreNotEqual() {
        // all objects here have EMPTY_BUFFER data but are instances of different classes
        // so we want to check that none of them are equal to another.
        BufferRef dflt = new BufferRef(onHeapUnpooled().allocate(0).send());
        BufferRef2 other = new BufferRef2(onHeapUnpooled().allocate(0));
        BufferHolder<?> constant1 = new BufferHolder(onHeapUnpooled().allocate(0)) {
            @Override
            protected BufferHolder receive(Buffer buf) {
                return null;
            }
        };
        BufferHolder<?> constant2 = new BufferHolder(onHeapUnpooled().allocate(0)) {
            @Override
            protected BufferHolder receive(Buffer buf) {
                return null;
            }
        };
        try {
            // not using 'assertNotEquals' to be explicit about which object we are calling .equals() on
            assertFalse(dflt.equals(other));
            assertFalse(dflt.equals(constant1));
            assertFalse(constant1.equals(dflt));
            assertFalse(constant1.equals(other));
            assertFalse(constant1.equals(constant2));
        } finally {
            dflt.close();
            other.close();
            constant1.close();
            constant2.close();
        }
    }

    private static final class BufferRef2 extends BufferHolder<BufferRef2> {
        BufferRef2(Buffer buf) {
            super(buf);
        }

        @Override
        protected BufferRef2 receive(Buffer buf) {
            return new BufferRef2(buf);
        }
    }
}
