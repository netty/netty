/*
 * Copyright 2020 The Netty Project
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

import java.net.SocketAddress;
import java.security.SecureRandom;

public final class QuicConnectionIdAddress extends SocketAddress {
    private static final SecureRandom random = new SecureRandom();

    final byte[] connId;

    private QuicConnectionIdAddress(byte[] connId) {
        this.connId = connId.clone();
    }

    public static QuicConnectionIdAddress random() {
        byte[] bytes = new byte[Quiche.QUICHE_MAX_CONN_ID_LEN];
        random.nextBytes(bytes);
        return new QuicConnectionIdAddress(bytes);
    }

    public static QuicConnectionIdAddress fromBytes(byte[] bytes) {
        return new QuicConnectionIdAddress(bytes.clone());
    }
}
