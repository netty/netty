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
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_DATA;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.readPaddingLength;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Flags;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

/**
 * An unmarshaller for {@link Http2DataFrame} instances. The buffer contained in the frames is a
 * slice of the original input buffer. If the frame needs to be persisted it should be copied.
 */
public class Http2DataFrameUnmarshaller extends AbstractHttp2FrameUnmarshaller {

  @Override
  protected void validate(Http2FrameHeader frameHeader) throws Http2Exception {
    if (frameHeader.getType() != FRAME_TYPE_DATA) {
      throw protocolError("Unsupported frame type: %d.", frameHeader.getType());
    }
    if (frameHeader.getStreamId() <= 0) {
      throw protocolError("A stream ID must be > 0.");
    }
    Http2Flags flags = frameHeader.getFlags();
    if (!flags.isPaddingLengthValid()) {
      throw protocolError("Pad high is set but pad low is not");
    }
    if (frameHeader.getPayloadLength() < flags.getNumPaddingLengthBytes()) {
      throw protocolError("Frame length %d too small.", frameHeader.getPayloadLength());
    }
    if (frameHeader.getPayloadLength() > MAX_FRAME_PAYLOAD_LENGTH) {
      throw protocolError("Frame length %d too big.", frameHeader.getPayloadLength());
    }
  }

  @Override
  protected Http2Frame doUnmarshall(Http2FrameHeader header, ByteBuf payload,
      ByteBufAllocator alloc) throws Http2Exception {
    DefaultHttp2DataFrame.Builder builder = new DefaultHttp2DataFrame.Builder();
    builder.setStreamId(header.getStreamId());

    Http2Flags flags = header.getFlags();
    builder.setEndOfStream(flags.isEndOfStream());

    // Read the padding length.
    int paddingLength = readPaddingLength(flags, payload);
    builder.setPaddingLength(paddingLength);

    // Determine how much data there is to read by removing the trailing
    // padding.
    int dataLength = payload.readableBytes() - paddingLength;
    if (dataLength < 0) {
      throw protocolError("Frame payload too small for padding.");
    }

    // Copy the remaining data into the frame.
    ByteBuf data = payload.slice(payload.readerIndex(), dataLength).retain();
    builder.setContent(data);

    // Skip the rest of the bytes in the payload.
    payload.skipBytes(payload.readableBytes());

    return builder.build();
  }

}
