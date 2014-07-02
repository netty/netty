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

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import static io.netty.handler.codec.http2.Http2CodecUtil.*;

/**
 * Reads and writes the HTTP/2 connection preface, which must be the first bytes sent by both
 * endpoints upon successful establishment of an HTTP/2 connection. After receiving the preface from
 * the remote endpoint, this handler removes itself from the pipeline.
 *
 * https://tools.ietf.org/html/draft-ietf-httpbis-http2-12#section-3.5
 */
public class Http2PrefaceHandler extends ChannelHandlerAdapter {

    private final boolean server;
    private final ByteBuf preface = connectionPrefaceBuf();
    private boolean prefaceWritten;

    public Http2PrefaceHandler(boolean server) {
        this.server = server;
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
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (server) {
            // Only servers receive the preface string.
            if (preface.isReadable() && msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                processHttp2Preface(ctx, buf);
                if (preface.isReadable()) {
                    // More preface left to process.
                    buf.release();
                    return;
                }
            }
        }
        super.channelRead(ctx, msg);
    }

    /**
     * Sends the HTTP2 connection preface to the remote endpoint, if not already sent.
     */
    private void sendPreface(final ChannelHandlerContext ctx) {
        if (server) {
            // The preface string is only written by clients.
            return;
        }
        if (!prefaceWritten && ctx.channel().isActive()) {
            prefaceWritten = true;
            ctx.writeAndFlush(connectionPrefaceBuf()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess() && ctx.channel().isOpen()) {
                        // The write failed, close the connection.
                        ctx.close();
                    } else {
                        ctx.pipeline().remove(Http2PrefaceHandler.this);
                    }
                }
            });
        }
    }

    private void processHttp2Preface(ChannelHandlerContext ctx, ByteBuf in) {
        int prefaceRemaining = preface.readableBytes();
        int bytesRead = Math.min(in.readableBytes(), prefaceRemaining);

        // Read the portion of the input up to the length of the preface, if reached.
        ByteBuf sourceSlice = in.readSlice(bytesRead);

        // Read the same number of bytes from the preface buffer.
        ByteBuf prefaceSlice = preface.readSlice(bytesRead);

        // If the input so far doesn't match the preface, break the connection.
        if (bytesRead == 0 || !prefaceSlice.equals(sourceSlice)) {
            ctx.close();
            return;
        }

        if (!preface.isReadable()) {
            // Entire preface has been read, remove ourselves from the pipeline.
            ctx.pipeline().remove(this);
        }
    }
}
