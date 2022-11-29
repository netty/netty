/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core.events;

import io.netty.channel.Channel;

/**
 * This event is fired when an {@link Channel} is closed.
 */
public final class ChannelClosedEvent {

    public static final ChannelClosedEvent INSTANCE = new ChannelClosedEvent();

    private ChannelClosedEvent() {
        // Prevent outside initialization
    }
}
