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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.quic.QuicStreamChannel;

import static org.junit.Assert.assertNotNull;

public class Http3ServerConnectionHandlerTest extends AbtractHttp3ConnectionHandlerTest {
    private static final ChannelHandler REQUEST_HANDLER = new ChannelInboundHandlerAdapter() {
        @Override
        public boolean isSharable() {
            return true;
        }
    };

    @Override
    protected Http3ConnectionHandler newConnectionHandler() {
        return new Http3ServerConnectionHandler(REQUEST_HANDLER);
    }

    @Override
    protected void assertBidirectionalStreamHandled(EmbeddedQuicChannel channel, QuicStreamChannel streamChannel) {
        assertNotNull(streamChannel.pipeline().context(REQUEST_HANDLER));
    }
}
