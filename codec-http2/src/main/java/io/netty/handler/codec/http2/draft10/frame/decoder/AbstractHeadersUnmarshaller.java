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
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.FRAME_TYPE_CONTINUATION;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.readPaddingLength;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2Flags;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

public abstract class AbstractHeadersUnmarshaller extends AbstractHttp2FrameUnmarshaller {

  /**
   * A builder for a headers/push_promise frame.
   */
  protected abstract class FrameBuilder {
    protected ByteBuf headerBlock;

    abstract int getStreamId();

    final void addHeaderFragment(ByteBuf fragment, ByteBufAllocator alloc) {
      if (headerBlock == null) {
        headerBlock = alloc.buffer(fragment.readableBytes());
        headerBlock.writeBytes(fragment);
      } else {
        ByteBuf buf = alloc.buffer(headerBlock.readableBytes() + fragment.readableBytes());
        buf.writeBytes(headerBlock);
        buf.writeBytes(fragment);
        headerBlock.release();
        headerBlock = buf;
      }
    }

    abstract Http2Frame buildFrame() throws Http2Exception;
  }

  private FrameBuilder frameBuilder;

  @Override
  protected final void validate(Http2FrameHeader frameHeader) throws Http2Exception {
    if (frameBuilder == null) {
      // This frame is the beginning of a headers/push_promise.
      validateStartOfHeaderBlock(frameHeader);
      return;
    }

    // Validate the continuation of a headers block.
    if (frameHeader.getType() != FRAME_TYPE_CONTINUATION) {
      throw protocolError("Unsupported frame type: %d.", frameHeader.getType());
    }
    if (frameBuilder.getStreamId() != frameHeader.getStreamId()) {
      throw protocolError("Continuation received for wrong stream. Expected %d, found %d",
          frameBuilder.getStreamId(), frameHeader.getStreamId());
    }
    Http2Flags flags = frameHeader.getFlags();
    if (!flags.isPaddingLengthValid()) {
      throw protocolError("Pad high is set but pad low is not");
    }
    if (frameHeader.getPayloadLength() < flags.getNumPaddingLengthBytes()) {
      throw protocolError("Frame length %d to small.", frameHeader.getPayloadLength());
    }
    if (frameHeader.getPayloadLength() > MAX_FRAME_PAYLOAD_LENGTH) {
      throw protocolError("Frame length %d too big.", frameHeader.getPayloadLength());
    }
  }

  @Override
  protected final Http2Frame doUnmarshall(Http2FrameHeader header, ByteBuf payload,
      ByteBufAllocator alloc) throws Http2Exception {
    Http2Flags flags = header.getFlags();
    if (frameBuilder == null) {
      // This is the start of a headers/push_promise frame. Delegate to the subclass to create
      // the appropriate builder for the frame.
      frameBuilder = createFrameBuilder(header, payload, alloc);
    } else {
      // Processing a continuation frame for a headers/push_promise. Update the current frame
      // builder with the new fragment.

      int paddingLength = readPaddingLength(flags, payload);

      // Determine how much data there is to read by removing the trailing
      // padding.
      int dataLength = payload.readableBytes() - paddingLength;
      if (dataLength < 0) {
        throw protocolError("Payload too small for padding.");
      }

      // The remainder of this frame is the headers block.
      frameBuilder.addHeaderFragment(payload, alloc);
    }

    // If the headers are complete, build the frame.
    Http2Frame frame = null;
    if (flags.isEndOfHeaders()) {
      frame = frameBuilder.buildFrame();
      frameBuilder = null;
    }

    return frame;
  }

  protected abstract void validateStartOfHeaderBlock(Http2FrameHeader frameHeader)
      throws Http2Exception;

  protected abstract FrameBuilder createFrameBuilder(Http2FrameHeader header, ByteBuf payload,
      ByteBufAllocator alloc) throws Http2Exception;
}
