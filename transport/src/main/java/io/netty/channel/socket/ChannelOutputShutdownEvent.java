/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel.socket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.util.internal.UnstableApi;

/**
 * Special event which will be fired and passed to the
 * {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)} methods once the output of
 * a {@link SocketChannel} was shutdown.
 */
@UnstableApi
public final class ChannelOutputShutdownEvent {
    public static final ChannelOutputShutdownEvent INSTANCE = new ChannelOutputShutdownEvent();

    private ChannelOutputShutdownEvent() {
    }
}
