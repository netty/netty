/*
 * Copyright 2014 The Netty Project
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

package io.netty5.handler.codec.http2;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.adaptor.ByteBufAdaptor;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.EmptyHttpHeaders;
import io.netty5.handler.codec.http.FullHttpMessage;
import io.netty5.handler.codec.http.HttpContent;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpScheme;
import io.netty5.handler.codec.http.LastHttpContent;
import io.netty5.handler.codec.http2.Http2CodecUtil.SimpleChannelPromiseAggregator;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.UnstableApi;

/**
 * Translates HTTP/1.x object writes into HTTP/2 frames.
 * <p>
 * See {@link InboundHttp2ToHttpAdapter} to get translation from HTTP/2 frames to HTTP/1.x objects.
 */
@UnstableApi
public class HttpToHttp2ConnectionHandler extends Http2ConnectionHandler {

    private final boolean validateHeaders;
    private int currentStreamId;
    private HttpScheme httpScheme;

    protected HttpToHttp2ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                           Http2Settings initialSettings, boolean validateHeaders) {
        super(decoder, encoder, initialSettings);
        this.validateHeaders = validateHeaders;
    }

    protected HttpToHttp2ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                           Http2Settings initialSettings, boolean validateHeaders,
                                           boolean decoupleCloseAndGoAway) {
        this(decoder, encoder, initialSettings, validateHeaders, decoupleCloseAndGoAway, null);
    }

    protected HttpToHttp2ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                           Http2Settings initialSettings, boolean validateHeaders,
                                           boolean decoupleCloseAndGoAway, HttpScheme httpScheme) {
        super(decoder, encoder, initialSettings, decoupleCloseAndGoAway);
        this.validateHeaders = validateHeaders;
        this.httpScheme = httpScheme;
    }

    /**
     * Get the next stream id either from the {@link HttpHeaders} object or HTTP/2 codec
     *
     * @param httpHeaders The HTTP/1.x headers object to look for the stream id
     * @return The stream id to use with this {@link HttpHeaders} object
     * @throws Exception If the {@code httpHeaders} object specifies an invalid stream id
     */
    private int getStreamId(HttpHeaders httpHeaders) throws Exception {
        return httpHeaders.getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(),
                                  connection().local().incrementAndGetNextStreamId());
    }

    /**
     * Handles conversion of {@link HttpMessage} and {@link HttpContent} to HTTP/2 frames.
     */
    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof HttpMessage || msg instanceof HttpContent)) {
            return ctx.write(msg);
        }

        boolean release = true;
        Promise<Void> promise = ctx.newPromise();
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(promise, ctx.executor());
        try {
            Http2ConnectionEncoder encoder = encoder();
            boolean endStream = false;
            if (msg instanceof HttpMessage) {
                final HttpMessage httpMsg = (HttpMessage) msg;

                // Provide the user the opportunity to specify the streamId
                currentStreamId = getStreamId(httpMsg.headers());

                // Add HttpScheme if it's defined in constructor and header does not contain it.
                if (httpScheme != null &&
                        !httpMsg.headers().contains(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text())) {
                    httpMsg.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), httpScheme.name());
                }

                // Convert and write the headers.
                Http2Headers http2Headers = HttpConversionUtil.toHttp2Headers(httpMsg, validateHeaders);
                endStream = msg instanceof FullHttpMessage && ((FullHttpMessage<?>) msg).payload().readableBytes() == 0;
                writeHeaders(ctx, encoder, currentStreamId, httpMsg.headers(), http2Headers,
                        endStream).cascadeTo(promiseAggregator.newPromise());
            }

            if (!endStream && msg instanceof HttpContent) {
                boolean isLastContent = false;
                HttpHeaders trailers = EmptyHttpHeaders.INSTANCE;
                Http2Headers http2Trailers = EmptyHttp2Headers.INSTANCE;
                if (msg instanceof LastHttpContent) {
                    isLastContent = true;

                    // Convert any trailing headers.
                    final LastHttpContent<?> lastContent = (LastHttpContent<?>) msg;
                    trailers = lastContent.trailingHeaders();
                    http2Trailers = HttpConversionUtil.toHttp2Headers(trailers, validateHeaders);
                }

                // Write the data
                final Buffer payload = ((HttpContent<?>) msg).payload();
                final ByteBuf content = ByteBufAdaptor.intoByteBuf(payload);
                endStream = isLastContent && trailers.isEmpty();
                encoder.writeData(ctx, currentStreamId, content, 0, endStream)
                        .cascadeTo(promiseAggregator.newPromise());
                release = false;

                if (!trailers.isEmpty()) {
                    // Write trailing headers.
                    writeHeaders(ctx, encoder, currentStreamId, trailers, http2Trailers, true)
                            .cascadeTo(promiseAggregator.newPromise());
                }
            }
        } catch (Throwable t) {
            onError(ctx, true, t);
            promiseAggregator.setFailure(t);
        } finally {
            if (release) {
                ReferenceCountUtil.release(msg);
            }
            promiseAggregator.doneAllocatingPromises();
        }
        return promise.asFuture();
    }

    private static Future<Void> writeHeaders(ChannelHandlerContext ctx, Http2ConnectionEncoder encoder, int streamId,
                                     HttpHeaders headers, Http2Headers http2Headers, boolean endStream) {
        int dependencyId = headers.getInt(
                HttpConversionUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(), 0);
        short weight = headers.getShort(
                HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT);
        return encoder.writeHeaders(ctx, streamId, http2Headers, dependencyId, weight, false,
                0, endStream);
    }
}
