/*
 * Copyright 2021 The Netty Project
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

package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.PromiseCombiner;

import java.net.SocketAddress;

/**
 * This handler converts from {@link Http3RequestStreamFrame} to {@link HttpObject},
 * and back. It can be used as an adapter in conjunction with {@link
 * Http3ServerConnectionHandler} or {@link Http3ClientConnectionHandler} to make http/3 connections
 * backward-compatible with {@link ChannelHandler}s expecting {@link HttpObject}.
 *
 * For simplicity, it converts to chunked encoding unless the entire stream
 * is a single header.
 */
public final class Http3FrameToHttpObjectCodec extends Http3RequestStreamInboundHandler
        implements ChannelOutboundHandler {

    private final boolean isServer;
    private final boolean validateHeaders;
    private boolean inboundTranslationInProgress;

    public Http3FrameToHttpObjectCodec(final boolean isServer,
                                       final boolean validateHeaders) {
        this.isServer = isServer;
        this.validateHeaders = validateHeaders;
    }

    public Http3FrameToHttpObjectCodec(final boolean isServer) {
        this(isServer, true);
    }

    @Override
    public boolean isSharable() {
        return false;
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
        Http3Headers headers = frame.headers();
        long id = ((QuicStreamChannel) ctx.channel()).streamId();

        final CharSequence status = headers.status();

        // 100-continue response is a special case where we should not send a fin,
        // but we need to decode it as a FullHttpResponse to play nice with HttpObjectAggregator.
        if (null != status && HttpResponseStatus.CONTINUE.codeAsText().contentEquals(status)) {
            final FullHttpMessage fullMsg = newFullMessage(id, headers, ctx.alloc());
            ctx.fireChannelRead(fullMsg);
            return;
        }

        if (headers.method() == null && status == null) {
            // Must be trailers!
            LastHttpContent last = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, validateHeaders);
            HttpConversionUtil.addHttp3ToHttpHeaders(id, headers, last.trailingHeaders(),
                    HttpVersion.HTTP_1_1, true, true);
            inboundTranslationInProgress = false;
            ctx.fireChannelRead(last);
        } else {
            HttpMessage req = newMessage(id, headers);
            if (!HttpUtil.isContentLengthSet(req)) {
                req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
            inboundTranslationInProgress = true;
            ctx.fireChannelRead(req);
        }
    }

    @Override
    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
        inboundTranslationInProgress = true;
        ctx.fireChannelRead(new DefaultHttpContent(frame.content()));
    }

    @Override
    protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
        if (inboundTranslationInProgress) {
            ctx.fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT);
        }
    }

    /**
     * Encode from an {@link HttpObject} to an {@link Http3RequestStreamFrame}. This method will
     * be called for each written message that can be handled by this encoder.
     *
     * NOTE: 100-Continue responses that are NOT {@link FullHttpResponse} will be rejected.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this handler belongs to
     * @param msg           the {@link HttpObject} message to encode
     * @throws Exception    is thrown if an error occurs
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof HttpObject)) {
            throw new UnsupportedMessageTypeException();
        }
        // 100-continue is typically a FullHttpResponse, but the decoded
        // Http3HeadersFrame should not handles as a end of stream.
        if (msg instanceof HttpResponse) {
            final HttpResponse res = (HttpResponse) msg;
            if (res.status().equals(HttpResponseStatus.CONTINUE)) {
                if (res instanceof FullHttpResponse) {
                    final Http3Headers headers = toHttp3Headers(res);
                    ctx.write(new DefaultHttp3HeadersFrame(headers), promise);
                    ((FullHttpResponse) res).release();
                    return;
                } else {
                    throw new EncoderException(
                            HttpResponseStatus.CONTINUE.toString() + " must be a FullHttpResponse");
                }
            }
        }

        // this combiner is created lazily if we need multiple write calls
        PromiseCombiner combiner = null;
        // With the last content, *if* we write anything here, we need to wait for that write to complete before
        // closing. To do that, we need to unvoid the promise. So if we write anything *and* this is the last message
        // we will unvoid.
        boolean isLast = msg instanceof LastHttpContent;

        if (msg instanceof HttpMessage) {
            Http3Headers headers = toHttp3Headers((HttpMessage) msg);
            DefaultHttp3HeadersFrame frame = new DefaultHttp3HeadersFrame(headers);

            if (msg instanceof HttpContent && (!promise.isVoid() || isLast)) {
                combiner = new PromiseCombiner(ctx.executor());
            }
            promise = writeWithOptionalCombiner(ctx, frame, promise, combiner, isLast);
        }

        if (isLast) {
            LastHttpContent last = (LastHttpContent) msg;
            boolean readable = last.content().isReadable();
            boolean hasTrailers = !last.trailingHeaders().isEmpty();

            if (combiner == null && readable && hasTrailers && !promise.isVoid()) {
                combiner = new PromiseCombiner(ctx.executor());
            }

            if (readable) {
                promise = writeWithOptionalCombiner(ctx,
                        new DefaultHttp3DataFrame(last.content()), promise, combiner, true);
            }
            if (hasTrailers) {
                Http3Headers headers = HttpConversionUtil.toHttp3Headers(last.trailingHeaders(), validateHeaders);
                promise = writeWithOptionalCombiner(ctx,
                        new DefaultHttp3HeadersFrame(headers), promise, combiner, true);
            }
            if (!readable) {
                last.release();
            }

            if (!readable && !hasTrailers && combiner == null) {
                // we had to write nothing. happy days!
                ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                promise.trySuccess();
            } else {
                promise.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
            }
        } else if (msg instanceof HttpContent) {
            promise = writeWithOptionalCombiner(ctx,
                    new DefaultHttp3DataFrame(((HttpContent) msg).content()), promise, combiner, false);
        }

        if (combiner != null) {
            combiner.finish(promise);
        }
    }

    /**
     * Write a message. If there is a combiner, add a new write promise to that combiner. If there is no combiner
     * ({@code null}), use the {@code outerPromise} directly as the write promise.
     */
    private static ChannelPromise writeWithOptionalCombiner(
            ChannelHandlerContext ctx,
            Object msg,
            ChannelPromise outerPromise,
            PromiseCombiner combiner,
            boolean unvoidPromise
    ) {
        if (unvoidPromise) {
            outerPromise = outerPromise.unvoid();
        }
        if (combiner == null) {
            ctx.write(msg, outerPromise);
        } else {
            combiner.add(ctx.write(msg));
        }
        return outerPromise;
    }

    private Http3Headers toHttp3Headers(HttpMessage msg) {
        if (msg instanceof HttpRequest) {
            msg.headers().set(
                    HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTPS);
        }

        return HttpConversionUtil.toHttp3Headers(msg, validateHeaders);
    }

    private HttpMessage newMessage(final long id,
                                   final Http3Headers headers) throws Http3Exception {
        return isServer ?
                HttpConversionUtil.toHttpRequest(id, headers, validateHeaders) :
                HttpConversionUtil.toHttpResponse(id, headers, validateHeaders);
    }

    private FullHttpMessage newFullMessage(final long id,
                                           final Http3Headers headers,
                                           final ByteBufAllocator alloc) throws Http3Exception {
        return isServer ?
                HttpConversionUtil.toFullHttpRequest(id, headers, alloc, validateHeaders) :
                HttpConversionUtil.toFullHttpResponse(id, headers, alloc, validateHeaders);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelPromise promise) {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }
}
