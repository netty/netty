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

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class Http3ControlStreamFrameDispatcherTest {
    @Test
    public void testOnlyDispatchControlFrame() {
        EmbeddedChannel controlChannel = new EmbeddedChannel();
        EmbeddedChannel channel = new EmbeddedChannel(new Http3ControlStreamFrameDispatcher(controlChannel));
        Http3Frame frame = new Http3Frame() { };
        Http3RequestStreamFrame requestStreamFrame = new Http3RequestStreamFrame() { };
        Http3PushStreamFrame pushStreamFrame = new Http3PushStreamFrame() { };

        assertTrue(channel.writeOutbound(frame));
        assertTrue(channel.writeOutbound(requestStreamFrame));
        assertTrue(channel.writeOutbound(pushStreamFrame));
        assertSame(frame, channel.readOutbound());
        assertSame(requestStreamFrame, channel.readOutbound());
        assertSame(pushStreamFrame, channel.readOutbound());
        assertFalse(channel.finish());
        assertFalse(controlChannel.finish());
    }

    @Test
    public void testDispatchControlFrame() {
        EmbeddedChannel controlChannel = new EmbeddedChannel();
        EmbeddedChannel channel = new EmbeddedChannel(new Http3ControlStreamFrameDispatcher(controlChannel));
        Http3ControlStreamFrame frame = new Http3ControlStreamFrame() { };

        assertFalse(channel.writeOutbound(frame));
        assertFalse(channel.finish());
        assertSame(frame, controlChannel.readOutbound());
        assertFalse(controlChannel.finish());
    }
}
