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
package io.netty.handler.codec.json;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Before;
import org.junit.Test;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class JsonCodecTest {
    private EmbeddedChannel ch;

    @Before
    public void setUp() throws Exception {
        ch = new EmbeddedChannel(new JsonCodec<Foo>(Foo.class));
    }

    @Test
    public void testEncode() throws Exception {
        Foo device = new Foo().setToDefaultValue();
        byte[] bytes = JsonCodec.getObjectMapper().writeValueAsBytes(device);
        ch.writeOutbound(device);
        assertThat((ByteBuf) ch.readOutbound(), is(wrappedBuffer(bytes)));
    }

    @Test
    public void testDecode() throws Exception {
        Foo device = new Foo().setToDefaultValue();
        byte[] bytes = JsonCodec.getObjectMapper().writeValueAsBytes(device);
        ch.writeInbound(Unpooled.wrappedBuffer(bytes));
        assertThat((Foo) ch.readInbound(), is(device));
    }
}
