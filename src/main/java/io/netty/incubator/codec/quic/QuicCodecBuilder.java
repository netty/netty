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


import static io.netty.util.internal.ObjectUtil.checkInRange;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Abstract base class for {@code QUIC} codec builders.
 *
 * @param <B> the type of the {@link QuicCodecBuilder}.
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
    private int localConnIdLength = Quiche.QUICHE_MAX_CONN_ID_LEN;

    QuicCodecBuilder() {
        Quic.ensureAvailability();
    }

    /**
     * Returns itself.
     *
     * @return itself.
     */
    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    /**
     * Sets the congestion control algorithm to use.
     *
     * @param congestionControlAlgorithm    the {@link QuicCongestionControlAlgorithm} to use.
     * @return                              the instance itself.
     */
    public final B congestionControlAlgorithm(QuicCongestionControlAlgorithm congestionControlAlgorithm) {
        this.congestionControlAlgorithm = congestionControlAlgorithm;
        return self();
    }

    /**
     * Set the path to the certificate chain to use.
     *
     * @param path  the path to the chain.
     * @return      the instance itself.
     */
    public final B certificateChain(String path) {
        certPath = checkNotNull(path, "path");
        return self();
    }

    /**
     * Set the path to the private key to use.
     *
     * @param path  the path to the key.
     * @return      the instance itself.
     */
    public final B privateKey(String path) {
        keyPath = checkNotNull(path, "path");
        return self();
    }

    /**
     * Set if the remote peer should be verified or not.
     *
     * @param verify    {@code true} if verification should be done.
     * @return          the instance itself.
     */
    public final B verifyPeer(boolean verify) {
        verifyPeer = verify;
        return self();
    }

    /**
     * Set if <a href="https://tools.ietf.org/html/draft-thomson-quic-bit-grease-00">greasing</a> should be enabled
     * or not.
     *
     * @param enable    {@code true} if enabled, {@code false} otherwise.
     * @return          the instance itself.
     */
    public final B grease(boolean enable) {
        grease = enable;
        return self();
    }

    /**
     * Enable the support of early data.
     *
     * @return the instance itself.
     */
    public final B enableEarlyData() {
        earlyData = true;
        return self();
    }

    /**
     * Set the application protocols to use. These are in wire-format and so prefixed with the length each.
     *
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_application_protos">
     *     set_application_protos</a>
     *
     * @param protos    the application protocols in wire-format.
     * @return          the instance itself.
     */
    public final B applicationProtocols(byte[] protos) {
        this.protos = protos.clone();
        return self();
    }

    /**
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_max_idle_timeout">
     *     set_max_idle_timeout</a>.
     *
     * @param millis    the maximum idle timeout.
     * @return          the instance itself.
     */
    public final B maxIdleTimeout(long millis) {
        this.maxIdleTimeout = checkPositiveOrZero(millis, "value");
        return self();
    }
    /**
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_max_udp_payload_size">
     *     set_max_udp_payload_size</a>.
     *
     * @param size    the maximum payload size that is advertised to the remote peer.
     * @return        the instance itself.
     */
    public final B maxUdpPayloadSize(long size) {
        this.maxUdpPayloadSize = checkPositiveOrZero(size, "value");
        return self();
    }

    /**
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_data">
     *     set_initial_max_data</a>.
     *
     * @param value   the initial maximum data limit.
     * @return        the instance itself.
     */
    public final B initialMaxData(long value) {
        this.initialMaxData = checkPositiveOrZero(value, "value");
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_stream_data_bidi_local">
     *     set_initial_max_stream_data_bidi_local</a>.
     *
     * @param value   the initial maximum data limit for local bidirectional streams.
     * @return        the instance itself.
     */
    public final B initialMaxStreamDataBidirectionalLocal(long value) {
        this.initialMaxStreamDataBidiLocal = checkPositiveOrZero(value, "value");
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_stream_data_bidi_remote">
     *     set_initial_max_stream_data_bidi_remote</a>.
     *
     * @param value   the initial maximum data limit for remote bidirectional streams.
     * @return        the instance itself.
     */
    public final B initialMaxStreamDataBidirectionalRemote(long value) {
        this.initialMaxStreamDataBidiRemote = checkPositiveOrZero(value, "value");
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_streams_uni">
     *     set_initial_max_streams_uni</a>.
     *
     * @param value   the initial maximum data limit for unidirectional streams.
     * @return        the instance itself.
     */
    public final B initialMaxStreamDataUnidirectional(long value) {
        this.initialMaxStreamDataUni = checkPositiveOrZero(value, "value");
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_streams_bidi">
     *     set_initial_max_streams_bidi</a>.
     *
     * @param value   the initial maximum stream limit for bidirectional streams.
     * @return        the instance itself.
     */
    public final B initialMaxStreamsBidirectional(long value) {
        this.initialMaxStreamsBidi = checkPositiveOrZero(value, "value");
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_streams_uni">
     *     set_initial_max_streams_uni</a>.
     *
     * @param value   the initial maximum stream limit for bidirectional streams.
     * @return        the instance itself.
     */
    public final B initialMaxStreamsUnidirectional(long value) {
        this.initialMaxStreamsUni = checkPositiveOrZero(value, "value");
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_ack_delay_exponent">
     *     set_ack_delay_exponent</a>.
     *
     * @param value   the delay exponent used for ACKs.
     * @return        the instance itself.
     */
    public final B ackDelayExponent(long value) {
        this.ackDelayExponent = checkPositiveOrZero(value, "value");
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_max_ack_delay">
     *     set_max_ack_delay</a>.
     *
     * @param value   the max ack delay.
     * @return        the instance itself.
     */
    public final B maxAckDelay(long value) {
        this.maxAckDelay = checkPositiveOrZero(value, "value");
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_disable_active_migration">
     *     set_disable_active_migration</a>.
     *
     * @param value   {@code true} if migration should be disabled.
     * @return        the instance itself.
     */
    public final B disableActiveMigration(boolean value) {
        this.disableActiveMigration = value;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.enable_hystart">
     *     enable_hystart</a>.
     *
     * @param value   {@code true} if Hystart should be enabled.
     * @return        the instance itself.
     */
    public final B enableHystart(boolean value) {
        this.enableHystart = value;
        return self();
    }

    /**
     * Sets the local connection id length that is used.
     *
     * @param value   {@code true} the length of local generated connections ids.
     * @return        the instance itself.
     */
    public final B localConnectionIdLength(int value) {
        this.localConnIdLength = checkInRange(value, 0, Quiche.QUICHE_MAX_CONN_ID_LEN,  "value");
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
     * Builds the QUIC codec that should be added to the {@link io.netty.channel.ChannelPipeline} of the underlying
     * {@link io.netty.channel.Channel} which is used as transport for QUIC.
     *
     * @return the {@link ChannelHandler} which acts as QUIC codec.
     */
    public final ChannelHandler build() {
        validate();
        return build(createConfig(), localConnIdLength);
    }

    /**
     * Builds the QUIC codec.
     *
     * @param config            the {@link QuicheConfig} that should be used.
     * @param localConnIdLength the local connection id length.
     * @return                  the {@link ChannelHandler} which acts as codec.
     */
    protected abstract ChannelHandler build(QuicheConfig config, int localConnIdLength);
}
