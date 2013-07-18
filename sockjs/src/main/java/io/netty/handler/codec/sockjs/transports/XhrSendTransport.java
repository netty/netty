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

import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.sockjs.Config;

/**
 * Handles the send part the xhr-polling transport.
 */
public class XhrSendTransport extends AbstractSendTransport {

    public XhrSendTransport(final Config config) {
        super(config);
    }

    @Override
    public void respond(final ChannelHandlerContext ctx, final FullHttpRequest request) throws Exception {
        respond(ctx, request.getProtocolVersion(), NO_CONTENT, "");
    }

    @Override
    public String toString() {
        return "XhrSendTransport[config=" + config + "]";
    }
}
