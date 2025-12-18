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
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.util.ReferenceCounted;

/**
 * WebTransport 数据报的高层抽象。
 */
public interface WebTransportDatagram extends ByteBufHolder {

    /**
     * 获取数据报的有效负载。
     * @return 数据报的有效负载
     */
    ByteBuf content();

    /**
     * 获取数据报的源通道。
     * @return 数据报的源通道
     */
    Channel channel();

    /**
     * 释放数据报及其持有的资源。
     */
    @Override
    WebTransportDatagram copy();

    /**
     * 复制数据报，但共享底层内容。
     */
    @Override
    WebTransportDatagram duplicate();

    /**
     * 复制数据报，但共享底层内容，并设置新的读取索引和写入索引。
     */
    @Override
    WebTransportDatagram retainedDuplicate();

    /**
     * 复制数据报，但增加引用计数。
     */
    @Override
    WebTransportDatagram replace(ByteBuf content);

    /**
     * 增加引用计数。
     */
    @Override
    WebTransportDatagram retain();

    /**
     * 增加引用计数指定次数。
     */
    @Override
    WebTransportDatagram retain(int increment);

    /**
     * 接触自动释放。
     */
    @Override
    WebTransportDatagram touch();

    /**
     * 接触自动释放并附加用户对象。
     */
    @Override
    WebTransportDatagram touch(Object hint);
}
