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
package io.netty.handler.codec.http3;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.quic.QuicStreamType;

import java.util.Arrays;
import java.util.List;

public class Http3PushStreamServerValidationHandlerTest extends
        AbstractHttp3FrameTypeValidationHandlerTest<Http3PushStreamFrame> {

    public Http3PushStreamServerValidationHandlerTest() {
        super(QuicStreamType.UNIDIRECTIONAL, false, true);
    }

    @Override
    protected ChannelHandler newHandler(boolean server) {
        return Http3PushStreamServerValidationHandler.INSTANCE;
    }

    @Override
    protected List<Http3PushStreamFrame> newValidFrames() {
        return Arrays.asList(new DefaultHttp3HeadersFrame(), new DefaultHttp3DataFrame(Unpooled.EMPTY_BUFFER));
    }

    @Override
    protected List<Http3Frame> newInvalidFrames() {
        return Arrays.asList(Http3TestUtils.newHttp3RequestStreamFrame(), Http3TestUtils.newHttp3ControlStreamFrame());
    }
}
