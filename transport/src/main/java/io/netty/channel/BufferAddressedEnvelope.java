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
package io.netty.channel;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.Resource;
import io.netty.buffer.api.Send;

import java.net.SocketAddress;
import java.util.function.Supplier;

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
