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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

/**
 * <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8">Unknown HTTP3 frame</a>.
 * These frames are valid on all stream types.
 * <pre>
 *    HTTP/3 Frame Format {
 *      Type (i),
 *      Length (i),
 *      Frame Payload (..),
 *    }
 * </pre>
 */
public interface Http3UnknownFrame extends
        Http3RequestStreamFrame, Http3PushStreamFrame, Http3ControlStreamFrame, ByteBufHolder {

    /**
     * Return the payload length of the frame.
     *
     * @return the length.
     */
    default long length() {
        return content().readableBytes();
    }

    @Override
    Http3UnknownFrame copy();

    @Override
    Http3UnknownFrame duplicate();

    @Override
    Http3UnknownFrame retainedDuplicate();

    @Override
    Http3UnknownFrame replace(ByteBuf content);

    @Override
    Http3UnknownFrame retain();

    @Override
    Http3UnknownFrame retain(int increment);

    @Override
    Http3UnknownFrame touch();

    @Override
    Http3UnknownFrame touch(Object hint);
}
