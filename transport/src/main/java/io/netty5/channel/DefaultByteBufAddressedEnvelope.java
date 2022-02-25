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
package io.netty5.channel;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufHolder;

import java.net.SocketAddress;

/**
 * The default {@link AddressedEnvelope} implementation for {@link ByteBuf} messages.
 *
 * @param <A> the type of the recipient and sender addresses.
 */
public class DefaultByteBufAddressedEnvelope<A extends SocketAddress>
        extends DefaultAddressedEnvelope<ByteBuf, A>
        implements ByteBufHolder {
    /**
     * Creates a new instance with the specified {@code message}, {@code recipient} address, and
     * {@code sender} address.
     */
    public DefaultByteBufAddressedEnvelope(ByteBuf message, A recipient, A sender) {
        super(message, recipient, sender);
    }

    /**
     * Creates a new instance with the specified {@code message} and {@code recipient} address.
     * The sender address becomes {@code null}.
     */
    public DefaultByteBufAddressedEnvelope(ByteBuf message, A recipient) {
        super(message, recipient);
    }

    @Override
    public ByteBufHolder copy() {
        return new DefaultByteBufAddressedEnvelope<A>(content().copy(), recipient(), sender());
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public DefaultByteBufAddressedEnvelope<A> retain() {
        content().retain();
        return this;
    }

    @Override
    public DefaultByteBufAddressedEnvelope<A> retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public boolean release() {
        return content().release();
    }

    @Override
    public boolean release(int decrement) {
        return content().release(decrement);
    }

    @Override
    public DefaultByteBufAddressedEnvelope<A> touch() {
        content().touch();
        return this;
    }

    @Override
    public DefaultByteBufAddressedEnvelope<A> touch(Object hint) {
        content().touch(hint);
        return this;
    }

    @Override
    public DefaultByteBufAddressedEnvelope<A> duplicate() {
        return new DefaultByteBufAddressedEnvelope<A>(content().duplicate(), recipient(), sender());
    }

    @Override
    public DefaultByteBufAddressedEnvelope<A> retainedDuplicate() {
        return new DefaultByteBufAddressedEnvelope<A>(content().retainedDuplicate(), recipient(), sender());
    }

    @Override
    public DefaultByteBufAddressedEnvelope<A> replace(ByteBuf content) {
        return new DefaultByteBufAddressedEnvelope<>(content, recipient(), sender());
    }
}
