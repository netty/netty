/*
 * Copyright 2026 The Netty Project
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

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class QuicheQuicChannelPacingTest {
    private static final long NOW = TimeUnit.SECONDS.toNanos(1000);

    @Test
    void sendRightAwayIfThereIsNoSendTime() {
        assertThat(QuicheQuicChannel.pacingDelayNanos(0, NOW)).isZero();
    }

    @Test
    void sendRightAwayIfTheClockIsUnknown() {
        assertThat(QuicheQuicChannel.pacingDelayNanos(NOW + TimeUnit.MILLISECONDS.toNanos(5), 0)).isZero();
    }

    @Test
    void sendRightAwayIfTheSendTimeIsReached() {
        assertThat(QuicheQuicChannel.pacingDelayNanos(NOW, NOW)).isZero();
        assertThat(QuicheQuicChannel.pacingDelayNanos(NOW - 1, NOW)).isZero();
    }

    @Test
    void sendRightAwayIfTheSendTimeIsWithinTheGranularity() {
        assertThat(QuicheQuicChannel.pacingDelayNanos(NOW + 1, NOW)).isZero();
        assertThat(QuicheQuicChannel.pacingDelayNanos(NOW + TimeUnit.MILLISECONDS.toNanos(1), NOW)).isZero();
    }

    @Test
    void waitIfTheSendTimeIsInTheFuture() {
        long delay = TimeUnit.MILLISECONDS.toNanos(1) + 1;
        assertThat(QuicheQuicChannel.pacingDelayNanos(NOW + delay, NOW)).isEqualTo(delay);
        delay = TimeUnit.SECONDS.toNanos(1);
        assertThat(QuicheQuicChannel.pacingDelayNanos(NOW + delay, NOW)).isEqualTo(delay);
    }

    @Test
    void sendRightAwayIfTheSendTimeIsTooFarInTheFuture() {
        // This can only happen if the clocks don't match, so we should not stall the connection.
        assertThat(QuicheQuicChannel.pacingDelayNanos(NOW + TimeUnit.SECONDS.toNanos(1) + 1, NOW)).isZero();
        assertThat(QuicheQuicChannel.pacingDelayNanos(Long.MAX_VALUE, NOW)).isZero();
    }
}
