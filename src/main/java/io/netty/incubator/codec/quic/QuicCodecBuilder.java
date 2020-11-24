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

import io.netty.channel.ChannelHandler;

/**
 * Abstract base class for {@code QUIC} codec builders.
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
    private QuicCongestionControlAlgorithm congestionControlAlgorithm;

    QuicCodecBuilder() { }

    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    /**
     * Sets the congestion control algorithm to use.
     */
    public final B congestionControlAlgorithm(QuicCongestionControlAlgorithm congestionControlAlgorithm) {
        this.congestionControlAlgorithm = congestionControlAlgorithm;
        return self();
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

    private QuicheConfig createConfig() {
        return new QuicheConfig(certPath, keyPath, verifyPeer, grease, earlyData,
                protos, maxIdleTimeout, maxUdpPayloadSize, initialMaxData,
                initialMaxStreamDataBidiLocal, initialMaxStreamDataBidiRemote,
                initialMaxStreamDataUni, initialMaxStreamsBidi, initialMaxStreamsUni,
                ackDelayExponent, maxAckDelay, disableActiveMigration, enableHystart,
                congestionControlAlgorithm);
    }

    /**
     * Validate the configuration before building the codec.
     */
    protected void validate() { }

    /**
     * Build the QUIC codec that should be added to the {@link io.netty.channel.ChannelPipeline} of the underlying
     * {@link io.netty.channel.Channel} which is used as transport for QUIC.
     */
    public final ChannelHandler build() {
        validate();
        return build(createConfig());
    }

    protected abstract ChannelHandler build(QuicheConfig config);
}
