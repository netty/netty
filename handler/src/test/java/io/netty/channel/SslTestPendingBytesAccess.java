/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

/** Test-only access to pending outbound byte accounting. */
public final class SslTestPendingBytesAccess {
    private SslTestPendingBytesAccess() { }

    public static void increment(Channel channel, long bytes) {
        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer == null) {
            throw new IllegalStateException("channel has no outbound buffer");
        }
        buffer.incrementPendingOutboundBytes(bytes);
    }

    public static void decrement(Channel channel, long bytes) {
        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer == null) {
            throw new IllegalStateException("channel has no outbound buffer");
        }
        buffer.decrementPendingOutboundBytes(bytes);
    }
}
