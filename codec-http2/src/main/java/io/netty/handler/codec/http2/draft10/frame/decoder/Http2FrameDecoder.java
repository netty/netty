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

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_LENGTH_MASK;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.readUnsignedInt;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2Flags;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

import java.util.List;

/**
 * Decodes {@link Http2Frame} objects from an input {@link ByteBuf}. The frames that this handler
 * emits can be configured by providing a {@link Http2FrameUnmarshaller}. By default, the
 * {@link Http2StandardFrameUnmarshaller} is used to handle all frame types - see the documentation
 * for details.
 *
 * @see Http2StandardFrameUnmarshaller
 */
public class Http2FrameDecoder extends ByteToMessageDecoder {

    private enum State {
        FRAME_HEADER,
        FRAME_PAYLOAD,
        ERROR
    }

    private final Http2FrameUnmarshaller frameUnmarshaller;
    private State state;
    private int payloadLength;

    public Http2FrameDecoder() {
        this(new Http2StandardFrameUnmarshaller());
    }

    public Http2FrameDecoder(Http2FrameUnmarshaller frameUnmarshaller) {
        if (frameUnmarshaller == null) {
            throw new NullPointerException("frameUnmarshaller");
        }
        this.frameUnmarshaller = frameUnmarshaller;
        state = State.FRAME_HEADER;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            switch (state) {
                case FRAME_HEADER:
                    processFrameHeader(in);
                    if (state == State.FRAME_HEADER) {
                        // Still haven't read the entire frame header yet.
                        break;
                    }

                    // If we successfully read the entire frame header, drop down and start processing
                    // the payload now.

                case FRAME_PAYLOAD:
                    processFramePayload(ctx, in, out);
                    break;
                case ERROR:
                    in.skipBytes(in.readableBytes());
                    break;
                default:
                    throw new IllegalStateException("Should never get here");
            }
        } catch (Throwable t) {
            ctx.fireExceptionCaught(t);
            state = State.ERROR;
        }
    }

    private void processFrameHeader(ByteBuf in) throws Http2Exception {
        if (in.readableBytes() < FRAME_HEADER_LENGTH) {
            // Wait until the entire frame header has been read.
            return;
        }

        // Read the header and prepare the unmarshaller to read the frame.
        Http2FrameHeader frameHeader = readFrameHeader(in);
        payloadLength = frameHeader.getPayloadLength();
        frameUnmarshaller.unmarshall(frameHeader);

        // Start reading the payload for the frame.
        state = State.FRAME_PAYLOAD;
    }

    private void processFramePayload(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Http2Exception {
        if (in.readableBytes() < payloadLength) {
            // Wait until the entire payload has been read.
            return;
        }

        // Get a view of the buffer for the size of the payload.
        ByteBuf payload = in.readSlice(payloadLength);

        // Create the frame and add it to the output.
        Http2Frame frame = frameUnmarshaller.from(payload, ctx.alloc());
        if (frame != null) {
            out.add(frame);
        }

        // Go back to reading the next frame header.
        state = State.FRAME_HEADER;
    }

    /**
     * Reads the frame header from the input buffer and creates an envelope initialized with those
     * values.
     */
    private static Http2FrameHeader readFrameHeader(ByteBuf in) {
        int payloadLength = in.readUnsignedShort() & FRAME_LENGTH_MASK;
        short type = in.readUnsignedByte();
        short flags = in.readUnsignedByte();
        int streamId = readUnsignedInt(in);

        return new Http2FrameHeader.Builder().setPayloadLength(payloadLength).setType(type)
                .setFlags(new Http2Flags(flags)).setStreamId(streamId).build();
    }
}
