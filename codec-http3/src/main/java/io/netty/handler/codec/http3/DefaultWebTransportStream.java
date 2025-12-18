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
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ObjectUtil;

/**
 * WebTransportStream 的默认实现。
 */
public class DefaultWebTransportStream implements WebTransportStream {

    private final Channel channel;
    private final long streamId;

    public DefaultWebTransportStream(Channel channel, long streamId) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        this.streamId = streamId;
    }

    @Override
    public long streamId() {
        return streamId;
    }

    @Override
    public ChannelFuture write(ByteBuf buf) {
        return channel.write(buf);
    }

    @Override
    public ChannelFuture write(ByteBuf buf, ChannelPromise promise) {
        return channel.write(buf, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(ByteBuf buf) {
        return channel.writeAndFlush(buf);
    }

    @Override
    public ChannelFuture writeAndFlush(ByteBuf buf, ChannelPromise promise) {
        return channel.writeAndFlush(buf, promise);
    }

    @Override
    public ChannelFuture close() {
        return channel.close();
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return channel.close(promise);
    }

    @Override
    public Future<Void> closeFuture() {
        return channel.closeFuture();
    }
}
