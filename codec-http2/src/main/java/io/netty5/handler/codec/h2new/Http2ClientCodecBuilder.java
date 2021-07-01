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

package io.netty.handler.codec.h2new;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty.handler.codec.http2.Http2Settings;

import java.util.function.Function;

import static io.netty.util.internal.ObjectUtil.checkNotNullWithIAE;

public final class Http2ClientCodecBuilder {
    private Function<Channel, ChannelFlowControlledBytesDistributor> distributorFactory;
    private Http2Settings initialSettings;
    private Http2ClientSslContext sslContext;
    private boolean validateHeaders;
    private SensitivityDetector headerSensitivityDetector;

    public Http2ClientCodecBuilder maxReservedStreams(int maxReservedStreams) {
        return null;
    }

    public Http2ClientCodecBuilder validateHeaders(boolean validateHeaders) {
        this.validateHeaders = validateHeaders;
        return this;
    }

    public Http2ClientCodecBuilder encoderEnforceMaxConcurrentStreams(boolean encoderEnforceMaxConcurrentStreams) {
        return null;
    }

    public Http2ClientCodecBuilder encoderEnforceMaxQueuedControlFrames(int maxQueuedControlFrames) {
        return null;
    }

    public Http2ClientCodecBuilder headerSensitivityDetector(SensitivityDetector headerSensitivityDetector) {
        this.headerSensitivityDetector = checkNotNullWithIAE(headerSensitivityDetector, "headerSensitivityDetector");
        return this;
    }

    public Http2ClientCodecBuilder encoderIgnoreMaxHeaderListSize(boolean ignoreMaxHeaderListSize) {
        return null;
    }

    public Http2ClientCodecBuilder autoAckSettingsFrame(boolean autoAckSettings) {
        return null;
    }

    public Http2ClientCodecBuilder autoAckPingFrame(boolean autoAckPingFrame) {
        return null;
    }

    public Http2ClientCodecBuilder decoupleCloseAndGoAway(boolean decoupleCloseAndGoAway) {
        return null;
    }

    public Http2ClientCodecBuilder decoderEnforceMaxConsecutiveEmptyDataFrames(int maxConsecutiveEmptyFrames) {
        return null;
    }

    public Http2ClientCodecBuilder initialSettings(Http2Settings settings) {
        this.initialSettings = settings;
        return this;
    }

    public Http2ClientCodecBuilder sslContext(Http2ClientSslContext sslContext) {
        this.sslContext = checkNotNullWithIAE(sslContext, "sslContext");
        return this;
    }

    public Http2ClientCodecBuilder channelFlowControlledBytesDistributor(
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
                ch.pipeline().addLast(new EnsureByteBufOutbound());
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
                ch.pipeline().addLast(new Http2ControlStreamInitializer(settings, distributor, false));
                ch.pipeline().addLast(framesValidator);
                ch.pipeline().addLast(handler);
            }
        });
    }

    public ChannelHandler build(Http2ClientChannelInitializer initializer) {
        checkNotNullWithIAE(initializer, "initializer");

        final Http2Settings settings = initialSettings;
        final boolean validateHeaders = this.validateHeaders;
        final SensitivityDetector headerSensitivityDetector = this.headerSensitivityDetector;
        final Function<Channel, ChannelFlowControlledBytesDistributor> distributorFactory = this.distributorFactory;
        return build0(sslContext, new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new EnsureByteBufOutbound());
                ch.pipeline().addLast(new Http2FrameDecoder(validateHeaders));
                final ChannelFlowControlledBytesDistributor distributor;
                if (distributorFactory == null) {
                    distributor = addDistributorForChannel(ch);
                } else {
                    distributor = distributorFactory.apply(ch);
                }

                DefaultHttp2Channel h2channel = new DefaultHttp2Channel(ch, distributor, false);
                ch.pipeline().addLast(new Http2ControlStreamInitializer(settings, h2channel, distributor, false));
                Http2ClientStreamMuxer muxer = distributor instanceof DefaultChannelFlowControlledBytesDistributor ?
                        new Http2ClientStreamMuxer(h2channel, headerSensitivityDetector,
                                (DefaultChannelFlowControlledBytesDistributor) distributor) :
                        new Http2ClientStreamMuxer(h2channel, headerSensitivityDetector);
                // Muxer creates child streams which will write Buffer instances to the parent channel
                ch.pipeline().addLast(new EnsureByteBufOutbound());
                h2channel.pipeline().addLast(muxer);

                initializer.initialize(ch, h2channel);
            }
        });
    }

    private ChannelHandler build0(Http2ClientSslContext sslContext, ChannelInitializer<Channel> h2Initializer) {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                if (sslContext != null) {
                    ch.pipeline().addLast(sslContext.newHandler(ch.alloc()), sslContext.newApnHandler(h2Initializer));
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
    public interface Http2ClientChannelInitializer {
        void initialize(Channel rawChannel, Http2Channel http2Channel);
    }
}
