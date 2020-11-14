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

import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Builder for QUIC.
 */
public abstract class QuicBuilder<B extends QuicBuilder<B>> {
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

    QuicBuilder() { }

    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    /**
     * Set the path to the certificate chain to use.
     */
    public final B certificateChain(String path) {
        certPath = path;
        return self();
    }

    /**
     * Set the path to the private key to use.
     */
    public final B privateKey(String path) {
        keyPath = path;
        return self();
    }

    /**
     * Set if the remote peer should be verified or not.
     */
    public final B verifyPeer(boolean verify) {
        verifyPeer = verify;
        return self();
    }

    /**
     * Set if <a href="https://tools.ietf.org/html/draft-thomson-quic-bit-grease-00">greasing</a> should be enabled
     * or not.
     */
    public final B grease(boolean enable) {
        grease = enable;
        return self();
    }

    /**
     * Enable the support of early data.
     */
    public final B enableEarlyData() {
        earlyData = true;
        return self();
    }

    /**
     * Set the application protocols to use. These are in wire.format and so prefixed with the length each.
     *
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_application_protos">
     *     set_application_protos</a>
     */
    public final B applicationProtocols(byte[] protos) {
        this.protos = protos.clone();
        return self();
    }

    /**
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_max_idle_timeout">
     *     set_max_idle_timeout</a>.
     */
    public final B maxIdleTimeout(long nanos) {
        this.maxIdleTimeout = nanos;
        return self();
    }
    /**
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_max_udp_payload_size">
     *     set_max_udp_payload_size</a>.
     */
    public final B maxUdpPayloadSize(long size) {
        this.maxUdpPayloadSize = size;
        return self();
    }

    /**
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_data">
     *     set_initial_max_data</a>.
     */
    public final B initialMaxData(long value) {
        this.initialMaxData = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_stream_data_bidi_local">
     *     set_initial_max_stream_data_bidi_local</a>.
     */
    public final B initialMaxStreamDataBidirectionalLocal(long value) {
        this.initialMaxStreamDataBidiLocal = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_stream_data_bidi_remote">
     *     set_initial_max_stream_data_bidi_remote</a>.
     */
    public final B initialMaxStreamDataBidirectionalRemote(long value) {
        this.initialMaxStreamDataBidiRemote = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_streams_uni">
     *     set_initial_max_streams_uni</a>.
     */
    public final B initialMaxStreamDataUnidirectional(long value) {
        this.initialMaxStreamDataUni = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_streams_bidi">
     *     set_initial_max_streams_bidi</a>.
     */
    public final B initialMaxStreamsBidirectional(long value) {
        this.initialMaxStreamsBidi = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_streams_uni">
     *     set_initial_max_streams_uni</a>.
     */
    public final B initialMaxStreamsUnidirectional(long value) {
        this.initialMaxStreamsUni = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_ack_delay_exponent">
     *     set_ack_delay_exponent</a>.
     */
    public final B ackDelayExponent(long value) {
        this.ackDelayExponent = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_max_ack_delay">
     *     set_max_ack_delay</a>.
     */
    public final B maxAckDelay(long value) {
        this.maxAckDelay = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_disable_active_migration">
     *     set_disable_active_migration</a>.
     */
    public final B disableActiveMigration(boolean value) {
        this.disableActiveMigration = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.enable_hystart">
     *     enable_hystart</a>.
     */
    public final B enableHystart(boolean value) {
        this.enableHystart = value;
        return self();
    }

    /**
     * Creates the native config object and return it.
     */
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

    /**
     * Return a {@link QuicConnectionIdGenerator} which randomly generates new connection ids.
     */
    public static QuicConnectionIdGenerator randomGenerator() {
        return SecureRandomQuicConnectionIdGenerator.INSTANCE;
    }

    private static final class SecureRandomQuicConnectionIdGenerator implements QuicConnectionIdGenerator {
        private static final SecureRandom RANDOM = new SecureRandom();

        public static final QuicConnectionIdGenerator INSTANCE = new SecureRandomQuicConnectionIdGenerator();

        private SecureRandomQuicConnectionIdGenerator() { }

        @Override
        public ByteBuffer newId() {
            return newId(maxConnectionIdLength());
        }

        @Override
        public ByteBuffer newId(int length) {
            if (length > maxConnectionIdLength()) {
                throw new IllegalArgumentException();
            }
            byte[] bytes = new byte[length];
            RANDOM.nextBytes(bytes);
            return ByteBuffer.wrap(bytes);
        }

        @Override
        public ByteBuffer newId(ByteBuffer buffer, int length) {
            return newId(length);
        }

        @Override
        public int maxConnectionIdLength() {
            return Quiche.QUICHE_MAX_CONN_ID_LEN;
        }
    }
}
