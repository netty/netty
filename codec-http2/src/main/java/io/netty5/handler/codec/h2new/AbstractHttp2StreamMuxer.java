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

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.h2new.Http2ControlStreamInitializer.ControlStream;
import io.netty.handler.codec.http2.Http2HeadersEncoder.SensitivityDetector;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.handler.codec.h2new.Http2ControlStreamInitializer.CONTROL_STREAM_ATTRIBUTE_KEY;
import static io.netty.util.internal.ObjectUtil.checkNotNullWithIAE;

abstract class AbstractHttp2StreamMuxer extends ChannelHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractHttp2StreamMuxer.class);

    private final boolean isServer;
    private final IntObjectMap<DefaultHttp2StreamChannel> streams = new IntObjectHashMap<>();
    protected final DefaultHttp2Channel channel;
    protected final SensitivityDetector headerSensitivityDetector;
    protected final DefaultChannelFlowControlledBytesDistributor defaultDistributor;

    AbstractHttp2StreamMuxer(DefaultHttp2Channel channel, boolean isServer,
                             SensitivityDetector headerSensitivityDetector) {
        this.channel = checkNotNullWithIAE(channel, "channel");
        this.isServer = isServer;
        this.headerSensitivityDetector = headerSensitivityDetector;
        this.defaultDistributor = null;
    }

    AbstractHttp2StreamMuxer(DefaultHttp2Channel channel, boolean isServer,
                             SensitivityDetector headerSensitivityDetector,
                             DefaultChannelFlowControlledBytesDistributor defaultDistributor) {
        this.channel = checkNotNullWithIAE(channel, "channel");
        this.isServer = isServer;
        this.headerSensitivityDetector = headerSensitivityDetector;
        this.defaultDistributor = checkNotNullWithIAE(defaultDistributor, "defaultDistributor");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            initControlStream(ctx, false);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        initControlStream(ctx, true);
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Http2Frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        Http2Frame http2Frame = (Http2Frame) msg;
        final DefaultHttp2StreamChannel stream = createOrGetStream(http2Frame);

        stream.frameRead(http2Frame);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof DefaultHttp2StreamChannel) {
            DefaultHttp2StreamChannel stream = (DefaultHttp2StreamChannel) msg;
            streams.put(stream.streamId(), stream);
            initLocalInitiatedStream(stream);
            return ctx.newSucceededFuture();
        }
        return ctx.write(msg);
    }

    private void initControlStream(ChannelHandlerContext ctx, boolean fireChannelActive) {
        if (streams.get(0) != null) {
            // already initialized
            return;
        }
        ControlStream controlStream = ctx.channel().attr(CONTROL_STREAM_ATTRIBUTE_KEY).get();
        if (controlStream == null) {
            throw new IllegalStateException("Control stream channel attribute (key: " +
                    CONTROL_STREAM_ATTRIBUTE_KEY.name() + " ) not found.");
        }
        final DefaultHttp2StreamChannel controlChannel = controlStream.channel();
        streams.put(0, controlChannel);
        if (fireChannelActive) {
            ctx.fireChannelActive();
        }

        initControlStream(controlChannel);
        this.channel.pipeline().fireChannelRead(controlChannel);
        final Future<Void> register = controlChannel.register();
        if (register.isDone()) {
            handleControlStreamRegistrationDone(ctx, register, controlChannel);
            return;
        }
        register.addListener(future -> handleControlStreamRegistrationDone(ctx, register, controlChannel));
    }

    private DefaultHttp2StreamChannel createOrGetStream(Http2Frame frame) {
        DefaultHttp2StreamChannel stream = streams.get(frame.streamId());
        if (stream != null) {
            return stream;
        }
        // control stream should always be present in the streams map, so this should be request stream
        stream = new DefaultHttp2StreamChannel(channel, isServer, frame.streamId());
        streams.put(stream.streamId(), stream);
        initRemoteInitiatedStream(stream);
        channel.pipeline().fireChannelRead(stream);
        stream.register();
        return stream;
    }

    private static void handleControlStreamRegistrationDone(ChannelHandlerContext ctx, Future<Void> register,
                                                            Http2StreamChannel controlStream) {
        if (register.isSuccess()) {
            ctx.fireChannelRead(controlStream);
        } else {
            logger.error("Control stream registration failed, closing channel {}.", ctx.channel(),
                    register.cause());
            ctx.channel().close();
        }
    }

    protected abstract void initRemoteInitiatedStream(Http2StreamChannel stream);

    protected abstract void initLocalInitiatedStream(Http2StreamChannel stream);

    protected void initControlStream(Http2StreamChannel stream) {
        // Http2FrameEncoder currently writes out ByteBuf which DefaultHttp2StreamChannel does not understand, so
        // convert all ByteBuf to Buffer
        stream.pipeline().addLast(new EnsureBufferOutbound());
        stream.pipeline().addLast(headerSensitivityDetector == null ? new Http2FrameEncoder() :
                new Http2FrameEncoder(headerSensitivityDetector));
        stream.pipeline().addLast(new Http2ControlStreamFramesValidator());
    }
}
