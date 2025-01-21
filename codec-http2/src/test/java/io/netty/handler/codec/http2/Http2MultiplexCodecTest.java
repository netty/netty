/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandler;

public class Http2MultiplexCodecTest extends Http2MultiplexTest<Http2FrameCodec> {

    @Override
    protected Http2FrameCodec newCodec(TestChannelInitializer childChannelInitializer, Http2FrameWriter frameWriter) {
        return new Http2MultiplexCodecBuilder(true, childChannelInitializer).frameWriter(frameWriter).build();
    }

    @Override
    protected ChannelHandler newMultiplexer(TestChannelInitializer childChannelInitializer) {
        return null;
    }

    @Override
    protected boolean useUserEventForResetFrame() {
        return false;
    }

    @Override
    protected boolean ignoreWindowUpdateFrames() {
        return false;
    }

    @Override
    protected boolean useUserEventForPriorityFrame() {
        return false;
    }
}
