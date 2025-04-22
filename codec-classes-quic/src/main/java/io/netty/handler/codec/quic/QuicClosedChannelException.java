/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.quic;

import org.jetbrains.annotations.Nullable;

import java.nio.channels.ClosedChannelException;

/**
 * Special {@link QuicClosedChannelException} which also provides extra info if the close was a result of a
 * {@link QuicConnectionCloseEvent} that was triggered by the remote peer.
 */
public final class QuicClosedChannelException extends ClosedChannelException {

    private final QuicConnectionCloseEvent event;

    QuicClosedChannelException(@Nullable QuicConnectionCloseEvent event) {
        this.event = event;
    }

    /**
     * Returns the {@link QuicConnectionCloseEvent} that caused the closure or {@code null} if none was received.
     *
     * @return the event.
     */
    @Nullable
    public QuicConnectionCloseEvent event() {
        return event;
    }
}
