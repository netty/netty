/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import org.junit.Test;
import static org.junit.Assert.*;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebSocket08FrameDecoderTest {

    @Test
    public void channelInactive() throws Exception {
        final WebSocket08FrameDecoder decoder = new WebSocket08FrameDecoder(true, true, 65535, false);
        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        decoder.channelInactive(ctx);
        Mockito.verify(ctx).fireChannelInactive();
    }

    @Test
    public void supportIanaStatusCodes() throws Exception {
        Set<Integer> forbiddenIanaCodes = new HashSet<Integer>();
        forbiddenIanaCodes.add(1004);
        forbiddenIanaCodes.add(1005);
        forbiddenIanaCodes.add(1006);
        Set<Integer> validIanaCodes = new HashSet<Integer>();
        for (int i = 1000; i < 1015; i++) {
            validIanaCodes.add(i);
        }
        validIanaCodes.removeAll(forbiddenIanaCodes);

        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        Mockito.when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

        Channel channel = Mockito.mock(Channel.class);
        Mockito.when(channel.isActive()).thenReturn(false);
        Mockito.when(ctx.channel()).thenReturn(channel);

        List<Object> out = new ArrayList<Object>();

        for (int statusCode: validIanaCodes) {
            WebSocket08FrameEncoder encoder = new WebSocket08FrameEncoder(true);
            WebSocket08FrameDecoder decoder = new WebSocket08FrameDecoder(true, true, 65535, false);

            CloseWebSocketFrame inputFrame = new CloseWebSocketFrame(statusCode, "Bye");
            try {
                encoder.encode(ctx, inputFrame, out);
                ByteBuf serializedCloseFrame = (ByteBuf) out.get(0);
                out.clear();

                decoder.decode(ctx, serializedCloseFrame, out);
                CloseWebSocketFrame outputFrame = (CloseWebSocketFrame) out.get(0);
                out.clear();

                try {
                    assertEquals(statusCode, outputFrame.statusCode());
                } finally {
                    outputFrame.release();
                }
            } finally {
                inputFrame.release();
            }
        }
    }
}
