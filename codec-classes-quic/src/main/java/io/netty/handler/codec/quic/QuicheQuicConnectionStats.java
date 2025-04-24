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
package io.netty.handler.codec.quic;

import io.netty.util.internal.StringUtil;

final class QuicheQuicConnectionStats implements QuicConnectionStats {

    private final long[] values;

    QuicheQuicConnectionStats(long[] values) {
        this.values = values;
    }

    @Override
    public long recv() {
        return values[0];
    }

    @Override
    public long sent() {
        return values[1];
    }

    @Override
    public long lost() {
        return values[2];
    }

    @Override
    public long retrans() {
        return values[3];
    }

    @Override
    public long sentBytes() {
        return values[4];
    }

    @Override
    public long recvBytes() {
        return values[5];
    }

    @Override
    public long lostBytes() {
        return values[6];
    }

    @Override
    public long streamRetransBytes() {
        return values[7];
    }

    @Override
    public long pathsCount() {
        return values[8];
    }

    /**
     * Returns the {@link String} representation of stats.
     */
    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[" +
                "recv=" + recv() +
                ", sent=" + sent() +
                ", lost=" + lost() +
                ", retrans=" + retrans() +
                ", sentBytes=" + sentBytes() +
                ", recvBytes=" + recvBytes() +
                ", lostBytes=" + lostBytes() +
                ", streamRetransBytes=" + streamRetransBytes() +
                ", pathsCount=" + pathsCount() +
                "]";
    }

}
