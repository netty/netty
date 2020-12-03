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

import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Http3FrameCodecTest extends Http3FrameEncoderDecoderTest {
    public Http3FrameCodecTest(boolean fragmented) {
        super(fragmented);
    }

    @Override
    protected ChannelOutboundHandler newEncoder() {
        return new Http3FrameCodec(new QpackDecoder(), Long.MAX_VALUE, new QpackEncoder());
    }

    @Override
    protected ChannelInboundHandler newDecoder() {
        return new Http3FrameCodec(new QpackDecoder(), Long.MAX_VALUE, new QpackEncoder());
    }
}
