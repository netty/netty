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
package io.netty.handler.codec.serialization;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompatibleObjectEncoderTest {
    @Test
    public void testMultipleEncodeReferenceCount() throws IOException, ClassNotFoundException {
        EmbeddedChannel channel = new EmbeddedChannel(new CompatibleObjectEncoder());
        testEncode(channel, new TestSerializable(6, 8));
        testEncode(channel, new TestSerializable(10, 5));
        testEncode(channel, new TestSerializable(1, 5));
        assertFalse(channel.finishAndReleaseAll());
    }

    private static void testEncode(EmbeddedChannel channel, TestSerializable original)
            throws IOException, ClassNotFoundException {
        channel.writeOutbound(original);
        Object o = channel.readOutbound();
        ByteBuf buf = (ByteBuf) o;
        ObjectInputStream ois = new ObjectInputStream(new ByteBufInputStream(buf));
        try {
            assertEquals(original, ois.readObject());
        } finally {
            buf.release();
            ois.close();
        }
    }

    private static final class TestSerializable implements Serializable {
        private static final long serialVersionUID = 2235771472534930360L;

        public final int x;
        public final int y;

        TestSerializable(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TestSerializable)) {
                return false;
            }
            TestSerializable rhs = (TestSerializable) o;
            return x == rhs.x && y == rhs.y;
        }

        @Override
        public int hashCode() {
            return 31 * (31 + x) + y;
        }
    }
}
