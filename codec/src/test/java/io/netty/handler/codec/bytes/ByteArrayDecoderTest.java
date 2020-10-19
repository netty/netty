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
package io.netty.handler.codec.bytes;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.EmptyArrays;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static io.netty.buffer.Unpooled.*;
import static org.hamcrest.core.Is.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class ByteArrayDecoderTest {

    private EmbeddedChannel ch;

    @Before
    public void setUp() {
        ch = new EmbeddedChannel(new ByteArrayDecoder());
    }

    @Test
    public void testDecode() {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        ch.writeInbound(wrappedBuffer(b));
        assertThat((byte[]) ch.readInbound(), is(b));
    }

    @Test
    public void testDecodeEmpty() {
        ch.writeInbound(EMPTY_BUFFER);
        assertThat((byte[]) ch.readInbound(), is(EmptyArrays.EMPTY_BYTES));
    }

    @Test
    public void testDecodeOtherType() {
        String str = "Meep!";
        ch.writeInbound(str);
        assertThat(ch.readInbound(), is((Object) str));
    }
}
