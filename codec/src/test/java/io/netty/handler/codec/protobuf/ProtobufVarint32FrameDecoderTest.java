/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.protobuf;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import static io.netty.buffer.Unpooled.*;
import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.hamcrest.core.Is.*;
import static org.hamcrest.core.IsNull.*;
import static org.junit.Assert.*;

public class ProtobufVarint32FrameDecoderTest {

    private EmbeddedChannel ch;

    @Before
    public void setUp() {
        ch = new EmbeddedChannel(new ProtobufVarint32FrameDecoder());
    }

    @Test
    public void testTinyDecode() {
        byte[] b = { 4, 1, 1, 1, 1 };
        ch.writeInbound(wrappedBuffer(b, 0, 1));
        assertThat(ch.readInbound(), is(nullValue()));
        ch.writeInbound(wrappedBuffer(b, 1, 2));
        assertThat(ch.readInbound(), is(nullValue()));
        ch.writeInbound(wrappedBuffer(b, 3, b.length - 3));
        assertThat(
                releaseLater((ByteBuf) ch.readInbound()),
                is(releaseLater(wrappedBuffer(new byte[] { 1, 1, 1, 1 }))));
    }

    @Test
    public void testRegularDecode() {
        byte[] b = new byte[2048];
        for (int i = 2; i < 2048; i ++) {
            b[i] = 1;
        }
        b[0] = -2;
        b[1] = 15;
        ch.writeInbound(wrappedBuffer(b, 0, 127));
        assertThat(ch.readInbound(), is(nullValue()));
        ch.writeInbound(wrappedBuffer(b, 127, 600));
        assertThat(ch.readInbound(), is(nullValue()));
        ch.writeInbound(wrappedBuffer(b, 727, b.length - 727));
        assertThat(releaseLater((ByteBuf) ch.readInbound()), is(releaseLater(wrappedBuffer(b, 2, b.length - 2))));
    }
}
