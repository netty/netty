/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.StringUtil;

/**
 * A {@link ChannelDuplexHandler} providing additional functionality for HTTP/2. Specifically it allows to:
 * <ul>
 *     <li>Create new outbound streams using {@link #newStream()}.</li>
 *     <li>Iterate over all active streams using {@link #forEachActiveStream(Http2FrameStreamVisitor)}.</li>
 * </ul>
 *
 * <p>The {@link Http2FrameCodec} is required to be part of the {@link ChannelPipeline} before this handler is added,
 * or else an {@link IllegalStateException} will be thrown.
 */
public abstract class Http2ChannelDuplexHandler extends ChannelDuplexHandler {

    private volatile Http2FrameCodec frameCodec;

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        frameCodec = requireHttp2FrameCodec(ctx);
        handlerAdded0(ctx);
    }

    protected void handlerAdded0(@SuppressWarnings("unused") ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            handlerRemoved0(ctx);
        } finally {
            frameCodec = null;
        }
    }

    protected void handlerRemoved0(@SuppressWarnings("unused") ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * Creates a new {@link Http2FrameStream} object.
     *
     * <p>This method is <em>thread-safe</em>.
     */
    public final Http2FrameStream newStream() {
        Http2FrameCodec codec = frameCodec;
        if (codec == null) {
            throw new IllegalStateException(StringUtil.simpleClassName(Http2FrameCodec.class) + " not found." +
                    " Has the handler been added to a pipeline?");
        }
        return codec.newStream();
    }

    /**
     * Allows to iterate over all currently active streams.
     *
     * <p>This method may only be called from the eventloop thread.
     */
    protected final void forEachActiveStream(Http2FrameStreamVisitor streamVisitor) throws Http2Exception {
        frameCodec.forEachActiveStream(streamVisitor);
    }

    private static Http2FrameCodec requireHttp2FrameCodec(ChannelHandlerContext ctx) {
        ChannelHandlerContext frameCodecCtx = ctx.pipeline().context(Http2FrameCodec.class);
        if (frameCodecCtx == null) {
            throw new IllegalArgumentException(Http2FrameCodec.class.getSimpleName()
                                               + " was not found in the channel pipeline.");
        }
        return (Http2FrameCodec) frameCodecCtx.handler();
    }
}
