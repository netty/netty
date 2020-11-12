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

/**
 * Build a {@link QuicServerCodec} that can be used in a {@link io.netty.channel.ChannelPipeline}.
 */
public abstract class QuicCodecBuilder<B extends QuicCodecBuilder<B>> {
    private String certPath;
    private String keyPath;
    private Boolean verifyPeer;
    private Boolean grease;
    private boolean earlyData;
    private byte[] protos;
    private Long maxIdleTimeout;
    private Long maxUdpPayloadSize;
    private Long initialMaxData;
    private Long initialMaxStreamDataBidiLocal;
    private Long initialMaxStreamDataBidiRemote;
    private Long initialMaxStreamDataUni;
    private Long initialMaxStreamsBidi;
    private Long initialMaxStreamsUni;
    private Long ackDelayExponent;
    private Long maxAckDelay;
    private Boolean disableActiveMigration;
    private Boolean enableHystart;

    QuicCodecBuilder() { }

    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    public final B certificateChain(String path) {
        certPath = path;
        return self();
    }

    public final B privateKey(String path) {
        keyPath = path;
        return self();
    }

    public final B verifyPeer(boolean verify) {
        verifyPeer = verify;
        return self();
    }

    public final B grease(boolean enable) {
        grease = enable;
        return self();
    }

    public final B enableEarlyData() {
        earlyData = true;
        return self();
    }

    public final B applicationProtocols(byte[] protos) {
        this.protos = protos.clone();
        return self();
    }

    public final B maxIdleTimeout(long nanos) {
        this.maxIdleTimeout = nanos;
        return self();
    }

    public final B maxUdpPayloadSize(long size) {
        this.maxUdpPayloadSize = size;
        return self();
    }

    public final B initialMaxData(long value) {
        this.initialMaxData = value;
        return self();
    }

    public final B initialMaxStreamDataBidirectionalLocal(long value) {
        this.initialMaxStreamDataBidiLocal = value;
        return self();
    }

    public final B initialMaxStreamDataBidirectionalRemote(long value) {
        this.initialMaxStreamDataBidiRemote = value;
        return self();
    }

    public final B initialMaxStreamDataUnidirectional(long value) {
        this.initialMaxStreamDataUni = value;
        return self();
    }

    public final B initialMaxStreamsBidirectional(long value) {
        this.initialMaxStreamsBidi = value;
        return self();
    }

    public final B initialMaxStreamsUnidirectional(long value) {
        this.initialMaxStreamsUni = value;
        return self();
    }

    public final B ackDelayExponent(long value) {
        this.ackDelayExponent = value;
        return self();
    }

    public final B maxAckDelay(long value) {
        this.maxAckDelay = value;
        return self();
    }

    public final B disableActiveMigration(boolean value) {
        this.disableActiveMigration = value;
        return self();
    }

    public final B enableHystart(boolean value) {
        this.enableHystart = value;
        return self();
    }

    protected final long createConfig() {
        long config = Quiche.quiche_config_new(Quiche.QUICHE_PROTOCOL_VERSION);
        try {
            if (certPath != null && Quiche.quiche_config_load_cert_chain_from_pem_file(config, certPath) != 0) {
                throw new IllegalArgumentException("Unable to load certificate chain");
            }
            if (keyPath != null && Quiche.quiche_config_load_priv_key_from_pem_file(config, keyPath) != 0) {
                throw new IllegalArgumentException("Unable to load private key");
            }
            if (verifyPeer != null) {
                Quiche.quiche_config_verify_peer(config, verifyPeer);
            }
            if (grease != null) {
                Quiche.quiche_config_grease(config, grease);
            }
            if (earlyData) {
                Quiche.quiche_config_enable_early_data(config);
            }
            if (protos != null) {
                Quiche.quiche_config_set_application_protos(config, protos);
            }
            if (maxIdleTimeout != null) {
                Quiche.quiche_config_set_max_idle_timeout(config, maxIdleTimeout);
            }
            if (maxUdpPayloadSize != null) {
                Quiche.quiche_config_set_max_udp_payload_size(config, maxUdpPayloadSize);
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
            return config;
        } catch (Throwable cause) {
            Quiche.quiche_config_free(config);
            throw cause;
        }
    }
}
