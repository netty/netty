/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.timeout;

import io.netty.channel.Channel;

/**
 * A user event triggered by {@link IdleStateHandler} when a {@link Channel} is idle.
 */
public final class IdleStateEvent {
    private final IdleState state;
    private final boolean first;
    private final IdleStateHandler source;

    IdleStateEvent(IdleState state, boolean first, IdleStateHandler source) {
        this.state = state;
        this.first = first;
        this.source = source;
    }

    /**
     * Returns the idle state.
     */
    public IdleState state() {
        return state;
    }

    /**
     * Returns {@code true} if this was the first event for the {@link IdleState}
     */
    public boolean isFirst() {
        return first;
    }

    /**
     * Returns the {@link IdleStateHandler} that triggered this event.
     *
     * @return the {@code IdleStateHandler} that triggered this event
     */
    public IdleStateHandler source() {
        return source;
    }
}
