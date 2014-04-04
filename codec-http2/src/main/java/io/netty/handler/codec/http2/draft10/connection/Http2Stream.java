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

package io.netty.handler.codec.http2.draft10.connection;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.draft10.Http2Error;
import io.netty.handler.codec.http2.draft10.Http2Exception;

/**
 * A single stream within an HTTP2 connection. Streams are compared to each other by priority.
 */
public interface Http2Stream extends Comparable<Http2Stream> {

  /**
   * The allowed states of an HTTP2 stream.
   */
  enum State {
    IDLE, RESERVED_LOCAL, RESERVED_REMOTE, OPEN, HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, CLOSED;
  }

  /**
   * Gets the unique identifier for this stream within the connection.
   */
  int getId();

  /**
   * Gets the state of this stream.
   */
  State getState();

  /**
   * Verifies that the stream is in one of the given allowed states.
   */
  void verifyState(Http2Error error, State... allowedStates) throws Http2Exception;

  /**
   * Sets the priority of this stream. A value of zero is the highest priority and a value of
   * {@link Integer#MAX_VALUE} is the lowest.
   */
  void setPriority(int priority) throws Http2Exception;

  /**
   * Gets the priority of this stream. A value of zero is the highest priority and a value of
   * {@link Integer#MAX_VALUE} is the lowest.
   */
  int getPriority();

  /**
   * If this is a reserved push stream, opens the stream for push in one direction.
   */
  void openForPush() throws Http2Exception;

  /**
   * Closes the stream.
   */
  void close(ChannelHandlerContext ctx, ChannelFuture future);

  /**
   * Closes the local side of this stream. If this makes the stream closed, the child is closed as
   * well.
   */
  void closeLocalSide(ChannelHandlerContext ctx, ChannelFuture future);

  /**
   * Closes the remote side of this stream. If this makes the stream closed, the child is closed as
   * well.
   */
  void closeRemoteSide(ChannelHandlerContext ctx, ChannelFuture future);

  /**
   * Indicates whether the remote side of this stream is open (i.e. the state is either
   * {@link State#OPEN} or {@link State#HALF_CLOSED_LOCAL}).
   */
  boolean isRemoteSideOpen();

  /**
   * Indicates whether the local side of this stream is open (i.e. the state is either
   * {@link State#OPEN} or {@link State#HALF_CLOSED_REMOTE}).
   */
  boolean isLocalSideOpen();
}
