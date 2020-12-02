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

import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.util.internal.ObjectUtil;

import java.util.function.Supplier;

/**
 * Codec that handles decoding and encoding of {@link Http3Frame}s.
 */
final class Http3FrameCodec extends CombinedChannelDuplexHandler<Http3FrameDecoder, Http3FrameEncoder> {
    Http3FrameCodec(QpackDecoder qpackDecoder, QpackEncoder qpackEncoder) {
        super(new Http3FrameDecoder(qpackDecoder), new Http3FrameEncoder(qpackEncoder));
    }

    static Supplier<Http3FrameCodec> newSupplier(QpackDecoder qpackDecoder, QpackEncoder qpackEncoder) {
        ObjectUtil.checkNotNull(qpackDecoder, "qpackDecoder");
        ObjectUtil.checkNotNull(qpackEncoder, "qpackEncoder");

        // QPACK decoder and encoder are shared between streams in a connection.
        return () ->  new Http3FrameCodec(qpackDecoder, qpackEncoder);
    }
}
