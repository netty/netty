/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.Queue;

import org.junit.Test;

/**
 * Tests for {@link Http2PrefaceHandler}.
 */
public class Http2PrefaceHandlerTest {

    @Test
    public void clientShouldWritePrefaceAtStartup() {
        EmbeddedChannel channel = createChannel(false);
        // Ensure that the preface was automatically written at startup.
        Queue<Object> outboundMessages = channel.outboundMessages();
        assertTrue(channel.isOpen());
        assertNull(channel.pipeline().get(Http2PrefaceHandler.class));
        assertTrue(channel.finish());
        assertEquals(1, outboundMessages.size());
        assertEquals(connectionPrefaceBuf(), outboundMessages.peek());
    }

    @Test
    public void serverShouldNotWritePrefaceAtStartup() {
        EmbeddedChannel channel = createChannel(true);
        // Ensure that the preface was automatically written at startup.
        Queue<Object> outboundMessages = channel.outboundMessages();
        assertTrue(channel.isOpen());
        assertNotNull(channel.pipeline().get(Http2PrefaceHandler.class));
        assertFalse(channel.finish());
        assertTrue(outboundMessages.isEmpty());
    }

    @Test
    public void serverShouldBeRemovedAfterReceivingPreface() {
        EmbeddedChannel channel = createChannel(true);
        // Simulate receiving the preface.
        assertTrue(channel.writeInbound(connectionPrefaceBuf()));
        assertNull(channel.pipeline().get(Http2PrefaceHandler.class));
        assertTrue(channel.isOpen());
        assertTrue(channel.finish());
    }

    @Test
    public void serverReceivingBadPrefaceShouldCloseTheConnection() {
        EmbeddedChannel channel = createChannel(true);
        // Simulate receiving the bad preface.
        assertFalse(channel.writeInbound(Unpooled.copiedBuffer("BAD_PREFACE", UTF_8)));
        assertFalse(channel.isOpen());
        assertFalse(channel.finish());
    }

    private EmbeddedChannel createChannel(boolean server) {
        return new EmbeddedChannel(new Http2PrefaceHandler(server));
    }
}
