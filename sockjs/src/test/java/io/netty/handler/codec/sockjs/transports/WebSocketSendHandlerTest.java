/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.transports;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.util.CharsetUtil;

import org.junit.Test;

public class WebSocketSendHandlerTest {

    @Test
    public void messageReceived() throws Exception {
        final EmbeddedChannel ch = createWebsocketChannel(Config.prefix("/echo").build());
        ch.writeOutbound(new MessageFrame("testing"));
        final TextWebSocketFrame textFrame = (TextWebSocketFrame) ch.readOutbound();
        assertThat(textFrame.content().toString(CharsetUtil.UTF_8), equalTo("a[\"testing\"]"));
    }

    private EmbeddedChannel createWebsocketChannel(final Config config) throws Exception {
        return new EmbeddedChannel(
                new WebSocket13FrameDecoder(true, false, 2048),
                new WebSocketTransport(config),
                new WebSocketSendHandler());
    }

}
