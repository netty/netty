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

package io.netty.handler.codec.http2.draft10.frame.decoder;

import static io.netty.handler.codec.http2.draft10.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_PING;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.PING_FRAME_PAYLOAD_LENGTH;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

/**
 * An unmarshaller for {@link Http2PingFrame} instances. The buffer contained in the frames is a
 * slice of the original input buffer. If the frame needs to be persisted it should be copied.
 */
public class Http2PingFrameUnmarshaller extends AbstractHttp2FrameUnmarshaller {

    @Override
    protected void validate(Http2FrameHeader frameHeader) throws Http2Exception {
        if (frameHeader.getType() != FRAME_TYPE_PING) {
            throw protocolError("Unsupported frame type: %d.", frameHeader.getType());
        }
        if (frameHeader.getStreamId() != 0) {
            throw protocolError("A stream ID must be zero.");
        }
        if (frameHeader.getPayloadLength() != PING_FRAME_PAYLOAD_LENGTH) {
            throw protocolError("Frame length %d incorrect size for ping.",
                    frameHeader.getPayloadLength());
        }
    }

    @Override
    protected Http2Frame doUnmarshall(Http2FrameHeader header, ByteBuf payload,
                                      ByteBufAllocator alloc) throws Http2Exception {
        DefaultHttp2PingFrame.Builder builder = new DefaultHttp2PingFrame.Builder();
        builder.setAck(header.getFlags().isAck());

        // The remainder of this frame is the opaque data.
        ByteBuf data = payload.slice().retain();
        builder.setData(data);

        return builder.build();
    }
}
