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

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FLAG_END_HEADERS;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_CONTINUATION;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_PUSH_PROMISE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_FRAME_PAYLOAD_LENGTH;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2PushPromiseFrame;

import com.google.common.base.Preconditions;

public class Http2PushPromiseFrameMarshaller extends
        AbstractHttp2FrameMarshaller<Http2PushPromiseFrame> {

    private final Http2HeadersEncoder headersEncoder;

    public Http2PushPromiseFrameMarshaller(Http2HeadersEncoder headersEncoder) {
        super(Http2PushPromiseFrame.class);
        this.headersEncoder = Preconditions.checkNotNull(headersEncoder, "headersEncoder");
    }

    @Override
    protected void doMarshall(Http2PushPromiseFrame frame, ByteBuf out, ByteBufAllocator alloc)
            throws Http2Exception {

        // Max size minus the promised stream ID.
        int maxFragmentLength = MAX_FRAME_PAYLOAD_LENGTH - 4;

        // Encode the entire header block into an intermediate buffer.
        ByteBuf headerBlock = alloc.buffer();
        headersEncoder.encodeHeaders(frame.getHeaders(), headerBlock);

        ByteBuf fragment =
                headerBlock.readSlice(Math.min(headerBlock.readableBytes(), maxFragmentLength));

        // Write the frame header.
        out.ensureWritable(FRAME_HEADER_LENGTH + fragment.readableBytes());
        out.writeShort(fragment.readableBytes() + 4);
        out.writeByte(FRAME_TYPE_PUSH_PROMISE);
        out.writeByte(headerBlock.readableBytes() == 0 ? FLAG_END_HEADERS : 0);
        out.writeInt(frame.getStreamId());

        // Write out the promised stream ID.
        out.writeInt(frame.getPromisedStreamId());

        // Write the first fragment.
        out.writeBytes(fragment);

        // Process any continuation frames there might be.
        while (headerBlock.readableBytes() > 0) {
            writeContinuationFrame(frame.getStreamId(), headerBlock, out);
        }

        // Release the intermediate buffer.
        headerBlock.release();
    }

    /**
     * Writes a single continuation frame with a fragment of the header block to the output buffer.
     */
    private static void writeContinuationFrame(int streamId, ByteBuf headerBlock, ByteBuf out) {
        ByteBuf fragment =
                headerBlock.readSlice(Math.min(headerBlock.readableBytes(), MAX_FRAME_PAYLOAD_LENGTH));

        // Write the frame header.
        out.ensureWritable(FRAME_HEADER_LENGTH + fragment.readableBytes());
        out.writeShort(fragment.readableBytes());
        out.writeByte(FRAME_TYPE_CONTINUATION);
        out.writeByte(headerBlock.readableBytes() == 0 ? FLAG_END_HEADERS : 0);
        out.writeInt(streamId);

        // Write the headers block.
        out.writeBytes(fragment);
    }
}
