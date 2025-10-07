/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineException;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.DecoderResult;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;

import java.net.SocketAddress;

/**
 * Decodes the content of the received {@link HttpRequest} and {@link HttpContent}.
 * The original content is replaced with the new content decoded by the
 * {@link ChannelDuplexHandler}, which is created by {@link #newContentDecoder}.
 * Once decoding is finished, the value of the <tt>'Content-Encoding'</tt>
 * header is set to the target content encoding, as returned by {@link #getTargetContentEncoding(String)}.
 * Also, the <tt>'Content-Length'</tt> header is updated to the length of the
 * decoded content.  If the content encoding of the original is not supported
 * by the decoder, {@link #newContentDecoder} should return {@code null}
 * so that no decoding occurs (i.e. pass-through).
 * <p>
 * Please note that this is an abstract class.  You have to extend this class
 * and implement {@link #newContentDecoder} properly to make this class
 * functional.  For example, refer to the source code of {@link HttpContentDecompressor}.
 * <p>
 * This handler must be placed after {@link HttpObjectDecoder} in the pipeline
 * so that this handler can intercept HTTP requests after {@link HttpObjectDecoder}
 * converts {@link ByteBuf}s into HTTP requests.
 * <p>
 * This class acts as a replacement for {@link HttpContentDecoder}, with two advantages: It does not use an
 * {@link EmbeddedChannel}, improving performance, and it correctly handles backpressure, so that decompressors will
 * not produce unrestricted amounts of output data before downstream handlers signal that they are ready to receive
 * this data.
 */
public abstract class BackpressureHttpContentDecoder extends ChannelDuplexHandler {
    private boolean continueResponse;
    private boolean needRead = true;

    private ChannelDuplexHandler decoder;
    private DelegatingChannelHandlerContext decoderContext;

    /**
     * Have we forwarded any input to the decoder? Before this happens, don't forward readComplete events to it.
     */
    private boolean decoderHasReceivedInput;
    /**
     * Have we forwarded some HTTP headers downstream? If so, we need to forward a readComplete downstream as well,
     * even if the decoder has not decoded any data yet, and thus won't send readComplete itself.
     */
    private boolean readCompleteForHeaders;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse && ((HttpResponse) msg).status().code() == 100) {

            if (!(msg instanceof LastHttpContent)) {
                continueResponse = true;
            }
            // 100-continue response must be passed through.
            needRead = false;
            ctx.fireChannelRead(msg);
            return;
        }

        if (continueResponse) {
            if (msg instanceof LastHttpContent) {
                continueResponse = false;
            }
            // 100-continue response must be passed through.
            needRead = false;
            ctx.fireChannelRead(msg);
            return;
        }

        if (msg instanceof HttpMessage) {
            cleanup();

            final HttpMessage message = (HttpMessage) msg;
            final HttpHeaders headers = message.headers();

            // Determine the content encoding.
            String contentEncoding = headers.get(HttpHeaderNames.CONTENT_ENCODING);
            if (contentEncoding != null) {
                contentEncoding = contentEncoding.trim();
            } else {
                String transferEncoding = headers.get(HttpHeaderNames.TRANSFER_ENCODING);
                if (transferEncoding != null) {
                    int idx = transferEncoding.indexOf(',');
                    if (idx != -1) {
                        contentEncoding = transferEncoding.substring(0, idx).trim();
                    } else {
                        contentEncoding = transferEncoding.trim();
                    }
                } else {
                    contentEncoding = HttpContentDecoder.IDENTITY;
                }
            }

            decoder = newContentDecoder(contentEncoding);

            if (decoder == null) {
                needRead = false;
                ctx.fireChannelRead(msg);
                return;
            }

            decoderContext = new WrappingChannelHandlerContext(ctx, decoder);
            decoder.handlerAdded(decoderContext);

            // Remove content-length header:
            // the correct value can be set only after all chunks are processed/decoded.
            // If buffering is not an issue, add HttpObjectAggregator down the chain, it will set the header.
            // Otherwise, rely on LastHttpContent message.
            if (headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
                headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
            // Either it is already chunked or EOF terminated.
            // See https://github.com/netty/netty/issues/5892

            // set new content encoding,
            CharSequence targetContentEncoding = getTargetContentEncoding(contentEncoding);
            if (HttpHeaderValues.IDENTITY.contentEquals(targetContentEncoding)) {
                // Do NOT set the 'Content-Encoding' header if the target encoding is 'identity'
                // as per: https://tools.ietf.org/html/rfc2616#section-14.11
                headers.remove(HttpHeaderNames.CONTENT_ENCODING);
            } else {
                headers.set(HttpHeaderNames.CONTENT_ENCODING, targetContentEncoding);
            }

            readCompleteForHeaders = true;
            if (message instanceof HttpContent) {
                // If message is a full request or response object (headers + data), don't copy data part into out.
                // Output headers only; data part will be decoded below.
                // Note: "copy" object must not be an instance of LastHttpContent class,
                // as this would (erroneously) indicate the end of the HttpMessage to other handlers.
                HttpMessage copy;
                if (message instanceof HttpRequest) {
                    HttpRequest r = (HttpRequest) message; // HttpRequest or FullHttpRequest
                    copy = new DefaultHttpRequest(r.protocolVersion(), r.method(), r.uri());
                } else if (message instanceof HttpResponse) {
                    HttpResponse r = (HttpResponse) message; // HttpResponse or FullHttpResponse
                    copy = new DefaultHttpResponse(r.protocolVersion(), r.status());
                } else {
                    throw new CodecException("Object of class " + message.getClass().getName() +
                            " is not an HttpRequest or HttpResponse");
                }
                copy.headers().set(message.headers());
                copy.setDecoderResult(message.decoderResult());
                needRead = false;
                ctx.fireChannelRead(copy);
            } else {
                needRead = false;
                ctx.fireChannelRead(message);
                decoderHasReceivedInput = false;
                return;
            }
        }

        if (msg instanceof HttpContent) {
            HttpContent c = (HttpContent) msg;
            if (decoder == null) {
                ctx.fireChannelRead(c);
            } else {
                decoderHasReceivedInput = true;
                decoder.channelRead(decoderContext, c.content());

                if (c instanceof LastHttpContent) {
                    decoder.channelInactive(decoderContext);
                    cleanup();

                    LastHttpContent last = (LastHttpContent) c;
                    // Generate an additional chunk if the decoder produced
                    // the last product on closure,
                    HttpHeaders headers = last.trailingHeaders();
                    needRead = false;
                    if (headers.isEmpty()) {
                        ctx.fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT);
                    } else {
                        ctx.fireChannelRead(new ComposedLastHttpContent(headers, DecoderResult.SUCCESS));
                    }
                }
            }
            return;
        }

        ReferenceCountUtil.release(msg);
    }

    private void cleanup() {
        if (decoder != null) {
            decoderContext.remove();
            decoderContext = null;
            decoder = null;
        }
    }

    protected abstract ChannelDuplexHandler newContentDecoder(String contentEncoding) throws Exception;

    /**
     * Returns the expected content encoding of the decoded content.
     * This getMethod returns {@code "identity"} by default, which is the case for
     * most decoders.
     *
     * @param contentEncoding the value of the {@code "Content-Encoding"} header
     * @return the expected content encoding of the new content
     */
    protected String getTargetContentEncoding(
            @SuppressWarnings("UnusedParameters") String contentEncoding) throws Exception {
        return HttpContentDecoder.IDENTITY;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null && decoderHasReceivedInput) {
            needRead = true;
            decoder.channelReadComplete(decoderContext);

            if (readCompleteForHeaders) {
                ctx.fireChannelReadComplete();
                readCompleteForHeaders = false;
            }
        } else {
            boolean needRead = this.needRead;
            this.needRead = true;
            readCompleteForHeaders = false;

            try {
                ctx.fireChannelReadComplete();
            } finally {
                if (needRead && !ctx.channel().config().isAutoRead()) {
                    ctx.read();
                }
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.channelInactive(ctx);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (decoder == null) {
            ctx.read();
        } else {
            decoder.read(decoderContext);
        }
    }

    private final class WrappingChannelHandlerContext extends DelegatingChannelHandlerContext {
        WrappingChannelHandlerContext(ChannelHandlerContext ctx, ChannelHandler handler) {
            super(ctx, handler);
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            return super.fireChannelRead(new DefaultHttpContent((ByteBuf) msg));
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            // This is sent at the end of an HTTP message. Don't forward
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            readCompleteForHeaders = false;
            return super.fireChannelReadComplete();
        }
    }

    private static class DelegatingChannelHandlerContext implements ChannelHandlerContext {

        private final ChannelHandlerContext ctx;
        private final ChannelHandler handler;
        boolean removed;

        DelegatingChannelHandlerContext(ChannelHandlerContext ctx, ChannelHandler handler) {
            this.ctx = ctx;
            this.handler = handler;
        }

        @Override
        public Channel channel() {
            return ctx.channel();
        }

        @Override
        public EventExecutor executor() {
            return ctx.executor();
        }

        @Override
        public String name() {
            return ctx.name();
        }

        @Override
        public ChannelHandler handler() {
            return ctx.handler();
        }

        @Override
        public boolean isRemoved() {
            return removed || ctx.isRemoved();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            ctx.fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            ctx.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            ctx.fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            ctx.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            ctx.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object event) {
            ctx.fireUserEventTriggered(event);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            ctx.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            ctx.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            ctx.fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            return ctx.bind(localAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            return ctx.connect(remoteAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return ctx.connect(remoteAddress, localAddress);
        }

        @Override
        public ChannelFuture disconnect() {
            return ctx.disconnect();
        }

        @Override
        public ChannelFuture close() {
            return ctx.close();
        }

        @Override
        public ChannelFuture deregister() {
            return ctx.deregister();
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            return ctx.bind(localAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            return ctx.connect(remoteAddress, promise);
        }

        @Override
        public ChannelFuture connect(
                SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            return ctx.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            return ctx.disconnect(promise);
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            return ctx.close(promise);
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return ctx.deregister(promise);
        }

        @Override
        public ChannelHandlerContext read() {
            ctx.read();
            return this;
        }

        @Override
        public ChannelFuture write(Object msg) {
            return ctx.write(msg);
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return ctx.write(msg, promise);
        }

        @Override
        public ChannelHandlerContext flush() {
            ctx.flush();
            return this;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return ctx.writeAndFlush(msg, promise);
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return ctx.writeAndFlush(msg);
        }

        @Override
        public ChannelPipeline pipeline() {
            return ctx.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return ctx.alloc();
        }

        @Override
        public ChannelPromise newPromise() {
            return ctx.newPromise();
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return ctx.newProgressivePromise();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return ctx.newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return ctx.newFailedFuture(cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            return ctx.voidPromise();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return ctx.channel().attr(key);
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return ctx.channel().hasAttr(key);
        }

        final void remove() {
            EventExecutor executor = executor();
            if (executor.inEventLoop()) {
                remove0();
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        remove0();
                    }
                });
            }
        }

        private void remove0() {
            if (!removed) {
                removed = true;
                try {
                    handler.handlerRemoved(this);
                } catch (Throwable cause) {
                    fireExceptionCaught(new ChannelPipelineException(
                            handler.getClass().getName() + ".handlerRemoved() has thrown an exception.", cause));
                }
            }
        }
    }
}
