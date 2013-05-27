/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class CorsOutboundHandler extends ChannelOutboundHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CorsOutboundHandler.class);

    @Override
    public void write(final ChannelHandlerContext ctx, final MessageList<Object> msgs, final ChannelPromise promise)
            throws Exception {
        for (int i = 0; i < msgs.size(); i++) {
            final Object obj = msgs.get(i);
            if (obj instanceof HttpResponse) {
                final HttpResponse response = (HttpResponse) obj;
                final CorsMetadata cmd = ctx.channel().attr(CorsInboundHandler.CORS).get();
                if (cmd != null) {
                    response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, cmd.origin());
                    if (cmd.hasHeaders()) {
                        response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_HEADERS, cmd.headers());
                    }
                    response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                }
                logger.debug("Responding ..." + response.getStatus());
            }
            ctx.write(obj, promise);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("caught error in CorsOutboundHandler", cause);
        ctx.fireExceptionCaught(cause);
    }

}
