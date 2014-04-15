/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10.frame.encoder;

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.connectionPrefaceBuf;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;

/**
 * Encodes {@link Http2Frame} objects and writes them to an output {@link ByteBuf}. The set of frame
 * types that is handled by this encoder is given by the {@link Http2FrameMarshaller}. By default,
 * the {@link Http2StandardFrameMarshaller} is used.
 *
 * @see Http2StandardFrameMarshaller
 */
public class Http2FrameEncoder extends MessageToByteEncoder<Http2Frame> {

    private final Http2FrameMarshaller frameMarshaller;
    private ChannelFutureListener prefaceWriteListener;
    private boolean prefaceWritten;

    public Http2FrameEncoder() {
        this(new Http2StandardFrameMarshaller());
    }

    public Http2FrameEncoder(Http2FrameMarshaller frameMarshaller) {
        if (frameMarshaller == null) {
            throw new NullPointerException("frameMarshaller");
        }
        this.frameMarshaller = frameMarshaller;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // The channel just became active - send the HTTP2 connection preface to the remote
        // endpoint.
        sendPreface(ctx);

        super.channelActive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // This handler was just added to the context. In case it was handled after
        // the connection became active, send the HTTP2 connection preface now.
        sendPreface(ctx);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Http2Frame frame, ByteBuf out)
            throws Exception {
        try {
            frameMarshaller.marshall(frame, out, ctx.alloc());
        } catch (Throwable t) {
            ctx.fireExceptionCaught(t);
        }
    }

    /**
     * Sends the HTTP2 connection preface to the remote endpoint, if not already sent.
     */
    private void sendPreface(final ChannelHandlerContext ctx) {
        if (!prefaceWritten && prefaceWriteListener == null && ctx.channel().isActive()) {
            prefaceWriteListener = new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        prefaceWritten = true;
                        prefaceWriteListener = null;
                    } else if (ctx.channel().isOpen()) {
                        // The write failed, close the connection.
                        ctx.close();
                    }
                }
            };
            ctx.writeAndFlush(connectionPrefaceBuf()).addListener(prefaceWriteListener);
        }
    }
}
