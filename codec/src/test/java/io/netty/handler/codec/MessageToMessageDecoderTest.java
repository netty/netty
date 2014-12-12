/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;

import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

public class MessageToMessageDecoderTest {

    @Test
    public void testDecodeIncomplete() throws Exception {
        MessageToMessageDecoder<Object> handler = new MessageToMessageDecoder<Object>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, Object msg,
                    List<Object> out) throws Exception {
                // nothing, force a partial decode
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        Object msg = new Object();
        assertFalse(channel.writeInbound(msg));
        assertFalse(channel.finish());
    }

    @Test
    public void testDecodeComplete() throws Exception {
        MessageToMessageDecoder<Object> handler = new MessageToMessageDecoder<Object>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, Object msg,
                    List<Object> out) throws Exception {
                // force decode via pass-through
                out.add(msg);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        Object msg = new Object();
        assertTrue(channel.writeInbound(msg));
        assertTrue(channel.finish());
        assertEquals(msg, channel.readInbound());
    }

    @Test
    public void testChannelReadFiresForEachMessage() throws Exception {
        final Object msg1 = new Object();
        final Object msg2 = new Object();

        MessageToMessageDecoder<Object> handler = new MessageToMessageDecoder<Object>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, Object msg,
                    List<Object> out) throws Exception {
                // force decode via pass-through
                out.add(msg);
            }
        };

        ChannelHandlerAdapter sink = createMockBuilder(ChannelHandlerAdapter.class)
                .addMockedMethod("channelReadComplete")
                .createStrictMock();
        // two calls to channelReadComplete
        sink.channelReadComplete(anyObject(ChannelHandlerContext.class));
        expectLastCall();
        sink.channelReadComplete(anyObject(ChannelHandlerContext.class));
        expectLastCall();
        replay(sink);

        EmbeddedChannel channel = new EmbeddedChannel(handler, sink);

        assertTrue(channel.writeInbound(msg1));
        assertTrue(channel.writeInbound(msg2));
        assertTrue(channel.finish());
        assertEquals(msg1, channel.readInbound());
        assertEquals(msg2, channel.readInbound());

        verify(sink);
    }

    @Test
    public void testChannelReadDecodeAggregation() throws Exception {
        final String foo = "foo";
        final String bar = "bar";

        MessageToMessageDecoder<Object> handler = new MessageToMessageDecoder<Object>() {
            private String aggregation = "";
            @Override
            protected void decode(ChannelHandlerContext ctx, Object msg,
                    List<Object> out) throws Exception {
                // simulate a two-step decode/aggregation
                aggregation += (String) msg;
                if (msg == bar) {
                    out.add(aggregation);
                }
            }
        };

        ChannelHandlerAdapter sink = createMockBuilder(ChannelHandlerAdapter.class)
                .addMockedMethod("channelReadComplete")
                .createStrictMock();
        sink.channelReadComplete(anyObject(ChannelHandlerContext.class));
        expectLastCall();
        replay(sink);

        EmbeddedChannel channel = new EmbeddedChannel(handler, sink);

        assertFalse(channel.writeInbound(foo));
        assertTrue(channel.writeInbound(bar));
        assertTrue(channel.finish());
        assertEquals(foo + bar, channel.readInbound());

        verify(sink);
    }

    @Test
    public void testChannelReadIncompleteReadAgain() throws Exception {
        MessageToMessageDecoder<Object> handler = new MessageToMessageDecoder<Object>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, Object msg,
                    List<Object> out) throws Exception {
                // nothing, force a partial decode
            }
        };

        Object msg = new Object();

        // make sure an incomplete decode causes a new read to be issued
        final AtomicBoolean readCalled = new AtomicBoolean(false);
        EmbeddedChannel channel = new EmbeddedChannel(handler) {
            @Override
            protected void doBeginRead() throws Exception {
                readCalled.set(Boolean.TRUE);
            }
        };
        channel.config().setAutoRead(false);

        assertFalse(channel.writeInbound(msg));
        assertFalse(channel.finish());
        assertTrue(readCalled.get());
    }

}
