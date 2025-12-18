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
import io.netty.util.concurrent.Future;

/**
 * WebTransport 流的高层抽象，提供流的读写操作。
 */
public interface WebTransportStream {

    /**
     * 获取流的 ID。
     * @return 流的 ID
     */
    long streamId();

    /**
     * 写入数据到流。
     * @param buf 要写入的数据
     * @return 写入操作的 Future
     */
    ChannelFuture write(ByteBuf buf);

    /**
     * 写入数据到流，并使用指定的 Promise。
     * @param buf 要写入的数据
     * @param promise 写入操作的 Promise
     * @return 写入操作的 Future
     */
    ChannelFuture write(ByteBuf buf, ChannelPromise promise);

    /**
     * 写入数据到流并立即刷新。
     * @param buf 要写入的数据
     * @return 写入操作的 Future
     */
    ChannelFuture writeAndFlush(ByteBuf buf);

    /**
     * 写入数据到流并立即刷新，使用指定的 Promise。
     * @param buf 要写入的数据
     * @param promise 写入操作的 Promise
     * @return 写入操作的 Future
     */
    ChannelFuture writeAndFlush(ByteBuf buf, ChannelPromise promise);

    /**
     * 关闭流。
     * @return 关闭操作的 Future
     */
    ChannelFuture close();

    /**
     * 关闭流，并使用指定的 Promise。
     * @param promise 关闭操作的 Promise
     * @return 关闭操作的 Future
     */
    ChannelFuture close(ChannelPromise promise);

    /**
     * 监听流的关闭事件。
     * @return 流关闭的 Future
     */
    Future<Void> closeFuture();
}
