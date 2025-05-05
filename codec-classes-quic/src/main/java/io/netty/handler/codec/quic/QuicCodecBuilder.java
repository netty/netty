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

import io.netty.channel.ChannelHandler;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.netty.util.internal.ObjectUtil.checkInRange;
import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Abstract base class for {@code QUIC} codec builders.
 *
 * @param <B> the type of the {@link QuicCodecBuilder}.
 */
public abstract class QuicCodecBuilder<B extends QuicCodecBuilder<B>> {
    private final boolean server;
    private Boolean grease;
    private Long maxIdleTimeout;
    private Long maxRecvUdpPayloadSize;
    private Long maxSendUdpPayloadSize;
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
    private Integer initialCongestionWindowPackets;
    private int localConnIdLength;
    private Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider;
    private FlushStrategy flushStrategy = FlushStrategy.DEFAULT;
    private Integer recvQueueLen;
    private Integer sendQueueLen;
    private Long activeConnectionIdLimit;
    private byte[] statelessResetToken;

    private Executor sslTaskExecutor;

    // package-private for testing only
    int version;

    QuicCodecBuilder(boolean server) {
        Quic.ensureAvailability();
        this.version = Quiche.QUICHE_PROTOCOL_VERSION;
        this.localConnIdLength = Quiche.QUICHE_MAX_CONN_ID_LEN;
        this.server = server;
    }

    QuicCodecBuilder(QuicCodecBuilder<B> builder) {
        Quic.ensureAvailability();
        this.server = builder.server;
        this.grease = builder.grease;
        this.maxIdleTimeout = builder.maxIdleTimeout;
        this.maxRecvUdpPayloadSize = builder.maxRecvUdpPayloadSize;
        this.maxSendUdpPayloadSize = builder.maxSendUdpPayloadSize;
        this.initialMaxData = builder.initialMaxData;
        this.initialMaxStreamDataBidiLocal = builder.initialMaxStreamDataBidiLocal;
        this.initialMaxStreamDataBidiRemote = builder.initialMaxStreamDataBidiRemote;
        this.initialMaxStreamDataUni = builder.initialMaxStreamDataUni;
        this.initialMaxStreamsBidi = builder.initialMaxStreamsBidi;
        this.initialMaxStreamsUni = builder.initialMaxStreamsUni;
        this.ackDelayExponent = builder.ackDelayExponent;
        this.maxAckDelay = builder.maxAckDelay;
        this.disableActiveMigration = builder.disableActiveMigration;
        this.enableHystart = builder.enableHystart;
        this.congestionControlAlgorithm = builder.congestionControlAlgorithm;
        this.initialCongestionWindowPackets = builder.initialCongestionWindowPackets;
        this.localConnIdLength = builder.localConnIdLength;
        this.sslEngineProvider = builder.sslEngineProvider;
        this.flushStrategy = builder.flushStrategy;
        this.recvQueueLen = builder.recvQueueLen;
        this.sendQueueLen = builder.sendQueueLen;
        this.activeConnectionIdLimit = builder.activeConnectionIdLimit;
        this.statelessResetToken = builder.statelessResetToken;
        this.sslTaskExecutor = builder.sslTaskExecutor;
        this.version = builder.version;
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
     * Sets the {@link FlushStrategy} that will be used to detect when an automatic flush
     * should happen.
     *
     * @param flushStrategy   the strategy to use.
     * @return                the instance itself.
     */
    public final B flushStrategy(FlushStrategy flushStrategy) {
        this.flushStrategy = Objects.requireNonNull(flushStrategy, "flushStrategy");
        return self();
    }

    /**
     * Sets the congestion control algorithm to use.
     *
     * The default is {@link QuicCongestionControlAlgorithm#CUBIC}.
     *
     * @param congestionControlAlgorithm    the {@link QuicCongestionControlAlgorithm} to use.
     * @return                              the instance itself.
     */
    public final B congestionControlAlgorithm(QuicCongestionControlAlgorithm congestionControlAlgorithm) {
        this.congestionControlAlgorithm = congestionControlAlgorithm;
        return self();
    }

    /**
     * Sets initial congestion window size in terms of packet count.
     *
     * The default value is 10.
     *
     * @param numPackets number of packets for the initial congestion window
     * @return
     */
    public final B initialCongestionWindowPackets(int numPackets) {
        this.initialCongestionWindowPackets = numPackets;
        return self();
    }

    /**
     * Set if <a href="https://tools.ietf.org/html/draft-thomson-quic-bit-grease-00">greasing</a> should be enabled
     * or not.
     *
     * The default value is {@code true}.
     *
     * @param enable    {@code true} if enabled, {@code false} otherwise.
     * @return          the instance itself.
     */
    public final B grease(boolean enable) {
        grease = enable;
        return self();
    }

    /**
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_max_idle_timeout">
     *     set_max_idle_timeout</a>.
     *
     * The default value is infinite, that is, no timeout is used.
     *
     * @param amount    the maximum idle timeout.
     * @param unit      the {@link TimeUnit}.
     * @return          the instance itself.
     */
    public final B maxIdleTimeout(long amount, TimeUnit unit) {
        this.maxIdleTimeout = unit.toMillis(checkPositiveOrZero(amount, "amount"));
        return self();
    }

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/35e38d987c1e53ef2bd5f23b754c50162b5adac8/src/lib.rs#L669">
     *     set_max_send_udp_payload_size</a>.
     *
     * The default and minimum value is 1200.
     *
     * @param size    the maximum payload size that is advertised to the remote peer.
     * @return        the instance itself.
     */
    public final B maxSendUdpPayloadSize(long size) {
        this.maxSendUdpPayloadSize = checkPositiveOrZero(size, "value");
        return self();
    }

    /**
     * See <a href="https://github.com/cloudflare/quiche/blob/35e38d987c1e53ef2bd5f23b754c50162b5adac8/src/lib.rs#L662">
     *     set_max_recv_udp_payload_size</a>.
     *
     * The default value is 65527.
     *
     * @param size    the maximum payload size that is advertised to the remote peer.
     * @return        the instance itself.
     */
    public final B maxRecvUdpPayloadSize(long size) {
        this.maxRecvUdpPayloadSize = checkPositiveOrZero(size, "value");
        return self();
    }

    /**
     * See <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_data">
     *     set_initial_max_data</a>.
     *
     * The default value is 0.
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
     * The default value is 0.
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
     * The default value is 0.
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
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_initial_max_stream_data_uni">
     *     set_initial_max_stream_data_uni</a>.
     *
     * The default value is 0.
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
     * The default value is 0.
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
     * The default value is 0.
     *
     * @param value   the initial maximum stream limit for unidirectional streams.
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
     * The default value is 3.
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
     * The default value is 25 milliseconds.
     *
     * @param amount    the max ack delay.
     * @param unit      the {@link TimeUnit}.
     * @return          the instance itself.
     */
    public final B maxAckDelay(long amount, TimeUnit unit) {
        this.maxAckDelay = unit.toMillis(checkPositiveOrZero(amount, "amount"));
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.set_disable_active_migration">
     *     set_disable_active_migration</a>.
     *
     * The default value is {@code true}.
     *
     * @param enable  {@code true} if migration should be enabled, {@code false} otherwise.
     * @return        the instance itself.
     */
    public final B activeMigration(boolean enable) {
        this.disableActiveMigration = !enable;
        return self();
    }

    /**
     * See
     * <a href="https://docs.rs/quiche/0.6.0/quiche/struct.Config.html#method.enable_hystart">
     *     enable_hystart</a>.
     *
     * The default value is {@code true}.
     *
     * @param enable  {@code true} if Hystart should be enabled.
     * @return        the instance itself.
     */
    public final B hystart(boolean enable) {
        this.enableHystart = enable;
        return self();
    }

    /**
     * Sets the local connection id length that is used.
     *
     * The default is 20, which is also the maximum that is supported.
     *
     * @param value   the length of local generated connections ids.
     * @return        the instance itself.
     */
    public final B localConnectionIdLength(int value) {
        this.localConnIdLength = checkInRange(value, 0, Quiche.QUICHE_MAX_CONN_ID_LEN,  "value");
        return self();
    }

    /**
     * Allows to configure the {@code QUIC version} that should be used.
     *
     * The default value is the latest supported version by the underlying library.
     *
     * @param version the {@code QUIC version} to use.
     * @return        the instance itself.
     */
    public final B version(int version) {
        this.version = version;
        return self();
    }

    /**
     * If configured this will enable <a href="https://tools.ietf.org/html/draft-ietf-quic-datagram-01">
     *     Datagram support.</a>
     * @param recvQueueLen  the RECV queue length.
     * @param sendQueueLen  the SEND queue length.
     * @return              the instance itself.
     */
    public final B datagram(int recvQueueLen, int sendQueueLen) {
        checkPositive(recvQueueLen, "recvQueueLen");
        checkPositive(sendQueueLen, "sendQueueLen");

        this.recvQueueLen = recvQueueLen;
        this.sendQueueLen = sendQueueLen;
        return self();
    }

    /**
     * The {@link QuicSslContext} that will be used to create {@link QuicSslEngine}s for {@link QuicChannel}s.
     *
     * If you need a more flexible way to provide {@link QuicSslEngine}s use {@link #sslEngineProvider(Function)}.
     *
     * @param sslContext    the context.
     * @return              the instance itself.
     */
    public final B sslContext(QuicSslContext sslContext) {
        if (server != sslContext.isServer()) {
            throw new IllegalArgumentException("QuicSslContext.isServer() " + sslContext.isServer()
                    + " isn't supported by this builder");
        }
        return sslEngineProvider(q -> sslContext.newEngine(q.alloc()));
    }

    /**
     * The {@link Function} that will return the {@link QuicSslEngine} that should be used for the
     * {@link QuicChannel}.
     *
     * @param sslEngineProvider    the provider.
     * @return                      the instance itself.
     */
    public final B sslEngineProvider(Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider) {
        this.sslEngineProvider = sslEngineProvider;
        return self();
    }

    /**
     * Allow to configure a {@link Executor} that will be used to run expensive SSL operations.
     *
     * @param sslTaskExecutor       the {@link Executor} that will be used to offload expensive SSL operations.
     * @return                      the instance itself.
     */
    public final B sslTaskExecutor(Executor sslTaskExecutor) {
        this.sslTaskExecutor = sslTaskExecutor;
        return self();
    }

    /**
     * Allows to configure the {@code active connect id limit} that should be used.
     *
     * @param limit     the limit to use.
     * @return          the instance itself.
     */
    public final B activeConnectionIdLimit(long limit) {
        checkPositive(limit, "limit");
        activeConnectionIdLimit = limit;
        return self();
    }

    /**
     * Allows to configure the {@code active connect id limit} that should be used.
     *
     * @param token     the token to use.
     * @return          the instance itself.
     */
    public final B statelessResetToken(byte[] token) {
        if (token.length != 16) {
            throw new IllegalArgumentException("token must be 16 bytes but was " + token.length);
        }

        this.statelessResetToken = token.clone();
        return self();
    }

    private QuicheConfig createConfig() {
        return new QuicheConfig(version, grease,
                maxIdleTimeout, maxSendUdpPayloadSize, maxRecvUdpPayloadSize, initialMaxData,
                initialMaxStreamDataBidiLocal, initialMaxStreamDataBidiRemote,
                initialMaxStreamDataUni, initialMaxStreamsBidi, initialMaxStreamsUni,
                ackDelayExponent, maxAckDelay, disableActiveMigration, enableHystart,
                congestionControlAlgorithm, initialCongestionWindowPackets, recvQueueLen, sendQueueLen,
                activeConnectionIdLimit, statelessResetToken);
    }

    /**
     * Validate the configuration before building the codec.
     */
    protected void validate() {
        if (sslEngineProvider == null) {
            throw new IllegalStateException("sslEngineProvider can't be null");
        }
    }

    /**
     * Builds the QUIC codec that should be added to the {@link io.netty.channel.ChannelPipeline} of the underlying
     * {@link io.netty.channel.Channel} which is used as transport for QUIC.
     *
     * @return the {@link ChannelHandler} which acts as QUIC codec.
     */
    public final ChannelHandler build() {
        validate();
        QuicheConfig config = createConfig();
        try {
            return build(config, sslEngineProvider, sslTaskExecutor, localConnIdLength, flushStrategy);
        } catch (Throwable cause) {
            config.free();
            throw cause;
        }
    }

    /**
     * Clone the builder
     *
     * @return the new instance that is a clone if this instance.
     */
    public abstract B clone();

    /**
     * Builds the QUIC codec.
     *
     * @param config                the {@link QuicheConfig} that should be used.
     * @param sslContextProvider    the context provider
     * @param sslTaskExecutor       the {@link Executor} to use.
     * @param localConnIdLength     the local connection id length.
     * @param flushStrategy         the {@link FlushStrategy}  that should be used.
     * @return                      the {@link ChannelHandler} which acts as codec.
     */
    abstract ChannelHandler build(QuicheConfig config,
                                            Function<QuicChannel, ? extends QuicSslEngine> sslContextProvider,
                                            Executor sslTaskExecutor,
                                            int localConnIdLength, FlushStrategy flushStrategy);
}
