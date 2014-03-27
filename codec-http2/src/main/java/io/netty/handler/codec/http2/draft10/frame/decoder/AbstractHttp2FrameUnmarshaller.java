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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameHeader;

/**
 * Abstract base class for all {@link Http2FrameUnmarshaller} classes.
 */
public abstract class AbstractHttp2FrameUnmarshaller implements Http2FrameUnmarshaller {
  private Http2FrameHeader header;

  @Override
  public final Http2FrameUnmarshaller unmarshall(Http2FrameHeader header) throws Http2Exception {
    if (header == null) {
      throw new IllegalArgumentException("header must be non-null.");
    }

    validate(header);
    this.header = header;
    return this;
  }

  @Override
  public final Http2Frame from(ByteBuf payload, ByteBufAllocator alloc) throws Http2Exception {
    if (header == null) {
      throw new IllegalStateException("header must be set before calling from().");
    }

    return doUnmarshall(header, payload, alloc);
  }

  /**
   * Verifies that the given frame header is valid for the frame type(s) supported by this decoder.
   */
  protected abstract void validate(Http2FrameHeader frameHeader) throws Http2Exception;

  /**
   * Unmarshalls the frame.
   *
   * @param header the frame header
   * @param payload the payload of the frame.
   * @param alloc an allocator for new buffers
   * @return the frame
   * @throws Http2Exception thrown if any protocol error was encountered.
   */
  protected abstract Http2Frame doUnmarshall(Http2FrameHeader header, ByteBuf payload,
      ByteBufAllocator alloc) throws Http2Exception;
}
