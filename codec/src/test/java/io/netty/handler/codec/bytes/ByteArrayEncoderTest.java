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

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.EmptyArrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.netty.buffer.Unpooled.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class ByteArrayEncoderTest {

    private EmbeddedChannel ch;

    @BeforeEach
    public void setUp() {
        ch = new EmbeddedChannel(new ByteArrayEncoder());
    }

    @AfterEach
    public void tearDown() {
        assertThat(ch.finish(), is(false));
    }

    @Test
    public void testEncode() {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        ch.writeOutbound(b);
        ByteBuf encoded = ch.readOutbound();
        assertThat(encoded, is(wrappedBuffer(b)));
        encoded.release();
    }

    @Test
    public void testEncodeEmpty() {
        ch.writeOutbound(EmptyArrays.EMPTY_BYTES);
        assertThat((ByteBuf) ch.readOutbound(), is(sameInstance(EMPTY_BUFFER)));
    }

    @Test
    public void testEncodeOtherType() {
        String str = "Meep!";
        ch.writeOutbound(str);
        assertThat(ch.readOutbound(), is((Object) str));
    }
}
