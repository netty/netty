/*
 * Copyright 2013 The Netty Project
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

package io.netty5.channel;

import io.netty5.buffer.BufferInputStream;
import io.netty5.buffer.BufferOutputStream;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
public class DefaultChannelIdTest {
    @Test
    public void testShortText() {
        String text = DefaultChannelId.newInstance().asShortText();
        assertTrue(text.matches("^[0-9a-f]{8}$"));
    }

    @Test
    public void testLongText() {
        String text = DefaultChannelId.newInstance().asLongText();
        assertTrue(text.matches("^[0-9a-f]{16}-[0-9a-f]{8}-[0-9a-f]{8}-[0-9a-f]{16}-[0-9a-f]{8}$"));
    }

    @Test
    public void testIdempotentMachineId() {
        String a = DefaultChannelId.newInstance().asLongText().substring(0, 16);
        String b = DefaultChannelId.newInstance().asLongText().substring(0, 16);
        assertThat(a).isEqualTo(b);
    }

    @Test
    public void testIdempotentProcessId() {
        String a = DefaultChannelId.newInstance().asLongText().substring(17, 21);
        String b = DefaultChannelId.newInstance().asLongText().substring(17, 21);
        assertThat(a).isEqualTo(b);
    }

    @Test
    public void testSerialization() throws Exception {
        ChannelId a = DefaultChannelId.newInstance();
        ChannelId b;

        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(256);
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferOutputStream(buf))) {
            out.writeObject(a);
            out.flush();
        }

        try (ObjectInputStream in = new ObjectInputStream(new BufferInputStream(buf.send()))) {
            b = (ChannelId) in.readObject();
        }

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotSameAs(b);
        assertThat(a.asLongText()).isEqualTo(b.asLongText());
    }

    @Test
    public void testDeserialization() {
        // DefaultChannelId with 8 byte machineId
        final DefaultChannelId c8 = new DefaultChannelId(
                new byte[] {
                        (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
                        (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef
                },
                0x000052af,
                0x00000000,
                0x06504f638eb4c386L,
                0xd964df5e);

        // DefaultChannelId with 6 byte machineId
        final DefaultChannelId c6 =
                new DefaultChannelId(
                        new byte[] {
                                (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
                                (byte) 0x89, (byte) 0xab,
                        },
                        0xce005283,
                        0x00000001,
                        0x069e6dce9eb4516fL,
                        0x721757b7);

        assertThat(c8.asLongText()).isEqualTo("0123456789abcdef-000052af-00000000-06504f638eb4c386-d964df5e");
        assertThat(c6.asLongText()).isEqualTo("0123456789ab-ce005283-00000001-069e6dce9eb4516f-721757b7");
    }

}
