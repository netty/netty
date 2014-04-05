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
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_HEADERS;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.readPaddingLength;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.readUnsignedInt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.Http2Headers;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Flags;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

import com.google.common.base.Preconditions;

/**
 * An unmarshaller for {@link Http2HeadersFrame} instances.
 */
public class Http2HeadersFrameUnmarshaller extends AbstractHeadersUnmarshaller {

    private final Http2HeadersDecoder headersDecoder;

    public Http2HeadersFrameUnmarshaller(Http2HeadersDecoder headersDecoder) {
        this.headersDecoder = Preconditions.checkNotNull(headersDecoder, "headersDecoder");
    }

    @Override
    protected void validateStartOfHeaderBlock(Http2FrameHeader frameHeader) throws Http2Exception {
        if (frameHeader.getType() != FRAME_TYPE_HEADERS) {
            throw protocolError("Unsupported frame type: %d.", frameHeader.getType());
        }

        if (frameHeader.getStreamId() <= 0) {
            throw protocolError("A stream ID must > 0.");
        }

        Http2Flags flags = frameHeader.getFlags();
        if (flags.isPriorityPresent() && frameHeader.getPayloadLength() < 4) {
            throw protocolError("Frame length too small." + frameHeader.getPayloadLength());
        }

        if (!flags.isPaddingLengthValid()) {
            throw protocolError("Pad high is set but pad low is not");
        }

        if (frameHeader.getPayloadLength() < flags.getNumPaddingLengthBytes()) {
            throw protocolError("Frame length %d too small for padding.", frameHeader.getPayloadLength());
        }

        if (frameHeader.getPayloadLength() > MAX_FRAME_PAYLOAD_LENGTH) {
            throw protocolError("Frame length %d too big.", frameHeader.getPayloadLength());
        }
    }

    @Override
    protected FrameBuilder createFrameBuilder(final Http2FrameHeader header, final ByteBuf payload,
                                              ByteBufAllocator alloc) throws Http2Exception {
        try {
            final DefaultHttp2HeadersFrame.Builder builder = new DefaultHttp2HeadersFrame.Builder();
            builder.setStreamId(header.getStreamId());

            Http2Flags flags = header.getFlags();
            builder.setEndOfStream(flags.isEndOfStream());

            // Read the padding length.
            int paddingLength = readPaddingLength(flags, payload);

            // Read the priority if it was included in the frame.
            if (flags.isPriorityPresent()) {
                int priority = readUnsignedInt(payload);
                builder.setPriority(priority);
            }

            // Determine how much data there is to read by removing the trailing
            // padding.
            int dataLength = payload.readableBytes() - paddingLength;
            if (dataLength < 0) {
                throw protocolError("Payload too small for padding");
            }

            // Get a view of the header block portion of the payload.
            final ByteBuf headerSlice = payload.readSlice(dataLength);

            // The remainder of this frame is the headers block.
            if (flags.isEndOfHeaders()) {
                // Optimization: don't copy the buffer if we have the entire headers block.
                return new FrameBuilder() {
                    @Override
                    int getStreamId() {
                        return header.getStreamId();
                    }

                    @Override
                    Http2Frame buildFrame() throws Http2Exception {
                        Http2Headers headers = headersDecoder.decodeHeaders(headerSlice);
                        builder.setHeaders(headers);
                        return builder.build();
                    }
                };
            }

            // The header block is not complete. Await one or more continuation frames
            // to complete the block before decoding.
            FrameBuilder frameBuilder = new FrameBuilder() {
                @Override
                int getStreamId() {
                    return header.getStreamId();
                }

                @Override
                Http2Frame buildFrame() throws Http2Exception {
                    try {
                        Http2Headers headers = headersDecoder.decodeHeaders(headerBlock);
                        builder.setHeaders(headers);
                        return builder.build();
                    } finally {
                        headerBlock.release();
                        headerBlock = null;
                    }
                }
            };

            // Copy and add the initial fragment of the header block.
            frameBuilder.addHeaderFragment(headerSlice, alloc);

            return frameBuilder;
        } finally {
            payload.skipBytes(payload.readableBytes());
        }
    }
}
