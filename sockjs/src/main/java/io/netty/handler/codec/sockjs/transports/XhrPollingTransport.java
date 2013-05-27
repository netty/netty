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
package io.netty.handler.codec.sockjs.transports;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Values.CLOSE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.sockjs.transports.Transports.CONTENT_TYPE_JAVASCRIPT;
import static io.netty.handler.codec.sockjs.transports.Transports.setDefaultHeaders;
import static io.netty.handler.codec.sockjs.transports.Transports.wrapWithLN;
import static io.netty.handler.codec.sockjs.transports.Transports.writeContent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class XhrPollingTransport extends ChannelOutboundHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(XhrPollingTransport.class);

    private final Config config;
    private final FullHttpRequest request;

    public XhrPollingTransport(final Config config, final FullHttpRequest request) {
        ArgumentUtil.checkNotNull(config, "config");
        ArgumentUtil.checkNotNull(request, "request");
        this.config = config;
        this.request = request;
        this.request.retain();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.debug("Added [" + ctx + "]");
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final MessageList<Object> msgs, final ChannelPromise promise)
            throws Exception {
        final int size = msgs.size();
        for (int i = 0; i < size; i ++) {
            final Object obj = msgs.get(i);
            if (obj instanceof Frame) {
                final Frame frame = (Frame) obj;
                final FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK);
                response.headers().set(CONNECTION, CLOSE);
                setDefaultHeaders(response, config, request);
                writeContent(response, wrapWithLN(frame.content()), CONTENT_TYPE_JAVASCRIPT);
                ctx.write(response, promise);
            }
        }
        msgs.releaseAllAndRecycle();
    }

    @Override
    public String toString() {
        return "XhrPollingTransport[config=" + config + "]";
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("caught exception : ", cause);
        ctx.fireExceptionCaught(cause);
    }

}

