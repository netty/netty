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

package io.netty.handler.codec.http2.draft10.frame.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.draft10.Http2Exception;

import static io.netty.handler.codec.http2.draft10.Http2Error.*;
import static io.netty.handler.codec.http2.draft10.Http2Exception.*;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.*;

/**
 * Reads the initial client preface, then removes itself from the pipeline.
 * Only the server pipeline should do this.
 *
 * https://tools.ietf.org/html/draft-ietf-httpbis-http2-10#section-3.5
 */
public class Http2ServerPrefaceReader extends ChannelHandlerAdapter {

    private final ByteBuf preface = connectionPrefaceBuf();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (preface.isReadable() && msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            processHttp2Preface(ctx, buf);
            if (preface.isReadable()) {
                // More preface left to process.
                buf.release();
                return;
            }
        }
        super.channelRead(ctx, msg);
    }

    private void processHttp2Preface(ChannelHandlerContext ctx, ByteBuf in) throws Http2Exception {
        int prefaceRemaining = preface.readableBytes();
        int bytesRead = Math.min(in.readableBytes(), prefaceRemaining);

        // Read the portion of the input up to the length of the preface, if reached.
        ByteBuf sourceSlice = in.readSlice(bytesRead);

        // Read the same number of bytes from the preface buffer.
        ByteBuf prefaceSlice = preface.readSlice(bytesRead);

        // If the input so far doesn't match the preface, break the connection.
        if (bytesRead == 0 || !prefaceSlice.equals(sourceSlice)) {
            throw format(PROTOCOL_ERROR, "Invalid HTTP2 preface");
        }

        if (!preface.isReadable()) {
            // Entire preface has been read, remove ourselves from the pipeline.
            ctx.pipeline().remove(this);
        }
    }

}
