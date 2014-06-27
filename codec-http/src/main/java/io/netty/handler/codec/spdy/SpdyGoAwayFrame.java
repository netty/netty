/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

/**
 * A SPDY Protocol GOAWAY Frame
 */
public interface SpdyGoAwayFrame extends SpdyFrame {

    /**
     * Returns the Last-good-stream-ID of this frame.
     */
    int lastGoodStreamId();

    /**
     * Sets the Last-good-stream-ID of this frame.  The Last-good-stream-ID
     * cannot be negative.
     */
    SpdyGoAwayFrame setLastGoodStreamId(int lastGoodStreamId);

    /**
     * Returns the status of this frame.
     */
    SpdySessionStatus status();

    /**
     * Sets the status of this frame.
     */
    SpdyGoAwayFrame setStatus(SpdySessionStatus status);
}
