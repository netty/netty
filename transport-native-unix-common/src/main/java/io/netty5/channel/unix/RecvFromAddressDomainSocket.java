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
package io.netty5.channel.unix;

import io.netty5.buffer.api.WritableComponent;
import io.netty5.buffer.api.WritableComponentProcessor;
import io.netty5.util.internal.UnstableApi;

import java.io.IOException;

/**
 * This class facilitates datagram reads from domain sockets, into {@linkplain io.netty5.buffer.api.Buffer buffers}.
 * <p>
 * <strong>Internal usage only!</strong>
 */
@UnstableApi
public final class RecvFromAddressDomainSocket implements WritableComponentProcessor<IOException> {
    private final Socket socket;
    private DomainDatagramSocketAddress remoteAddress;

    public RecvFromAddressDomainSocket(Socket socket) {
        this.socket = socket;
    }

    @Override
    public boolean process(int index, WritableComponent component) throws IOException {
        remoteAddress = socket.recvFromAddressDomainSocket(
                component.writableNativeAddress(), 0, component.writableBytes());
        return false;
    }

    public DomainDatagramSocketAddress remoteAddress() {
        return remoteAddress;
    }
}
