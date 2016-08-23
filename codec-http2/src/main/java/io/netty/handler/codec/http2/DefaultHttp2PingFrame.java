/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

/**
 * The default {@link Http2PingFrame} implementation.
 */
@UnstableApi
public class DefaultHttp2PingFrame extends DefaultByteBufHolder implements Http2PingFrame {

    private final boolean ack;

    public DefaultHttp2PingFrame(ByteBuf content) {
        this(content, false);
    }

    /**
     * A user cannot send a ping ack, as this is done automatically when a ping is received.
     */
    DefaultHttp2PingFrame(ByteBuf content, boolean ack) {
        super(mustBeEightBytes(content));
        this.ack = ack;
    }

    @Override
    public boolean ack() {
        return ack;
    }

    @Override
    public String name() {
        return "PING";
    }

    @Override
    public DefaultHttp2PingFrame copy() {
        return new DefaultHttp2PingFrame(content().copy(), ack);
    }

    @Override
    public DefaultHttp2PingFrame duplicate() {
        return (DefaultHttp2PingFrame) super.duplicate();
    }

    @Override
    public DefaultHttp2PingFrame retainedDuplicate() {
        return (DefaultHttp2PingFrame) super.retainedDuplicate();
    }

    @Override
    public DefaultHttp2PingFrame replace(ByteBuf content) {
        return new DefaultHttp2PingFrame(content, ack);
    }

    @Override
    public DefaultHttp2PingFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public DefaultHttp2PingFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public DefaultHttp2PingFrame touch() {
        super.touch();
        return this;
    }

    @Override
    public DefaultHttp2PingFrame touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Http2PingFrame)) {
            return false;
        }
        Http2PingFrame other = (Http2PingFrame) o;
        return super.equals(o) && ack == other.ack();
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + (ack ? 1 : 0);
        return hash;
    }

    private static ByteBuf mustBeEightBytes(ByteBuf content) {
        ObjectUtil.checkNotNull(content, "content must not be null.");
        if (content.readableBytes() != 8) {
            throw new IllegalArgumentException("PING frames require 8 bytes of content. Was " +
                                               content.readableBytes() + " bytes.");
        }
        return content;
    }

    @Override
    public String toString() {
        return "DefaultHttp2PingFrame(content=" + contentToString() + ", ack=" + ack + ')';
    }
}
