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
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.h2new.Http2ClientCodecBuilder.Http2ClientChannelInitializer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

import static io.netty.util.ReferenceCountUtil.release;

public class DefaultHttp2ClientChannelInitializer implements Http2ClientChannelInitializer {
    private final ChannelHandler controlStreamHandler;
    private final Promise<Http2Channel> http2ChannelPromise;

    public DefaultHttp2ClientChannelInitializer() {
        this(ReleaseAllHandler.RELEASE_ALL_HANDLER);
    }

    public DefaultHttp2ClientChannelInitializer(ChannelHandler controlStreamHandler) {
        this.controlStreamHandler = controlStreamHandler;
        http2ChannelPromise = ImmediateEventExecutor.INSTANCE.newPromise();
    }

    public final Future<Http2Channel> http2ChannelFuture(Future<Channel> connectFuture) {
        final Promise<Http2Channel> toReturn = connectFuture.executor().newPromise();
        connectFuture.addListener(future -> {
            if (!future.isSuccess()) {
                toReturn.setFailure(future.cause());
            }
        });
        http2ChannelPromise.addListener(future -> {
            if (future.isSuccess()) {
                toReturn.setSuccess(future.getNow());
            }
        });
        return toReturn;
    }

    @Override
    public final void initialize(Channel channel, Http2Channel http2Channel) {
        assert channel.executor().inEventLoop();

        http2Channel.pipeline().addLast(new ChannelHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (!(msg instanceof Http2StreamChannel)) {
                    ctx.fireChannelRead(msg);
                    return;
                }

                Http2StreamChannel stream = (Http2StreamChannel) msg;
                if (stream.streamId() == 0 && controlStreamHandler != null) {
                    stream.pipeline().addLast(controlStreamHandler);
                } else {
                    handlePushStream(stream);
                }
            }
        });
        http2ChannelPromise.setSuccess(http2Channel);
    }

    /**
     * Handle a new peer initiated {@link Http2StreamChannel}.
     *
     * @param stream New peer initiated {@link Http2StreamChannel}.
     */
    protected void handlePushStream(Http2StreamChannel stream) {
        stream.pipeline().addLast(new ChannelHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                release(msg);
            }
        });
        // Push streams not supported.
        stream.close();
    }

    private static final class ReleaseAllHandler extends ChannelHandlerAdapter {
        private static final ReleaseAllHandler RELEASE_ALL_HANDLER = new ReleaseAllHandler();

        private ReleaseAllHandler() {
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            release(msg);
        }

        @Override
        public boolean isSharable() {
            return true;
        }
    }
}
