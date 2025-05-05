/*
 * Copyright 2023 The Netty Project
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

final class QuicheQuicTransportParameters implements QuicTransportParameters {
    private final long[] values;

    QuicheQuicTransportParameters(long[] values) {
        this.values = values;
    }

    @Override
    public long maxIdleTimeout() {
        return values[0];
    }

    @Override
    public long maxUdpPayloadSize() {
        return values[1];
    }

    @Override
    public long initialMaxData() {
        return values[2];
    }

    @Override
    public long initialMaxStreamDataBidiLocal() {
        return values[3];
    }

    @Override
    public long initialMaxStreamDataBidiRemote() {
        return values[4];
    }

    @Override
    public long initialMaxStreamDataUni() {
        return values[5];
    }

    @Override
    public long initialMaxStreamsBidi() {
        return values[6];
    }

    @Override
    public long initialMaxStreamsUni() {
        return values[7];
    }

    @Override
    public long ackDelayExponent() {
        return values[8];
    }

    @Override
    public long maxAckDelay() {
        return values[9];
    }

    @Override
    public boolean disableActiveMigration() {
        return values[10] == 1;
    }

    @Override
    public long activeConnIdLimit() {
        return values[11];
    }

    @Override
    public long maxDatagramFrameSize() {
        return values[12];
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[" +
                "maxIdleTimeout=" + maxIdleTimeout() +
                ", maxUdpPayloadSize=" + maxUdpPayloadSize() +
                ", initialMaxData=" + initialMaxData() +
                ", initialMaxStreamDataBidiLocal=" + initialMaxStreamDataBidiLocal() +
                ", initialMaxStreamDataBidiRemote=" + initialMaxStreamDataBidiRemote() +
                ", initialMaxStreamDataUni=" + initialMaxStreamDataUni() +
                ", initialMaxStreamsBidi=" + initialMaxStreamsBidi() +
                ", initialMaxStreamsUni=" + initialMaxStreamsUni() +
                ", ackDelayExponent=" + ackDelayExponent() +
                ", maxAckDelay=" + maxAckDelay() +
                ", disableActiveMigration=" + disableActiveMigration() +
                ", activeConnIdLimit=" + activeConnIdLimit() +
                ", maxDatagramFrameSize=" + maxDatagramFrameSize() +
                "]";
    }
}
