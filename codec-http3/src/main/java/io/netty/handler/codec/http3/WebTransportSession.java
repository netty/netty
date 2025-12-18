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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;

/**
 * WebTransport 会话的高层抽象，封装 HTTP/3 CONNECT WebTransport 细节。
 */
public interface WebTransportSession {

    /**
     * 获取会话的 ID。
     * @return 会话的 ID
     */
    String sessionId();

    /**
     * 创建一个新的双向流。
     * @return 新创建的流的 Future
     */
    Future<WebTransportStream> createBidirectionalStream();

    /**
     * 创建一个新的单向流。
     * @return 新创建的流的 Future
     */
    Future<WebTransportStream> createUnidirectionalStream();

    /**
     * 发送数据报。
     * @param datagram 要发送的数据报
     * @return 发送操作的 Future
     */
    ChannelFuture sendDatagram(ByteBuf datagram);

    /**
     * 发送数据报，并使用指定的 Promise。
     * @param datagram 要发送的数据报
     * @param promise 发送操作的 Promise
     * @return 发送操作的 Future
     */
    ChannelFuture sendDatagram(ByteBuf datagram, ChannelPromise promise);

    /**
     * 关闭会话。
     * @return 关闭操作的 Future
     */
    ChannelFuture close();

    /**
     * 关闭会话，并使用指定的 Promise。
     * @param promise 关闭操作的 Promise
     * @return 关闭操作的 Future
     */
    ChannelFuture close(ChannelPromise promise);

    /**
     * 监听会话的关闭事件。
     * @return 会话关闭的 Future
     */
    Future<Void> closeFuture();

    /**
     * 获取会话的观测者。
     * @return 会话的观测者
     */
    WebTransportObserver observer();

    /**
     * 用于存储 WebTransportSession 的通道属性键。
     */
    AttributeKey<WebTransportSession> SESSION_KEY = AttributeKey.newInstance("webtransport.session");
}
