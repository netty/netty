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

final class QuicheConfig {
    private final boolean isDatagramSupported;
    private final long config;

    QuicheConfig(Boolean grease, Long maxIdleTimeout, Long maxSendUdpPayloadSize,
                        Long maxRecvUdpPayloadSize, Long initialMaxData,
                        Long initialMaxStreamDataBidiLocal, Long initialMaxStreamDataBidiRemote,
                        Long initialMaxStreamDataUni, Long initialMaxStreamsBidi, Long initialMaxStreamsUni,
                        Long ackDelayExponent, Long maxAckDelay, Boolean disableActiveMigration, Boolean enableHystart,
                        QuicCongestionControlAlgorithm congestionControlAlgorithm,
                 Integer recvQueueLen, Integer sendQueueLen) {
        long config = Quiche.quiche_config_new(Quiche.QUICHE_PROTOCOL_VERSION);
        try {
            if (grease != null) {
                Quiche.quiche_config_grease(config, grease);
            }
            if (maxIdleTimeout != null) {
                Quiche.quiche_config_set_max_idle_timeout(config, maxIdleTimeout);
            }
            if (maxSendUdpPayloadSize != null) {
                Quiche.quiche_config_set_max_send_udp_payload_size(config, maxSendUdpPayloadSize);
            }
            if (maxRecvUdpPayloadSize != null) {
                Quiche.quiche_config_set_max_recv_udp_payload_size(config, maxRecvUdpPayloadSize);
            }
            if (initialMaxData != null) {
                Quiche.quiche_config_set_initial_max_data(config, initialMaxData);
            }
            if (initialMaxStreamDataBidiLocal != null) {
                Quiche.quiche_config_set_initial_max_stream_data_bidi_local(config, initialMaxStreamDataBidiLocal);
            }
            if (initialMaxStreamDataBidiRemote != null) {
                Quiche.quiche_config_set_initial_max_stream_data_bidi_remote(config, initialMaxStreamDataBidiRemote);
            }
            if (initialMaxStreamDataUni != null) {
                Quiche.quiche_config_set_initial_max_stream_data_uni(config, initialMaxStreamDataUni);
            }
            if (initialMaxStreamsBidi != null) {
                Quiche.quiche_config_set_initial_max_streams_bidi(config, initialMaxStreamsBidi);
            }
            if (initialMaxStreamsUni != null) {
                Quiche.quiche_config_set_initial_max_streams_uni(config, initialMaxStreamsUni);
            }
            if (ackDelayExponent != null) {
                Quiche.quiche_config_set_ack_delay_exponent(config, ackDelayExponent);
            }
            if (maxAckDelay != null) {
                Quiche.quiche_config_set_max_ack_delay(config, maxAckDelay);
            }
            if (disableActiveMigration != null) {
                Quiche.quiche_config_set_disable_active_migration(config, disableActiveMigration);
            }
            if (enableHystart != null) {
                Quiche.quiche_config_enable_hystart(config, enableHystart);
            }
            if (congestionControlAlgorithm != null) {
                switch (congestionControlAlgorithm) {
                    case RENO:
                        Quiche.quiche_config_set_cc_algorithm(config, Quiche.QUICHE_CC_RENO);
                        break;
                    case CUBIC:
                        Quiche.quiche_config_set_cc_algorithm(config, Quiche.QUICHE_CC_CUBIC);
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Unknown congestionControlAlgorithm: " + congestionControlAlgorithm);
                }
            }
            if (recvQueueLen != null && sendQueueLen != null) {
                isDatagramSupported = true;
                Quiche.quiche_config_enable_dgram(config, true, recvQueueLen, sendQueueLen);
            } else {
                isDatagramSupported = false;
            }
            this.config = config;
        } catch (Throwable cause) {
            Quiche.quiche_config_free(config);
            throw cause;
        }
    }

    boolean isDatagramSupported() {
        return isDatagramSupported;
    }

    long nativeAddress() {
        return config;
    }

    void free() {
        if (config != 0) {
            Quiche.quiche_config_free(config);
        }
    }
}
