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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;

import static io.netty.util.ReferenceCountUtil.release;

/**
 * A {@link ChannelHandler} that reads {@link Http2Frame}s for a request stream
 * https://httpwg.org/specs/rfc7540.html#rfc.section.8.1
 */
public abstract class Http2RequestStreamInboundHandler extends ChannelHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Http2Frame)) {
            ctx.fireChannelRead(msg);
            return;
        }

        Http2Frame frame = (Http2Frame) msg;
        switch (frame.frameType()) {
            case Data:
                handleData((Http2DataFrame) frame);
                break;
            case Headers:
                handleHeaders((Http2HeadersFrame) frame);
                break;
            default:
                ReferenceCountUtil.release(msg);
        }
    }

    protected void handleHeaders(Http2HeadersFrame headersFrame) {
        release(headersFrame);
    }

    protected void handleData(Http2DataFrame dataFrame) {
        release(dataFrame);
    }

    protected void handleTrailers(Http2HeadersFrame trailersFrame) {
        release(trailersFrame);
    }
}
