/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
// (BSD License: http://www.opensource.org/licenses/bsd-license)
//
// Copyright (c) 2011, Joe Walnes and contributors
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or
// without modification, are permitted provided that the
// following conditions are met:
//
// * Redistributions of source code must retain the above
// copyright notice, this list of conditions and the
// following disclaimer.
//
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the
// following disclaimer in the documentation and/or other
// materials provided with the distribution.
//
// * Neither the name of the Webbit nor the names of
// its contributors may be used to endorse or promote products
// derived from this software without specific prior written
// permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
// GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
// OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package io.netty.handler.codec.http.websocketx;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class WebsocketPremessageDeflateExtensionHandler extends ChannelHandlerAdapter {

    public static final int RSV1 = 0x04;
    public static final byte[] FRAME_TAIL = new byte[] {0x00, 0x00, (byte) 0xff, (byte) 0xff};

    private static final String DEFLATE_HEADER = "permessage-deflate";
    private static final String CLIENT_MAX_WINDOW = "client_max_window_bits";
    private static final String SERVER_MAX_WINDOW = "server_max_window_bits";
    private static final String CLIENT_NO_CONTEXT = "client_no_context_takeover";
    private static final String SERVER_NO_CONTEXT = "server_no_context_takeover";

    private Boolean deflateEnabled;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            if (request.headers().contains(HttpHeaders.Names.CONNECTION) &&
                    HttpHeaders.Values.UPGRADE.equals(request.headers().get(HttpHeaders.Names.CONNECTION)) &&
                    request.headers().contains(HttpHeaders.Names.UPGRADE) &&
                    HttpHeaders.Values.WEBSOCKET.equals(request.headers().get(HttpHeaders.Names.UPGRADE))) {

                String extensionsHeader = request.headers().get(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS);
                if (extensionsHeader != null) {
                    Map<String, Map<String, String>> extensions =
                            WebSocketExtensionUtil.extractExtensions(extensionsHeader);

                    if (extensions.containsKey(DEFLATE_HEADER)) {
                        deflateEnabled = true;
                        Map<String, String> parameters = extensions.get(DEFLATE_HEADER);
                        Iterator<Entry<String, String>> parametersIterator = parameters.entrySet().iterator();

                        while (deflateEnabled && parametersIterator.hasNext()) {
                            String parameter = parametersIterator.next().getKey();
                            if (CLIENT_MAX_WINDOW.equalsIgnoreCase(parameter)) {
                                // TODO: handle client_max_window_bits - nothing to do if ignored
                            } else if (SERVER_MAX_WINDOW.equalsIgnoreCase(parameter)) {
                                // TODO: handle server_max_window_bits
                                deflateEnabled = false;
                            } else if (CLIENT_NO_CONTEXT.equalsIgnoreCase(parameter)) {
                                // TODO: handle client_no_context_takeover - nothing to do if ignored
                            } else if (SERVER_NO_CONTEXT.equalsIgnoreCase(parameter)) {
                                // TODO: handle server_no_context_takeover
                                deflateEnabled = false;
                            } else {
                                deflateEnabled = false;
                            }
                        }
                    } else {
                        deflateEnabled = false;
                    }
                }
            }
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse && Boolean.TRUE.equals(deflateEnabled)) {
            HttpResponse response = (HttpResponse) msg;
            String currentHeaderValue = response.headers().get(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS);
            String newHeaderValue = WebSocketExtensionUtil.appendExtension(currentHeaderValue,
                    DEFLATE_HEADER, Collections.<String, String>emptyMap());

            response.headers().set(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS, newHeaderValue);

            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.pipeline().addAfter(ctx.name(),
                                WebsocketPremessageDeflateExtensionDecoder.class.getName(),
                                new WebsocketPremessageDeflateExtensionDecoder());
                        ctx.pipeline().addAfter(ctx.name(),
                                WebsocketPremessageDeflateExtensionEncoder.class.getName(),
                                new WebsocketPremessageDeflateExtensionEncoder());
                        ctx.pipeline().remove(WebsocketPremessageDeflateExtensionHandler.this);
                    }
                }
            });
        }

        super.write(ctx, msg, promise);
    }

}
