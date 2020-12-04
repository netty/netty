/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.AttributeMap;
import io.netty.util.DefaultAttributeMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class Http3UnidirectionalStreamInboundHandlerTest {

    private final boolean server;

    @Parameterized.Parameters(name = "{index}: server = {0}")
    public static Object[] parameters() {
        return new Object[] { false, true };
    }

    public Http3UnidirectionalStreamInboundHandlerTest(boolean server) {
        this.server = server;
    }

    @Test
    public void testControlStream() {
        testStreamSetup(0x00, Http3ControlStreamInboundHandler.class, true);
    }

    @Test
    public void testQpackEncoderStream() {
        testStreamSetup(0x02, QpackStreamHandler.class, false);
    }

    @Test
    public void testQpackDecoderStream() {
        testStreamSetup(0x03, QpackStreamHandler.class, false);
    }

    private void testStreamSetup(long type, Class<? extends ChannelHandler> clazz, boolean hasCodec) {
        QuicChannel parent = Http3TestUtils.mockParent();
        AttributeMap map = new DefaultAttributeMap();
        when(parent.attr(any())).then(i -> map.attr(i.getArgument(0)));
        Http3UnidirectionalStreamInboundHandler handler = new Http3UnidirectionalStreamInboundHandler(true,
                CodecHandler::new, null);
        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, handler);
        ByteBuf buffer = Unpooled.buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, type);
        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        assertNull(channel.pipeline().context(handler));
        assertNotNull(channel.pipeline().context(clazz));
        if (hasCodec) {
            assertNotNull(channel.pipeline().context(CodecHandler.class));
        } else {
            assertNull(channel.pipeline().context(CodecHandler.class));
        }
        assertFalse(channel.finish());

        channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, new Http3UnidirectionalStreamInboundHandler(server,
                CodecHandler::new, null));

        // Try to create the stream a second time, this should fail
        buffer = Unpooled.buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, type);
        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        Http3TestUtils.verifyClose(Http3ErrorCode.H3_STREAM_CREATION_ERROR, parent);
        assertFalse(channel.finish());
    }

    private static final class CodecHandler extends ChannelHandlerAdapter {  }
}
