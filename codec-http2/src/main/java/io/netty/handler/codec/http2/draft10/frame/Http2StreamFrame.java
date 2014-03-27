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

/**
 * Base interface for all frames that are associated to a stream.
 */
public interface Http2StreamFrame extends Http2Frame {
  /**
   * Gets the identifier of the associated stream.
   */
  int getStreamId();

  /**
   * Indicates whether this frame represents the last frame for the stream.
   */
  boolean isEndOfStream();
}
