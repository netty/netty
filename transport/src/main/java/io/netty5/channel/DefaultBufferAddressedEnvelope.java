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

import io.netty5.buffer.api.Buffer;

import java.net.SocketAddress;

/**
 * The default {@link AddressedEnvelope} implementation for {@link Buffer} messages.
 *
 * @param <A> the type of the recipient and sender addresses.
 */
public class DefaultBufferAddressedEnvelope<A extends SocketAddress>
        extends BufferAddressedEnvelope<A, DefaultBufferAddressedEnvelope<A>> {
    /**
     * Creates a new instance with the specified {@code message}, {@code recipient} address, and
     * {@code sender} address.
     */
    public DefaultBufferAddressedEnvelope(Buffer message, A recipient, A sender) {
        super(message, recipient, sender);
    }

    /**
     * Creates a new instance with the specified {@code message} and {@code recipient} address.
     * The sender address becomes {@code null}.
     */
    public DefaultBufferAddressedEnvelope(Buffer message, A recipient) {
        super(message, recipient);
    }

    @Override
    public DefaultBufferAddressedEnvelope<A> replace(Buffer content) {
        return new DefaultBufferAddressedEnvelope<>(content, recipient(), sender());
    }

    @Override
    public DefaultBufferAddressedEnvelope<A> touch(Object hint) {
        content().touch(hint);
        return this;
    }
}
