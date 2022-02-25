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
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.Send;

import java.net.SocketAddress;
import java.util.function.Supplier;

/**
 * Base class for addressed envelopes that have {@link Buffer} instances as messages.
 *
 * @param <A> The type of socket address used for recipient and sender.
 * @param <T> The concrete sub-type of this class, used for implementing {@link #send()}.
 */
public abstract class BufferAddressedEnvelope<A extends SocketAddress, T extends BufferAddressedEnvelope<A, T>>
        extends DefaultAddressedEnvelope<Buffer, A>
        implements Resource<T> {
    protected BufferAddressedEnvelope(Buffer message, A recipient, A sender) {
        super(message, recipient, sender);
    }

    protected BufferAddressedEnvelope(Buffer message, A recipient) {
        super(message, recipient);
    }

    @Override
    public Send<T> send() {
        Send<Buffer> contentSend = content().send();
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) getClass();
        Supplier<T> supplier = () -> replace(contentSend.receive());
        return Send.sending(type, supplier);
    }

    /**
     * Create a new addressed envelope instance, that has the same recipient and sender as this one, but the given
     * content.
     *
     * @param content The contents of the returned addressed envelope instance.
     * @return An addressed envelope instance that has the same recipient and sender as this one, but the given content.
     */
    public abstract T replace(Buffer content);

    @Override
    public void close() {
        content().close();
    }

    @Override
    public boolean isAccessible() {
        return content().isAccessible();
    }
}
