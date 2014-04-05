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
 * HTTP2 SETTINGS frame providing configuration parameters that affect how endpoints communicate.
 */
public interface Http2SettingsFrame extends Http2Frame {

    /**
     * Indicates whether this is an acknowledgment of the settings sent by the peer.
     */
    boolean isAck();

    /**
     * Gets the sender's header compression table size, or {@code null} if not set.
     */
    Integer getHeaderTableSize();

    /**
     * Gets whether or not the sender allows server push, or {@code null} if not set.
     */
    Boolean getPushEnabled();

    /**
     * Gets the maximum number of streams the receiver is allowed to create, or {@code null} if not
     * set.
     */
    Long getMaxConcurrentStreams();

    /**
     * Gets the sender's initial flow control window in bytes, or {@code null} if not set.
     *
     * @return
     */
    Integer getInitialWindowSize();
}
