/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http.websocketx.extensions;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import static io.netty5.util.internal.ObjectUtil.checkNonEmpty;

/**
 * This handler negotiates and initializes the WebSocket Extensions.
 * <p>
 * It negotiates the extensions based on the client desired order,
 * ensures that the successfully negotiated extensions are consistent between them,
 * and initializes the channel pipeline with the extension decoder and encoder.
 * <p>
 * Find a basic implementation for compression extensions at
 * <tt>io.netty5.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler</tt>.
 */
public class WebSocketServerExtensionHandler implements ChannelHandler {

    private final List<WebSocketServerExtensionHandshaker> extensionHandshakers;

    private final Queue<List<WebSocketServerExtension>> validExtensions =
            new ArrayDeque<List<WebSocketServerExtension>>(4);

    /**
     * Constructor
     *
     * @param extensionHandshakers
     *      The extension handshaker in priority order. A handshaker could be repeated many times
     *      with fallback configuration.
     */
    public WebSocketServerExtensionHandler(WebSocketServerExtensionHandshaker... extensionHandshakers) {
        this.extensionHandshakers = Arrays.asList(checkNonEmpty(extensionHandshakers, "extensionHandshakers"));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            List<WebSocketServerExtension> validExtensionsList = null;
            HttpRequest request = (HttpRequest) msg;

            if (WebSocketExtensionUtil.isWebsocketUpgrade(request.headers())) {
                CharSequence extensionsHeader = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);

                if (extensionsHeader != null) {
                    List<WebSocketExtensionData> extensions =
                            WebSocketExtensionUtil.extractExtensions(extensionsHeader);
                    int rsv = 0;

                    for (WebSocketExtensionData extensionData : extensions) {
                        Iterator<WebSocketServerExtensionHandshaker> extensionHandshakersIterator =
                                extensionHandshakers.iterator();
                        WebSocketServerExtension validExtension = null;

                        while (validExtension == null && extensionHandshakersIterator.hasNext()) {
                            WebSocketServerExtensionHandshaker extensionHandshaker =
                                    extensionHandshakersIterator.next();
                            validExtension = extensionHandshaker.handshakeExtension(extensionData);
                        }

                        if (validExtension != null && ((validExtension.rsv() & rsv) == 0)) {
                            if (validExtensionsList == null) {
                                validExtensionsList = new ArrayList<>(1);
                            }
                            rsv = rsv | validExtension.rsv();
                            validExtensionsList.add(validExtension);
                        }
                    }
                }
            }

            if (validExtensionsList == null) {
                validExtensionsList = Collections.emptyList();
            }
            validExtensions.offer(validExtensionsList);
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public Future<Void> write(final ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpResponse) {
            List<WebSocketServerExtension> validExtensionsList = validExtensions.poll();
            HttpResponse httpResponse = (HttpResponse) msg;
            //checking the status is faster than looking at headers
            //so we do this first
            if (HttpResponseStatus.SWITCHING_PROTOCOLS.equals(httpResponse.status())) {
                HttpHeaders headers = httpResponse.headers();

                FutureListener<Void> listener = null;
                if (WebSocketExtensionUtil.isWebsocketUpgrade(headers)) {
                    if (validExtensionsList != null && !validExtensionsList.isEmpty()) {
                        CharSequence headerValue = headers.get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);
                        List<WebSocketExtensionData> extraExtensions =
                                new ArrayList<>(extensionHandshakers.size());
                        for (WebSocketServerExtension extension : validExtensionsList) {
                            extraExtensions.add(extension.newResponseData());
                        }
                        String newHeaderValue = WebSocketExtensionUtil
                                .computeMergeExtensionsHeaderValue(headerValue, extraExtensions);
                        listener = future -> {
                            if (future.isSuccess()) {
                                for (WebSocketServerExtension extension : validExtensionsList) {
                                    WebSocketExtensionDecoder decoder = extension.newExtensionDecoder();
                                    WebSocketExtensionEncoder encoder = extension.newExtensionEncoder();
                                    String name = ctx.name();
                                    ctx.pipeline()
                                            .addAfter(name, decoder.getClass().getName(), decoder)
                                            .addAfter(name, encoder.getClass().getName(), encoder);
                                }
                            }
                        };

                        headers.set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, newHeaderValue);
                    }
                    Future<Void> f = ctx.write(httpResponse);
                    if (listener != null) {
                        f.addListener(listener);
                    }
                    f.addListener(future -> {
                        if (future.isSuccess()) {
                            ctx.pipeline().remove(this);
                        }
                    });
                    return f;
                }
            }
        }
        return ctx.write(msg);
    }
}
