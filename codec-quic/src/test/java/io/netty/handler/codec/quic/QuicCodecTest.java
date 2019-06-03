/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Quic.
 */
public class QuicCodecTest {

    @Mock
    protected final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

    @Mock
    protected final Channel channel = mock(Channel.class);

    protected QuicDecoder decoder = new QuicDecoder();
    protected QuicEncoder encoder = new QuicEncoder();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    public void testStringCodec() {
        ByteBuf buf = Unpooled.buffer();
        try {
            String STR = "This is a test";
            QuicEncoder.writeString(buf, STR);
            assertEquals(STR, QuicDecoder.decodeString(buf));
        } finally {
            buf.release();
        }
    }

}
