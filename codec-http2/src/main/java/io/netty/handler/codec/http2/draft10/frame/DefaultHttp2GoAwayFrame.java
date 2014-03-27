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
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_UNSIGNED_INT;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;

/**
 * Default implementation of {@link Http2GoAwayFrame}.
 */
public final class DefaultHttp2GoAwayFrame extends DefaultByteBufHolder implements
    Http2GoAwayFrame {
  private final int lastStreamId;
  private final long errorCode;

  private DefaultHttp2GoAwayFrame(Builder builder) {
    super(builder.debugData);
    this.lastStreamId = builder.lastStreamId;
    this.errorCode = builder.errorCode;
  }

  @Override
  public int getLastStreamId() {
    return lastStreamId;
  }

  @Override
  public long getErrorCode() {
    return errorCode;
  }

  @Override
  public DefaultHttp2GoAwayFrame copy() {
    return copyBuilder().setDebugData(content().copy()).build();
  }

  @Override
  public DefaultHttp2GoAwayFrame duplicate() {
    return copyBuilder().setDebugData(content().duplicate()).build();
  }

  @Override
  public DefaultHttp2GoAwayFrame retain() {
    super.retain();
    return this;
  }

  @Override
  public DefaultHttp2GoAwayFrame retain(int increment) {
    super.retain(increment);
    return this;
  }

  @Override
  public DefaultHttp2GoAwayFrame touch() {
    super.touch();
    return this;
  }

  @Override
  public DefaultHttp2GoAwayFrame touch(Object hint) {
    super.touch(hint);
    return this;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = content().hashCode();
    result = prime * result + (int) (errorCode ^ (errorCode >>> 32));
    result = prime * result + lastStreamId;
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
    DefaultHttp2GoAwayFrame other = (DefaultHttp2GoAwayFrame) obj;
    if (errorCode != other.errorCode) {
      return false;
    }
    if (lastStreamId != other.lastStreamId) {
      return false;
    }
    if (!content().equals(other.content())) {
      return false;
    }
    return true;
  }

  private Builder copyBuilder() {
    return new Builder().setErrorCode(errorCode).setLastStreamId(lastStreamId);
  }

  /**
   * Builds instances of {@link DefaultHttp2GoAwayFrame}.
   */
  public static class Builder {
    private int lastStreamId = -1;
    private long errorCode = -1;
    private ByteBuf debugData = Unpooled.EMPTY_BUFFER;

    public Builder setLastStreamId(int lastStreamId) {
      if (lastStreamId < 0) {
        throw new IllegalArgumentException("Invalid lastStreamId.");
      }
      this.lastStreamId = lastStreamId;
      return this;
    }

    public Builder setErrorCode(long errorCode) {
      if (errorCode < 0 || errorCode > MAX_UNSIGNED_INT) {
        throw new IllegalArgumentException("Invalid error code.");
      }
      this.errorCode = errorCode;
      return this;
    }

    public Builder setDebugData(ByteBuf debugData) {
      if (debugData == null) {
        throw new IllegalArgumentException("debugData must not be null");
      }
      if (debugData.readableBytes() > MAX_FRAME_PAYLOAD_LENGTH - 8) {
        throw new IllegalArgumentException("Invalid debug data size.");
      }

      this.debugData = debugData;
      return this;
    }

    public DefaultHttp2GoAwayFrame build() {
      if (lastStreamId < 0) {
        throw new IllegalArgumentException("LastStreamId must be set");
      }
      if (errorCode < 0) {
        throw new IllegalArgumentException("ErrorCode must be set.");
      }

      return new DefaultHttp2GoAwayFrame(this);
    }
  }
}
