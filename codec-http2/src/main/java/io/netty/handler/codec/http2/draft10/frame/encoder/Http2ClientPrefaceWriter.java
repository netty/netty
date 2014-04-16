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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.*;

/**
 * Sends the initial client preface, then removes itself from the pipeline.
 * Only the client pipeline should do this.
 *
 * https://tools.ietf.org/html/draft-ietf-httpbis-http2-10#section-3.5
 */
public class Http2ClientPrefaceWriter extends ChannelHandlerAdapter {

    private boolean prefaceWritten;

    public Http2ClientPrefaceWriter() {
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

    /**
     * Sends the HTTP2 connection preface to the remote endpoint, if not already sent.
     */
    private void sendPreface(final ChannelHandlerContext ctx) {
        if (!prefaceWritten && ctx.channel().isActive()) {
            prefaceWritten = true;
            ctx.writeAndFlush(connectionPrefaceBuf()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess() && ctx.channel().isOpen()) {
                        // The write failed, close the connection.
                        ctx.close();
                    } else {
                        ctx.pipeline().remove(Http2ClientPrefaceWriter.this);
                    }
                }
            });
        }
    }
}
