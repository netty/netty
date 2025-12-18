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
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

/**
 * 可插拔的 WebTransport 观测接口，用于收集会话、流、数据报的事件。
 */
public interface WebTransportObserver {

    /**
     * 当新的 WebTransport 会话建立时调用。
     * @param session 建立的会话
     */
    void onSessionCreated(WebTransportSession session);

    /**
     * 当 WebTransport 会话关闭时调用。
     * @param session 关闭的会话
     * @param future 会话关闭的 Future
     */
    void onSessionClosed(WebTransportSession session, Future<Void> future);

    /**
     * 当新的 WebTransport 流创建时调用。
     * @param stream 创建的流
     */
    void onStreamCreated(WebTransportStream stream);

    /**
     * 当 WebTransport 流关闭时调用。
     * @param stream 关闭的流
     * @param future 流关闭的 Future
     */
    void onStreamClosed(WebTransportStream stream, Future<Void> future);

    /**
     * 当接收到 WebTransport 数据报时调用。
     * @param session 数据报所属的会话
     * @param datagram 接收到的数据报
     */
    void onDatagramReceived(WebTransportSession session, WebTransportDatagram datagram);

    /**
     * 当发送 WebTransport 数据报时调用。
     * @param session 数据报所属的会话
     * @param datagram 发送的数据报
     */
    void onDatagramSent(WebTransportSession session, WebTransportDatagram datagram);

    /**
     * 当发生错误时调用。
     * @param channel 发生错误的通道
     * @param cause 错误原因
     */
    void onError(Channel channel, Throwable cause);
}
