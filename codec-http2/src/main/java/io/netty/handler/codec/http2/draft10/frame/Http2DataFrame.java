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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;

/**
 * An HTTP2 data frame.
 */
public interface Http2DataFrame extends Http2StreamFrame, ByteBufHolder {

  /**
   * The amount of padding to follow the header data in the frame.
   */
  int getPaddingLength();

  /**
   * Returns the data payload of this frame.
   */
  @Override
  ByteBuf content();

  @Override
  Http2DataFrame copy();

  @Override
  Http2DataFrame duplicate();

  @Override
  Http2DataFrame retain();

  @Override
  Http2DataFrame retain(int increment);

  @Override
  Http2DataFrame touch();

  @Override
  Http2DataFrame touch(Object hint);
}
