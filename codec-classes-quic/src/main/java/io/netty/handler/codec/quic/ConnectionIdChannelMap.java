/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.quic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * We use a custom hash that uses SipHash 1-3 to prevent
 * <a href="https://github.com/ncc-pbottine/QUIC-Hash-Dos-Advisory">Hash Denial-of-Service Attacks</a>.
 */
final class ConnectionIdChannelMap {
    private static final SecureRandom random = new SecureRandom();

    private final Map<ConnectionIdKey, QuicheQuicChannel> channelMap = new HashMap<>();
    private final SipHash sipHash;

    ConnectionIdChannelMap() {
        byte[] seed = new byte[SipHash.SEED_LENGTH];
        random.nextBytes(seed);
        // Use SipHash 1-3 for now which is also what rust is using by default.
        sipHash = new SipHash(1, 3, seed);
    }

    private ConnectionIdKey key(ByteBuffer cid) {
        long hash = sipHash.macHash(cid);
        return new ConnectionIdKey(hash, cid);
    }

    @Nullable
    QuicheQuicChannel put(ByteBuffer cid, QuicheQuicChannel channel) {
        return channelMap.put(key(cid), channel);
    }

    @Nullable
    QuicheQuicChannel remove(ByteBuffer cid) {
        return channelMap.remove(key(cid));
    }

    @Nullable
    QuicheQuicChannel get(ByteBuffer cid) {
        return channelMap.get(key(cid));
    }

    void clear() {
        channelMap.clear();
    }

    private static final class ConnectionIdKey implements Comparable<ConnectionIdKey> {
        private final long hash;
        private final ByteBuffer key;

        ConnectionIdKey(long hash, ByteBuffer key) {
            this.hash = hash;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConnectionIdKey that = (ConnectionIdKey) o;
            return hash == that.hash && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return (int) hash;
        }

        @Override
        public int compareTo(@NotNull ConnectionIdChannelMap.ConnectionIdKey o) {
            int result = Long.compare(hash, o.hash);
            return result != 0 ? result : key.compareTo(o.key);
        }
    }
}
