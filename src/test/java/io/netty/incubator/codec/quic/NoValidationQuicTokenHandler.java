/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.quic;

import java.net.InetSocketAddress;

import io.netty.buffer.ByteBuf;

/**
 * A {@link QuicTokenHandler} which disables token validation.
 */
public final class NoValidationQuicTokenHandler implements QuicTokenHandler {

    private NoValidationQuicTokenHandler() {
    }

    public static final NoValidationQuicTokenHandler INSTANCE = new NoValidationQuicTokenHandler();

    @Override
    public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
        return false;
    }

    @Override
    public int validateToken(ByteBuf token, InetSocketAddress address) {
        return 0;
    }

    @Override
    public int maxTokenLength() {
        return 0;
    }
}
