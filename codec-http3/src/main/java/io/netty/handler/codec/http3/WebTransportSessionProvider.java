/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

/**
 * WebTransportSession 的提供者，用于从通道中获取 WebTransportSession。
 */
public interface WebTransportSessionProvider {

    /**
     * 从通道中获取 WebTransportSession。
     * @param channel 通道
     * @return WebTransportSession，如果不存在则返回 null
     */
    static WebTransportSession getSession(Channel channel) {
        return channel.attr(WebTransportSession.SESSION_KEY).get();
    }

    /**
     * 从通道中获取 WebTransportSession，并确保会话存在。
     * @param channel 通道
     * @return WebTransportSession
     * @throws IllegalStateException 如果会话不存在
     */
    static WebTransportSession requireSession(Channel channel) {
        WebTransportSession session = getSession(channel);
        if (session == null) {
            throw new IllegalStateException("WebTransportSession not found in channel");
        }
        return session;
    }

    /**
     * 从通道上下文获取 WebTransportSession。
     * @param ctx 通道上下文
     * @return WebTransportSession，如果不存在则返回 null
     */
    static WebTransportSession getSession(ChannelHandlerContext ctx) {
        return getSession(ctx.channel());
    }

    /**
     * 从通道上下文获取 WebTransportSession，并确保会话存在。
     * @param ctx 通道上下文
     * @return WebTransportSession
     * @throws IllegalStateException 如果会话不存在
     */
    static WebTransportSession requireSession(ChannelHandlerContext ctx) {
        return requireSession(ctx.channel());
    }
}
