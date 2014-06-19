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

import static io.netty.handler.codec.http.websocketx.WebSocketServerCompressionHandler.*;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class WebSocketClientCompressionHandler extends ChannelHandlerAdapter {

    private final int compressionLevel;
    private final boolean allowCustomClientWindowSize;
    private final int preferedServerWindowSize;

    public WebSocketClientCompressionHandler() {
        this(DEFAULT_COMPRESSION_LEVEL, false, DEFAULT_WINDOW_SIZE);
    }

    public WebSocketClientCompressionHandler(int compressionLevel,
            boolean allowCustomClientWindowSize, int preferedServerWindowSize) {
        if (preferedServerWindowSize > MAX_WINDOW_SIZE || preferedServerWindowSize < MIN_WINDOW_SIZE) {
            throw new IllegalArgumentException("preferedServerWindowSize");
        }
        this.compressionLevel = compressionLevel;
        this.allowCustomClientWindowSize = allowCustomClientWindowSize;
        this.preferedServerWindowSize = preferedServerWindowSize;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            if (WebSocketExtensionUtil.isWebsocketUpgrade(request)) {
                Map<String, String> parameters = new HashMap<String, String>();
                if (allowCustomClientWindowSize) {
                    parameters.put(CLIENT_MAX_WINDOW, null);
                }
                if (preferedServerWindowSize != DEFAULT_WINDOW_SIZE) {
                    parameters.put(SERVER_MAX_WINDOW, Integer.toString(preferedServerWindowSize));
                }

                String currentHeaderValue = request.headers().get(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS);
                String newHeaderValue = WebSocketExtensionUtil.appendExtension(currentHeaderValue,
                        PERMESSAGE_DEFLATE_EXTENSION, parameters);

                request.headers().set(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS, newHeaderValue);
            }
        }

        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;

            if (WebSocketExtensionUtil.isWebsocketUpgrade(response)) {
                String extensionsHeader = response.headers().get(HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS);

                if (extensionsHeader != null) {
                    Map<String, Map<String, String>> extensions =
                            WebSocketExtensionUtil.extractExtensions(extensionsHeader);

                    if (extensions.containsKey(PERMESSAGE_DEFLATE_EXTENSION)) {
                        Map<String, String> parameters = extensions.get(PERMESSAGE_DEFLATE_EXTENSION);
                        Iterator<Entry<String, String>> parametersIterator = parameters.entrySet().iterator();
                        boolean deflateEnabled = true;
                        int clientWindowSize = DEFAULT_WINDOW_SIZE;
                        int serverWindowSize = DEFAULT_WINDOW_SIZE;

                        while (deflateEnabled && parametersIterator.hasNext()) {
                            Entry<String, String> parameter = parametersIterator.next();

                            if (CLIENT_MAX_WINDOW.equalsIgnoreCase(parameter.getKey())) {
                                if (allowCustomClientWindowSize) {
                                    clientWindowSize = Integer.valueOf(parameter.getValue());
                                    if (clientWindowSize > MAX_WINDOW_SIZE || clientWindowSize < MIN_WINDOW_SIZE) {
                                        throw new IllegalStateException(
                                                "expected client_window_size=" + clientWindowSize + " out of range");
                                    }
                                } else {
                                    throw new IllegalStateException("unexpected parameter " + parameter.getKey());
                                }
                            } else if (SERVER_MAX_WINDOW.equalsIgnoreCase(parameter.getKey())) {
                                if (preferedServerWindowSize != Integer.parseInt(parameter.getValue())) {
                                    throw new IllegalStateException("unexpected parameter " + parameter.getKey());
                                }
                            } else {
                                throw new IllegalStateException("unexpected parameter " + parameter.getKey());
                            }
                        }

                        if (deflateEnabled) {
                            ctx.pipeline().addAfter(ctx.name(),
                                    WebSocketPermessageDeflateExtensionDecoder.class.getName(),
                                    new WebSocketPermessageDeflateExtensionDecoder());
                            ctx.pipeline().addAfter(ctx.name(),
                                    WebSocketPermessageDeflateExtensionEncoder.class.getName(),
                                    new WebSocketPermessageDeflateExtensionEncoder(compressionLevel, clientWindowSize));
                        }
                    }
                }

                ctx.pipeline().remove(this);
            }
        }

        super.channelRead(ctx, msg);
    }

}
