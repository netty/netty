/*
 * Copyright 2024 The Netty Project
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

import java.net.InetSocketAddress;

final class QuicheQuicConnectionPathStats implements QuicConnectionPathStats {

    private final Object[] values;

    QuicheQuicConnectionPathStats(Object[] values) {
        this.values = values;
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) values[0];
    }

    @Override
    public InetSocketAddress peerAddress() {
        return (InetSocketAddress) values[1];
    }

    public long validationState() {
        return (long) values[2];
    }

    @Override
    public boolean active() {
        return (boolean) values[3];
    }

    @Override
    public long recv() {
        return (long) values[4];
    }

    @Override
    public long sent() {
        return (long) values[5];
    }

    @Override
    public long lost() {
        return (long) values[6];
    }

    @Override
    public long retrans() {
        return (long) values[7];
    }

    @Override
    public long rtt() {
        return (long) values[8];
    }

    @Override
    public long cwnd() {
        return (long) values[9];
    }

    @Override
    public long sentBytes() {
        return (long) values[10];
    }

    @Override
    public long recvBytes() {
        return (long) values[11];
    }

    @Override
    public long lostBytes() {
        return (long) values[12];
    }

    @Override
    public long streamRetransBytes() {
        return (long) values[13];
    }

    @Override
    public long pmtu() {
        return (long) values[14];
    }

    @Override
    public long deliveryRate() {
        return (long) values[15];
    }

    /**
     * Returns the {@link String} representation of stats.
     */
    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[" +
                "local=" + localAddress() +
                ", peer=" + peerAddress() +
                ", validationState=" + validationState() +
                ", active=" + active() +
                ", recv=" + recv() +
                ", sent=" + sent() +
                ", lost=" + lost() +
                ", retrans=" + retrans() +
                ", rtt=" + rtt() +
                ", cwnd=" + cwnd() +
                ", sentBytes=" + sentBytes() +
                ", recvBytes=" + recvBytes() +
                ", lostBytes=" + lostBytes() +
                ", streamRetransBytes=" + streamRetransBytes() +
                ", pmtu=" + pmtu() +
                ", deliveryRate=" + deliveryRate() +
                ']';
    }

}
