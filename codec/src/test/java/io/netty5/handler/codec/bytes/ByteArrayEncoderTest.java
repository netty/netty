/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.bytes;

import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.internal.EmptyArrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ByteArrayEncoderTest {

    private EmbeddedChannel ch;

    @BeforeEach
    public void setUp() {
        ch = new EmbeddedChannel(new ByteArrayEncoder());
    }

    @AfterEach
    public void tearDown() {
        assertFalse(ch.finish());
    }

    @Test
    public void testEncode() {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        ch.writeOutbound(b);
        try (Buffer encoded = ch.readOutbound();
             Buffer expected = preferredAllocator().copyOf(b)) {
            assertThat(encoded).isEqualTo(expected);
        }
    }

    @Test
    public void testEncodeEmpty() {
        ch.writeOutbound(EmptyArrays.EMPTY_BYTES);
        try (Buffer buf = ch.readOutbound()) {
            assertThat(buf.readableBytes()).isZero();
        }
    }

    @Test
    public void testEncodeOtherType() {
        String str = "Meep!";
        ch.writeOutbound(str);
        assertThat((String) ch.readOutbound()).isEqualTo(str);
    }
}
