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

package io.netty.handler.codec.http2.draft10.frame;

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_UNSIGNED_SHORT;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;

/**
 * Default implementation of {@link Http2DataFrame}.
 */
public final class DefaultHttp2DataFrame extends DefaultByteBufHolder implements Http2DataFrame {

  private final int paddingLength;
  private final int streamId;
  private final boolean endOfStream;

  private DefaultHttp2DataFrame(Builder builder) {
    super(builder.content);
    this.streamId = builder.streamId;
    this.endOfStream = builder.endOfStream;
    this.paddingLength = builder.paddingLength;
  }

  @Override
  public int getStreamId() {
    return streamId;
  }

  @Override
  public boolean isEndOfStream() {
    return endOfStream;
  }

  @Override
  public int getPaddingLength() {
    return paddingLength;
  }

  @Override
  public DefaultHttp2DataFrame copy() {
    return copyBuilder().setContent(content().copy()).build();
  }

  @Override
  public DefaultHttp2DataFrame duplicate() {
    return copyBuilder().setContent(content().duplicate()).build();
  }

  @Override
  public DefaultHttp2DataFrame retain() {
    super.retain();
    return this;
  }

  @Override
  public DefaultHttp2DataFrame retain(int increment) {
    super.retain(increment);
    return this;
  }

  @Override
  public DefaultHttp2DataFrame touch() {
    super.touch();
    return this;
  }

  @Override
  public DefaultHttp2DataFrame touch(Object hint) {
    super.touch(hint);
    return this;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = content().hashCode();
    result = prime * result + (endOfStream ? 1231 : 1237);
    result = prime * result + paddingLength;
    result = prime * result + streamId;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DefaultHttp2DataFrame other = (DefaultHttp2DataFrame) obj;
    if (endOfStream != other.endOfStream) {
      return false;
    }
    if (paddingLength != other.paddingLength) {
      return false;
    }
    if (streamId != other.streamId) {
      return false;
    }
    if (!content().equals(other.content())) {
      return false;
    }
    return true;
  }

  private Builder copyBuilder() {
    return new Builder().setStreamId(streamId).setPaddingLength(paddingLength)
        .setEndOfStream(endOfStream);
  }

  /**
   * Builds instances of {@link DefaultHttp2DataFrame}.
   */
  public static class Builder {
    private int streamId;
    private boolean endOfStream;
    private ByteBuf content = Unpooled.EMPTY_BUFFER;
    private int paddingLength;

    public Builder setStreamId(int streamId) {
      if (streamId <= 0) {
        throw new IllegalArgumentException("StreamId must be > 0.");
      }
      this.streamId = streamId;
      return this;
    }

    public Builder setEndOfStream(boolean endOfStream) {
      this.endOfStream = endOfStream;
      return this;
    }

    /**
     * Sets the content for the data frame, excluding any padding. This buffer will be retained when
     * the frame is built.
     */
    public Builder setContent(ByteBuf content) {
      if (content == null) {
        throw new IllegalArgumentException("content must not be null");
      }
      verifyLength(paddingLength, content);
      this.content = content;
      return this;
    }

    public Builder setPaddingLength(int paddingLength) {
      if (paddingLength < 0 || paddingLength > MAX_UNSIGNED_SHORT) {
        throw new IllegalArgumentException("Padding length invalid.");
      }
      verifyLength(paddingLength, content);
      this.paddingLength = paddingLength;
      return this;
    }

    public DefaultHttp2DataFrame build() {
      if (streamId <= 0) {
        throw new IllegalArgumentException("StreamId must be set.");
      }

      verifyLength(paddingLength, content);

      return new DefaultHttp2DataFrame(this);
    }

    private void verifyLength(int paddingLength, ByteBuf data) {
      int maxLength = MAX_FRAME_PAYLOAD_LENGTH;
      maxLength -= paddingLength;
      if (data.readableBytes() > maxLength) {
        throw new IllegalArgumentException("Header block fragment length too big.");
      }
    }
  }
}
