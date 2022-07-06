/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec;

import io.netty5.channel.ChannelPipeline;

/**
 * An application-level event propagated via an {@link ChannelPipeline}, such as a TLS or WebSocket handshake event.
 */
public interface ProtocolEvent {

    /**
     * Returns {@code true} if the event was sent because of some successful protocol event.
     *
     * @return {@code true} when success, {@code false} otherwise.
     */
    default boolean isSuccess() {
        return cause() == null;
    }

    /**
     * Returns {@code true} if the event was sent because of some unsuccessful protocol event.
     *
     * @return {@code true} when unsuccessful, {@code false} otherwise.
     */
    default boolean isFailed() {
        return cause() != null;
    }

    /**
     * Returns the cause of the failure, or {@code null} if {@link #isSuccess()}.
     *
     * @return the {@link Throwable} if {@link #isFailed()}  or {@code null} if {@link #isSuccess()}.
     */
    Throwable cause();
}
