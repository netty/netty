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

package io.netty.handler.codec.http2.draft10.frame.encoder;

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_GO_AWAY;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.writeUnsignedInt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.frame.Http2GoAwayFrame;

public class Http2GoAwayFrameMarshaller extends AbstractHttp2FrameMarshaller<Http2GoAwayFrame> {

    public Http2GoAwayFrameMarshaller() {
        super(Http2GoAwayFrame.class);
    }

    @Override
    protected void doMarshall(Http2GoAwayFrame frame, ByteBuf out, ByteBufAllocator alloc) {
        ByteBuf data = frame.content();

        // Write the frame header.
        int payloadLength = data.readableBytes() + 8;
        out.ensureWritable(FRAME_HEADER_LENGTH + payloadLength);
        out.writeShort(payloadLength);
        out.writeByte(FRAME_TYPE_GO_AWAY);
        out.writeByte(0);
        out.writeInt(0);

        out.writeInt(frame.getLastStreamId());
        writeUnsignedInt(frame.getErrorCode(), out);

        // Write the debug data.
        out.writeBytes(data, data.readerIndex(), data.readableBytes());
    }
}
