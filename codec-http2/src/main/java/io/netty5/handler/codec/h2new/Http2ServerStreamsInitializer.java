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

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.h2new.Http2ServerCodecBuilder.Http2ServerChannelInitializer;

import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;

/**
 * An initializer of different {@link Http2StreamChannel}s for a server.
 */
public abstract class Http2ServerStreamsInitializer implements Http2ServerChannelInitializer {
    private final ChannelHandler controlStreamHandler;

    /**
     * Creates a new instance.
     */
    protected Http2ServerStreamsInitializer() {
        controlStreamHandler = null;
    }

    /**
     * Creates a new instance.
     *
     * @param controlStreamHandler {@link ChannelHandler} to add to the {@link Http2StreamChannel} with stream
     * identifier of {@code 0}.
     */
    protected Http2ServerStreamsInitializer(ChannelHandler controlStreamHandler) {
        this.controlStreamHandler = checkNotNullWithIAE(controlStreamHandler, "controlStreamHandler");
    }

    @Override
    public void initialize(Http2Channel channel) {
        channel.pipeline().addLast(new ChannelHandlerAdapter() {
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
                    handleRequestStream(stream);
                }
            }
        });
    }

    /**
     * Handle a new peer initiated {@link Http2StreamChannel}.
     *
     * @param stream New peer initiated {@link Http2StreamChannel}.
     */
    protected abstract void handleRequestStream(Http2StreamChannel stream);
}
