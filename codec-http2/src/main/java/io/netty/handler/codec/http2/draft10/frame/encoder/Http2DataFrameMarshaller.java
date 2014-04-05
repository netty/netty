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

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_END_STREAM;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_DATA;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.setPaddingFlags;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.writePaddingLength;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Flags;

public class Http2DataFrameMarshaller extends AbstractHttp2FrameMarshaller<Http2DataFrame> {

    public Http2DataFrameMarshaller() {
        super(Http2DataFrame.class);
    }

    @Override
    protected void doMarshall(Http2DataFrame frame, ByteBuf out, ByteBufAllocator alloc) {
        ByteBuf data = frame.content();

        Http2Flags flags = getFlags(frame);

        // Write the frame header.
        int payloadLength = data.readableBytes() + frame.getPaddingLength()
                + (flags.isPadHighPresent() ? 1 : 0) + (flags.isPadLowPresent() ? 1 : 0);
        out.ensureWritable(FRAME_HEADER_LENGTH + payloadLength);
        out.writeShort(payloadLength);
        out.writeByte(FRAME_TYPE_DATA);
        out.writeByte(flags.getValue());
        out.writeInt(frame.getStreamId());

        writePaddingLength(frame.getPaddingLength(), out);

        // Write the data.
        out.writeBytes(data, data.readerIndex(), data.readableBytes());

        // Write the required padding.
        out.writeZero(frame.getPaddingLength());
    }

    private static Http2Flags getFlags(Http2DataFrame frame) {
        short flags = 0;
        if (frame.isEndOfStream()) {
            flags |= FLAG_END_STREAM;
        }

        flags = setPaddingFlags(flags, frame.getPaddingLength());
        return new Http2Flags(flags);
    }
}
