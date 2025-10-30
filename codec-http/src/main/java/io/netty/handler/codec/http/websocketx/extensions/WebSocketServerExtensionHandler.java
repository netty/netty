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
package io.netty.handler.codec.http.websocketx.extensions;

import static io.netty.util.internal.ObjectUtil.checkNonEmpty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

/**
 * This handler negotiates and initializes the WebSocket Extensions.
 *
 * It negotiates the extensions based on the client desired order,
 * ensures that the successfully negotiated extensions are consistent between them,
 * and initializes the channel pipeline with the extension decoder and encoder.
 *
 * Find a basic implementation for compression extensions at
 * <tt>io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler</tt>.
 */
public class WebSocketServerExtensionHandler extends ChannelDuplexHandler {

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
        // JDK type checks vs non-implemented interfaces costs O(N), where
        // N is the number of interfaces already implemented by the concrete type that's being tested.
        // The only requirement for this call is to make HttpRequest(s) implementors to call onHttpRequestChannelRead
        // and super.channelRead the others, but due to the O(n) cost we perform few fast-path for commonly met
        // singleton and/or concrete types, to save performing such slow type checks.
        if (msg != LastHttpContent.EMPTY_LAST_CONTENT) {
            if (msg instanceof DefaultHttpRequest) {
                // fast-path
                onHttpRequestChannelRead(ctx, (DefaultHttpRequest) msg);
            } else if (msg instanceof HttpRequest) {
                // slow path
                onHttpRequestChannelRead(ctx, (HttpRequest) msg);
            } else {
                super.channelRead(ctx, msg);
            }
        } else {
            super.channelRead(ctx, msg);
        }
    }

    /**
     * This is a method exposed to perform fail-fast checks of user-defined http types.<p>
     * eg:<br>
     * If the user has defined a specific {@link HttpRequest} type i.e.{@code CustomHttpRequest} and
     * {@link #channelRead} can receive {@link LastHttpContent#EMPTY_LAST_CONTENT} {@code msg}
     * types too, can override it like this:
     * <pre>
     *     public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
     *         if (msg != LastHttpContent.EMPTY_LAST_CONTENT) {
     *             if (msg instanceof CustomHttpRequest) {
     *                 onHttpRequestChannelRead(ctx, (CustomHttpRequest) msg);
     *             } else {
     *                 // if it's handling other HttpRequest types it MUST use onHttpRequestChannelRead again
     *                 // or have to delegate it to super.channelRead (that can perform redundant checks).
     *                 // If msg is not implementing HttpRequest, it can call ctx.fireChannelRead(msg) on it
     *                 // ...
     *                 super.channelRead(ctx, msg);
     *             }
     *         } else {
     *             // given that msg isn't a HttpRequest type we can just skip calling super.channelRead
     *             ctx.fireChannelRead(msg);
     *         }
     *     }
     * </pre>
     * <strong>IMPORTANT:</strong>
     * It already call {@code super.channelRead(ctx, request)} before returning.
     */
    protected void onHttpRequestChannelRead(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        List<WebSocketServerExtension> validExtensionsList = null;

        if (WebSocketExtensionUtil.isWebsocketUpgrade(request.headers())) {
            String extensionsHeader = request.headers().getAsString(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);

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
                            validExtensionsList = new ArrayList<WebSocketServerExtension>(1);
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
        super.channelRead(ctx, request);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg != Unpooled.EMPTY_BUFFER && !(msg instanceof ByteBuf)) {
            if (msg instanceof DefaultHttpResponse) {
                onHttpResponseWrite(ctx, (DefaultHttpResponse) msg, promise);
            } else if (msg instanceof HttpResponse) {
                onHttpResponseWrite(ctx, (HttpResponse) msg, promise);
            } else {
                super.write(ctx, msg, promise);
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

    /**
     * This is a method exposed to perform fail-fast checks of user-defined http types.<p>
     * eg:<br>
     * If the user has defined a specific {@link HttpResponse} type i.e.{@code CustomHttpResponse} and
     * {@link #write} can receive {@link ByteBuf} {@code msg} types too, it can be overridden like this:
     * <pre>
     *     public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
     *         if (msg != Unpooled.EMPTY_BUFFER && !(msg instanceof ByteBuf)) {
     *             if (msg instanceof CustomHttpResponse) {
     *                 onHttpResponseWrite(ctx, (CustomHttpResponse) msg, promise);
     *             } else {
     *                 // if it's handling other HttpResponse types it MUST use onHttpResponseWrite again
     *                 // or have to delegate it to super.write (that can perform redundant checks).
     *                 // If msg is not implementing HttpResponse, it can call ctx.write(msg, promise) on it
     *                 // ...
     *                 super.write(ctx, msg, promise);
     *             }
     *         } else {
     *             // given that msg isn't a HttpResponse type we can just skip calling super.write
     *             ctx.write(msg, promise);
     *         }
     *     }
     * </pre>
     * <strong>IMPORTANT:</strong>
     * It already call {@code super.write(ctx, response, promise)} before returning.
     */
    protected void onHttpResponseWrite(ChannelHandlerContext ctx, HttpResponse response, ChannelPromise promise)
            throws Exception {
        List<WebSocketServerExtension> validExtensionsList = validExtensions.poll();
        // checking the status is faster than looking at headers so we do this first
        if (HttpResponseStatus.SWITCHING_PROTOCOLS.equals(response.status())) {
            handlePotentialUpgrade(ctx, promise, response, validExtensionsList);
        }
        super.write(ctx, response, promise);
    }

    private void handlePotentialUpgrade(final ChannelHandlerContext ctx,
                                        ChannelPromise promise, HttpResponse httpResponse,
                                        final List<WebSocketServerExtension> validExtensionsList) {
        HttpHeaders headers = httpResponse.headers();

        if (WebSocketExtensionUtil.isWebsocketUpgrade(headers)) {
            if (validExtensionsList != null && !validExtensionsList.isEmpty()) {
                String headerValue = headers.getAsString(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);
                List<WebSocketExtensionData> extraExtensions =
                  new ArrayList<WebSocketExtensionData>(extensionHandshakers.size());
                for (WebSocketServerExtension extension : validExtensionsList) {
                    extraExtensions.add(extension.newReponseData());
                }
                String newHeaderValue = WebSocketExtensionUtil
                  .computeMergeExtensionsHeaderValue(headerValue, extraExtensions);
                promise.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
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
                    }
                });

                if (newHeaderValue != null) {
                    headers.set(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, newHeaderValue);
                }
            }

            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        ctx.pipeline().remove(WebSocketServerExtensionHandler.this);
                    }
                }
            });
        }
    }
}
