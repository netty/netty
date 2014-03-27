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
 * An HTTP2 GO_AWAY frame indicating that the remote peer should stop creating streams for the
 * connection.
 */
public interface Http2GoAwayFrame extends Http2Frame, ByteBufHolder {
  /**
   * The highest numbered stream identifier for which the sender of the GOAWAY frame has received
   * frames on and might have taken some action on.
   */
  int getLastStreamId();

  /**
   * The error code containing the reason for closing the connection.
   */
  long getErrorCode();

  /**
   * Returns the debug data.
   */
  @Override
  ByteBuf content();

  @Override
  Http2GoAwayFrame copy();

  @Override
  Http2GoAwayFrame duplicate();

  @Override
  Http2GoAwayFrame retain();

  @Override
  Http2GoAwayFrame retain(int increment);

  @Override
  Http2GoAwayFrame touch();

  @Override
  Http2GoAwayFrame touch(Object hint);
}
