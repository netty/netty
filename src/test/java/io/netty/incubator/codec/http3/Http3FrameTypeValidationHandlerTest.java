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
import io.netty.incubator.codec.quic.QuicStreamType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

abstract class Http3FrameTypeValidationHandlerTest extends
        AbstractHttp3FrameTypeValidationHandlerTest<Http3RequestStreamFrame> {

    Http3FrameTypeValidationHandlerTest(boolean isOutbound, boolean isInbound) {
        super(true, QuicStreamType.BIDIRECTIONAL);
    }

    @Override
    protected ChannelHandler newHandler() {
        return new Http3FrameTypeDuplexValidationHandler<>(Http3RequestStreamFrame.class);
    }

    @Override
    protected List<Http3RequestStreamFrame> newValidFrames() {
        return Collections.singletonList(Http3TestUtils.newHttp3RequestStreamFrame());
    }

    @Override
    protected List<Http3Frame> newInvalidFrames() {
        return Arrays.asList(Http3TestUtils.newHttp3ControlStreamFrame(), Http3TestUtils.newHttp3PushStreamFrame());
    }
}
