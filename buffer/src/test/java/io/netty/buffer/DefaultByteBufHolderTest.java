/*
 * Copyright 2015 The Netty Project
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
package io.netty.buffer;

import org.junit.Test;

import static org.junit.Assert.*;

public class DefaultByteBufHolderTest {

    @Test
    public void testToString() {
        ByteBufHolder holder = new DefaultByteBufHolder(Unpooled.buffer());
        assertEquals(1, holder.refCnt());
        assertNotNull(holder.toString());
        assertTrue(holder.release());
        assertNotNull(holder.toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        ByteBufHolder holder = new DefaultByteBufHolder(Unpooled.EMPTY_BUFFER);
        ByteBufHolder copy = holder.copy();
        try {
            assertEquals(holder, copy);
            assertEquals(holder.hashCode(), copy.hashCode());
        } finally {
            holder.release();
            copy.release();
        }
    }

    @SuppressWarnings("SimplifiableJUnitAssertion")
    @Test
    public void testDifferentClassesAreNotEqual() {
        // all objects here have EMPTY_BUFFER data but are instances of different classes
        // so we want to check that none of them are equal to another.
        ByteBufHolder dflt = new DefaultByteBufHolder(Unpooled.EMPTY_BUFFER);
        ByteBufHolder other = new OtherByteBufHolder(Unpooled.EMPTY_BUFFER, 123);
        ByteBufHolder constant1 = new DefaultByteBufHolder(Unpooled.EMPTY_BUFFER) {
            // intentionally empty
        };
        ByteBufHolder constant2 = new DefaultByteBufHolder(Unpooled.EMPTY_BUFFER) {
            // intentionally empty
        };
        try {
            // not using 'assertNotEquals' to be explicit about which object we are calling .equals() on
            assertFalse(dflt.equals(other));
            assertFalse(dflt.equals(constant1));
            assertFalse(constant1.equals(dflt));
            assertFalse(constant1.equals(other));
            assertFalse(constant1.equals(constant2));
        } finally {
            dflt.release();
            other.release();
            constant1.release();
            constant2.release();
        }
    }

    private static class OtherByteBufHolder extends DefaultByteBufHolder {

        private final int extraField;

        OtherByteBufHolder(final ByteBuf data, final int extraField) {
            super(data);
            this.extraField = extraField;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            final OtherByteBufHolder that = (OtherByteBufHolder) o;
            return extraField == that.extraField;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + extraField;
            return result;
        }
    }
}
