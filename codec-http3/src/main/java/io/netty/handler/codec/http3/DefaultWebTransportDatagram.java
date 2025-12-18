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
import io.netty.util.internal.ObjectUtil;

/**
 * WebTransportDatagram 的默认实现。
 */
public class DefaultWebTransportDatagram implements WebTransportDatagram {

    private final Channel channel;
    private final ByteBuf content;

    public DefaultWebTransportDatagram(Channel channel, ByteBuf content) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        this.content = ObjectUtil.checkNotNull(content, "content");
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public WebTransportDatagram copy() {
        return new DefaultWebTransportDatagram(channel, content.copy());
    }

    @Override
    public WebTransportDatagram duplicate() {
        return new DefaultWebTransportDatagram(channel, content.duplicate());
    }

    @Override
    public WebTransportDatagram retainedDuplicate() {
        return new DefaultWebTransportDatagram(channel, content.retainedDuplicate());
    }

    @Override
    public WebTransportDatagram replace(ByteBuf content) {
        return new DefaultWebTransportDatagram(channel, content);
    }

    @Override
    public WebTransportDatagram retain() {
        content.retain();
        return this;
    }

    @Override
    public WebTransportDatagram retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public WebTransportDatagram touch() {
        content.touch();
        return this;
    }

    @Override
    public WebTransportDatagram touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(int decrement) {
        return content.release(decrement);
    }
}
