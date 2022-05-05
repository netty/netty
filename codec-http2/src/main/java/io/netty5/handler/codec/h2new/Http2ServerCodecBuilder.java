/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty5.handler.codec.h2new;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelInitializer;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.util.AttributeKey;

import java.util.function.BiFunction;
import java.util.function.Function;

import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;

public final class Http2ServerCodecBuilder {
    static final AttributeKey<ChannelFlowControlledBytesDistributor>
            FLOW_CONTROLLED_BYTES_DISTRIBUTOR_ATTRIBUTE_KEY =
            AttributeKey.newInstance("_netty.v5.h2.flow.controlled.bytes.distributor");
    private Function<Channel, ChannelFlowControlledBytesDistributor> distributorFactory;

    private Http2Settings initialSettings;
    private Http2ServerSslContext sslContext;
    private boolean validateHeaders;
    private SensitivityDetector headerSensitivityDetector;

    public Http2ServerCodecBuilder maxReservedStreams(int maxReservedStreams) {
        return null;
    }

    public Http2ServerCodecBuilder validateHeaders(boolean validateHeaders) {
        this.validateHeaders = validateHeaders;
        return this;
    }

    public Http2ServerCodecBuilder encoderEnforceMaxConcurrentStreams(boolean encoderEnforceMaxConcurrentStreams) {
        return null;
    }

    public Http2ServerCodecBuilder encoderEnforceMaxQueuedControlFrames(int maxQueuedControlFrames) {
        return null;
    }

    public Http2ServerCodecBuilder headerSensitivityDetector(SensitivityDetector headerSensitivityDetector) {
        this.headerSensitivityDetector = checkNotNullWithIAE(headerSensitivityDetector, "headerSensitivityDetector");
        return this;
    }

    public Http2ServerCodecBuilder encoderIgnoreMaxHeaderListSize(boolean ignoreMaxHeaderListSize) {
        return null;
    }

    public Http2ServerCodecBuilder autoAckSettingsFrame(boolean autoAckSettings) {
        return null;
    }

    public Http2ServerCodecBuilder autoAckPingFrame(boolean autoAckPingFrame) {
        return null;
    }

    public Http2ServerCodecBuilder decoupleCloseAndGoAway(boolean decoupleCloseAndGoAway) {
        return null;
    }

    public Http2ServerCodecBuilder decoderEnforceMaxConsecutiveEmptyDataFrames(int maxConsecutiveEmptyFrames) {
        return null;
    }

    public Http2ServerCodecBuilder initialSettings(Http2Settings settings) {
        this.initialSettings = settings;
        return this;
    }

    public Http2ServerCodecBuilder sslContext(Http2ServerSslContext sslContext) {
        this.sslContext = checkNotNullWithIAE(sslContext, "sslContext");
        return this;
    }

    public Http2ServerCodecBuilder supportUpgradeFromHttp1x() {
        return null;
    }

    public Http2ServerCodecBuilder supportUpgradeFromHttp1x(
            BiFunction<Channel, FullHttpRequest, Http2Settings> upgrade) {
        return null;
    }

    public Http2ServerCodecBuilder channelFlowControlledBytesDistributor(
            Function<Channel, ChannelFlowControlledBytesDistributor> distributorFactory) {
        this.distributorFactory = distributorFactory;
        return this;
    }

    public ChannelHandler buildRaw(ChannelHandler handler) {
        checkNotNullWithIAE(handler, "handler");

        final Http2Settings settings = initialSettings;
        final boolean validateHeaders = this.validateHeaders;
        final SensitivityDetector headerSensitivityDetector = this.headerSensitivityDetector;
        final Function<Channel, ChannelFlowControlledBytesDistributor> distributorFactory = this.distributorFactory;

        return build0(sslContext, new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                if (headerSensitivityDetector != null) {
                    ch.pipeline().addLast(new Http2FrameEncoder(headerSensitivityDetector));
                } else {
                    ch.pipeline().addLast(new Http2FrameEncoder());
                }
                ch.pipeline().addLast(new Http2FrameDecoder(validateHeaders));
                final Http2RawChannelFramesValidator framesValidator = new Http2RawChannelFramesValidator();
                final ChannelFlowControlledBytesDistributor distributor;
                if (distributorFactory == null) {
                    distributor = addDistributorForRawChannel(ch, framesValidator);
                } else {
                    distributor = distributorFactory.apply(ch);
                }
                ch.pipeline().addLast(new Http2ControlStreamInitializer(settings, distributor, true));
                ch.pipeline().addLast(framesValidator);
                ch.pipeline().addLast(handler);
            }
        });
    }

    public ChannelHandler build(Http2ServerChannelInitializer initializer) {
        checkNotNullWithIAE(initializer, "initializer");

        final Http2Settings settings = initialSettings;
        final boolean validateHeaders = this.validateHeaders;
        final SensitivityDetector headerSensitivityDetector = this.headerSensitivityDetector;
        final Function<Channel, ChannelFlowControlledBytesDistributor> distributorFactory = this.distributorFactory;

        return build0(sslContext, new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new Http2FrameDecoder(validateHeaders));
                final ChannelFlowControlledBytesDistributor distributor;
                if (distributorFactory == null) {
                    distributor = addDistributorForChannel(ch);
                } else {
                    distributor = distributorFactory.apply(ch);
                }

                DefaultHttp2Channel h2channel = new DefaultHttp2Channel(ch, distributor, true);
                ch.pipeline().addLast(new Http2ControlStreamInitializer(settings, h2channel, distributor, true));
                Http2ServerStreamMuxer muxer = distributor instanceof DefaultChannelFlowControlledBytesDistributor ?
                        new Http2ServerStreamMuxer(h2channel, headerSensitivityDetector,
                                (DefaultChannelFlowControlledBytesDistributor) distributor) :
                        new Http2ServerStreamMuxer(h2channel, headerSensitivityDetector);
                // Muxer creates child streams which will write Buffer instances to the parent channel
                h2channel.pipeline().addLast(muxer);

                initializer.initialize(h2channel);
            }
        });
    }

    private ChannelHandler build0(Http2ServerSslContext sslContext, ChannelInitializer<Channel> h2Initializer) {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                if (sslContext != null) {
                    ch.pipeline().addLast(sslContext.newHandler(ch.bufferAllocator()),
                                          sslContext.newApnHandler(h2Initializer));
                } else {
                    ch.pipeline().addLast(h2Initializer);
                }
            }
        };
    }

    private static ChannelFlowControlledBytesDistributor addDistributorForChannel(Channel channel) {
        DefaultChannelFlowControlledBytesDistributor distributor =
                new DefaultChannelFlowControlledBytesDistributor(channel);
        channel.pipeline().addLast(distributor);
        return distributor;
    }

    private static ChannelFlowControlledBytesDistributor addDistributorForRawChannel(
            Channel channel, RawHttp2RequestStreamCodecState rawHttp2RequestStreamCodecState) {
        DefaultChannelFlowControlledBytesDistributor distributor =
                new DefaultChannelFlowControlledBytesDistributor(channel);
        channel.pipeline().addLast(distributor);
        channel.pipeline().addLast(new RawFlowControlFrameInspector(rawHttp2RequestStreamCodecState, distributor));
        return distributor;
    }

    @FunctionalInterface
    public interface Http2ServerChannelInitializer {
        void initialize(Http2Channel http2Channel);
    }
}
