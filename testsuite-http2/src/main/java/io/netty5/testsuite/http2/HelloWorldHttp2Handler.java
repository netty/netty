/*
 * Copyright 2017 The Netty Project
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

package io.netty5.testsuite.http2;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpScheme;
import io.netty5.handler.codec.http.HttpServerUpgradeHandler;
import io.netty5.handler.codec.http2.Http2ConnectionDecoder;
import io.netty5.handler.codec.http2.Http2ConnectionEncoder;
import io.netty5.handler.codec.http2.Http2ConnectionHandler;
import io.netty5.handler.codec.http2.Http2Flags;
import io.netty5.handler.codec.http2.Http2FrameListener;
import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.handler.codec.http2.headers.Http2Headers;

import java.util.function.Supplier;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A simple handler that responds with the message "Hello World!".
 */
public final class HelloWorldHttp2Handler extends Http2ConnectionHandler implements Http2FrameListener {

    static final Supplier<Buffer> RESPONSE_BYTES_SUPPLIER =
            preferredAllocator().constBufferSupplier("Hello World".getBytes(UTF_8));

    HelloWorldHttp2Handler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                           Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

    private static Http2Headers http1HeadersToHttp2Headers(FullHttpRequest request) {
        CharSequence host = request.headers().get(HttpHeaderNames.HOST);
        Http2Headers http2Headers = Http2Headers.newHeaders()
                .method(HttpMethod.GET.asciiName())
                .path(request.uri())
                .scheme(HttpScheme.HTTP.name());
        if (host != null) {
            http2Headers.authority(host);
        }
        return http2Headers;
    }

    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            HttpServerUpgradeHandler.UpgradeEvent upgradeEvent =
                    (HttpServerUpgradeHandler.UpgradeEvent) evt;
            onHeadersRead(ctx, 1, http1HeadersToHttp2Headers(upgradeEvent.upgradeRequest()), 0 , true);
        }
        super.channelInboundEvent(ctx, evt);
    }

    @Override
    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.channelExceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * Sends a "Hello World" DATA frame to the client.
     */
    private void sendResponse(ChannelHandlerContext ctx, int streamId, Buffer payload) {
        // Send a frame for the response status
        Http2Headers headers = Http2Headers.newHeaders().status(OK.codeAsText());
        encoder().writeHeaders(ctx, streamId, headers, 0, false);
        encoder().writeData(ctx, streamId, payload, 0, true);

        // no need to call flush as channelReadComplete(...) will take care of it.
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, Buffer data, int padding, boolean endOfStream) {
        int processed = data.readableBytes() + padding;
        if (endOfStream) {
            sendResponse(ctx, streamId, data.copy());
        }
        return processed;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                              Http2Headers headers, int padding, boolean endOfStream) {
        if (endOfStream) {
            final Buffer content = RESPONSE_BYTES_SUPPLIER.get().copy()
                    .writeCharSequence(" - via HTTP/2", US_ASCII);
            sendResponse(ctx, streamId, content);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endOfStream) {
        onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                               short weight, boolean exclusive) {
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) {
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) {
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, long data) {
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2Headers headers, int padding) {
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, Buffer debugData) {
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, short frameType, int streamId,
                               Http2Flags flags, Buffer payload) {
    }
}
