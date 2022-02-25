/*
 * Copyright 2013 The Netty Project
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

import io.netty5.util.internal.StringUtil;

import java.net.SocketAddress;

import static java.util.Objects.requireNonNull;

/**
 * The default {@link AddressedEnvelope} implementation.
 *
 * @param <M> the type of the wrapped message
 * @param <A> the type of the recipient address
 */
public class DefaultAddressedEnvelope<M, A extends SocketAddress> implements AddressedEnvelope<M, A> {

    private final M message;
    private final A sender;
    private final A recipient;

    /**
     * Creates a new instance with the specified {@code message}, {@code recipient} address, and
     * {@code sender} address.
     */
    public DefaultAddressedEnvelope(M message, A recipient, A sender) {
        requireNonNull(message, "message");

        if (recipient == null && sender == null) {
            throw new NullPointerException("recipient and sender");
        }

        this.message = message;
        this.sender = sender;
        this.recipient = recipient;
    }

    /**
     * Creates a new instance with the specified {@code message} and {@code recipient} address.
     * The sender address becomes {@code null}.
     */
    public DefaultAddressedEnvelope(M message, A recipient) {
        this(message, recipient, null);
    }

    @Override
    public M content() {
        return message;
    }

    @Override
    public A sender() {
        return sender;
    }

    @Override
    public A recipient() {
        return recipient;
    }

    @Override
    public String toString() {
        if (sender != null) {
            return StringUtil.simpleClassName(this) +
                    '(' + sender + " => " + recipient + ", " + message + ')';
        } else {
            return StringUtil.simpleClassName(this) +
                    "(=> " + recipient + ", " + message + ')';
        }
    }
}
