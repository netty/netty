/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.http.websocketx.extensions;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionUtil.WebSocketExtensionData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WebSocketClientExtensionHandler extends ChannelHandlerAdapter {

    private final List<WebSocketClientExtensionHandshaker> extensionHandshakers;

    public WebSocketClientExtensionHandler(List<WebSocketClientExtensionHandshaker> extensionHandshakers) {
        if (extensionHandshakers == null) {
            throw new NullPointerException("extensionHandshakers");
        }
        this.extensionHandshakers = new ArrayList<WebSocketClientExtensionHandshaker>(extensionHandshakers);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpRequest && WebSocketExtensionUtil.isWebsocketUpgrade((HttpRequest) msg)) {
            HttpRequest request = (HttpRequest) msg;
            String headerValue = request.headers().get(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS);

            for (WebSocketClientExtensionHandshaker extentionHandshaker : extensionHandshakers) {
                WebSocketExtensionData extensionData = extentionHandshaker.newRequestData();
                headerValue = WebSocketExtensionUtil.appendExtension(headerValue,
                        extensionData.getName(), extensionData.getParameters());
            }

            request.headers().set(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS, headerValue);
        }

        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            if (WebSocketExtensionUtil.isWebsocketUpgrade(request)) {
                String extensionsHeader = request.headers().get(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS);

                if (extensionsHeader != null) {
                    List<WebSocketExtensionData> extensions =
                            WebSocketExtensionUtil.extractExtensions(extensionsHeader);
                    int rsv = 0;

                    for (WebSocketExtensionData extensionData : extensions) {
                        Iterator<WebSocketClientExtensionHandshaker> extensionHandshakersIterator =
                                extensionHandshakers.iterator();
                        WebSocketClientExtension validExtension = null;

                        while (validExtension == null && extensionHandshakersIterator.hasNext()) {
                            WebSocketClientExtensionHandshaker extensionHandshaker =
                                    extensionHandshakersIterator.next();
                            validExtension = extensionHandshaker.handshakeExtension(extensionData);
                        }

                        if (validExtension != null && ((validExtension.rsv() & rsv) == 0)) {
                            rsv = rsv | validExtension.rsv();

                            WebSocketExtensionDecoder decoder = validExtension.createExtensionDecoder();
                            WebSocketExtensionEncoder encoder = validExtension.createExtensionEncoder();
                            ctx.pipeline().addAfter(ctx.name(), decoder.getClass().getName(), decoder);
                            ctx.pipeline().addAfter(ctx.name(), encoder.getClass().getName(), encoder);
                        }
                    }
                }

                ctx.pipeline().remove(ctx.name());
            }
        }

        super.channelRead(ctx, msg);
    }

}
