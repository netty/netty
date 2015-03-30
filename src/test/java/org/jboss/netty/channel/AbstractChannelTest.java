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
package org.jboss.netty.channel;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.Assert.*;

public class AbstractChannelTest {
    private static class TestChannel extends AbstractChannel {
        private static final Integer DUMMY_ID = 0;

        private final ChannelConfig config;
        private final SocketAddress localAddress = new InetSocketAddress(1);
        private final SocketAddress remoteAddress = new InetSocketAddress(2);

        TestChannel(ChannelPipeline pipeline, ChannelSink sink) {
            super(DUMMY_ID, null, null, pipeline, sink);
            config = new DefaultChannelConfig();
        }
        public ChannelConfig getConfig() {
            return config;
        }
        public SocketAddress getLocalAddress() {
            return localAddress;
        }
        public SocketAddress getRemoteAddress() {
            return remoteAddress;
        }
        public boolean isBound() {
            return true;
        }
        public boolean isConnected() {
            return true;
        }
    }
    private static class TestChannelSink extends AbstractChannelSink {
        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
            // Do nothing
        }
    }
    private static class TestChannelHandler extends SimpleChannelHandler {
        private final StringBuilder buf = new StringBuilder();
        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            buf.append(ctx.getChannel().isWritable());
            buf.append(' ');
            super.channelInterestChanged(ctx, e);
        }
    }
    @Test
    public void testUserDefinedWritability() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
        TestChannelHandler handler = new TestChannelHandler();
        StringBuilder buf = handler.buf;
        pipeline.addLast("TRACE", handler);
        TestChannel channel = new TestChannel(pipeline, new TestChannelSink());
        // No configuration by default on Low and High WaterMark for no Nio
        // Ensure that the default value of a user-defined writability flag is true.
        for (int i = 1; i <= 30; i ++) {
            assertTrue(channel.getUserDefinedWritability(i));
        }
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        // Ensure that setting a user-defined writability flag to false affects channel.isWritable();
        channel.setUserDefinedWritability(1, false);
        assertEquals("false ", buf.toString());
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        // Ensure that setting a user-defined writability flag to true affects channel.isWritable();
        channel.setUserDefinedWritability(1, true);
        assertEquals("false true ", buf.toString());
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
    }
    @Test
    public void testUserDefinedWritability2() {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
        TestChannelHandler handler = new TestChannelHandler();
        StringBuilder buf = handler.buf;
        pipeline.addLast("TRACE", handler);
        TestChannel channel = new TestChannel(pipeline, new TestChannelSink());
        // No configuration by default on Low and High WaterMark for no Nio
        // Ensure that the default value of a user-defined writability flag is true.
        for (int i = 1; i <= 30; i ++) {
            assertTrue(channel.getUserDefinedWritability(i));
        }
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        // Ensure that setting a user-defined writability flag to false affects channel.isWritable();
        channel.setUserDefinedWritability(1, false);
        assertEquals("false ", buf.toString());
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        // Ensure that setting another user-defined writability flag to false does not trigger
        // channelWritabilityChanged.
        channel.setUserDefinedWritability(2, false);
        assertEquals("false ", buf.toString());
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        // Ensure that setting only one user-defined writability flag to true does not affect channel.isWritable()
        channel.setUserDefinedWritability(1, true);
        assertEquals("false ", buf.toString());
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        // Ensure that setting all user-defined writability flags to true affects channel.isWritable()
        channel.setUserDefinedWritability(2, true);
        assertEquals("false true ", buf.toString());
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
    }
}
