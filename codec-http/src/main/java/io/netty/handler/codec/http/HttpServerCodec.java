/*
 * Copyright 2012 The Netty Project
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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;

/**
 * A combination of {@link HttpRequestDecoder} and {@link HttpResponseEncoder}
 * which enables easier server side HTTP implementation.
 *
 * <h3>Header Validation</h3>
 *
 * It is recommended to always enable header validation.
 * <p>
 * Without header validation, your system can become vulnerable to
 * <a href="https://cwe.mitre.org/data/definitions/113.html">
 *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
 * </a>.
 * <p>
 * This recommendation stands even when both peers in the HTTP exchange are trusted,
 * as it helps with defence-in-depth.
 *
 * @see HttpClientCodec
 */
public final class HttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder>
        implements HttpServerUpgradeHandler.SourceCodec {

    /**
     * The maximum number of pipelined requests we allow to be awaiting a response by default, before
     * decoding of further requests is rejected. This bounds the memory a single connection can force us
     * to hold onto if the peer pipelines requests without reading the corresponding responses.
     */
    static final int DEFAULT_MAX_PIPELINE_DEPTH = 128;

    private static final byte METHOD_FLAG_HEAD = 1;
    private static final byte METHOD_FLAG_CONNECT = 2;
    private static final byte METHOD_FLAG_OTHER = 3;

    // We only need 2 bits per request because we distinguish:
    // 01 = HEAD, 10 = CONNECT, 11 = other
    private static final int METHOD_FLAG_BITS = 2;
    private static final int INLINE_QUEUE_CAPACITY = Long.SIZE / METHOD_FLAG_BITS; // 32

    /**
     * FIFO of request method flags.
     *
     * The oldest entry is stored in the least-significant bits so poll is just a mask + unsigned shift.
     * This avoids allocation for the common case of <= 32 outstanding requests.
     *
     * Once more than {@link #INLINE_QUEUE_CAPACITY} requests are queued, additional entries are appended
     * to {@link #methodOverflowQueue}. Order is preserved by always draining the inline queue first.
     */
    private long methodQueue;
    private int methodQueueSize;
    private Queue<Byte> methodOverflowQueue;
    private final int maxPipelineDepth;

    /**
     * When set, the connection will be closed after the next response is written.
     */
    private boolean mustCloseAfterResponse;

    /**
     * Creates a new instance with the default decoder options
     * ({@code maxInitialLineLength (4096)}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}).
     */
    public HttpServerCodec() {
        this(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize));
    }

    /**
     * Creates a new instance with the specified decoder options.
     *
     * @deprecated Prefer the {@link #HttpServerCodec(HttpDecoderConfig)} constructor,
     * to always enable header validation.
     */
    @Deprecated
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setValidateHeaders(validateHeaders));
    }

    /**
     * Creates a new instance with the specified decoder options.
     *
     * @deprecated Prefer the {@link #HttpServerCodec(HttpDecoderConfig)} constructor, to always enable header
     * validation.
     */
    @Deprecated
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders,
                           int initialBufferSize) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setValidateHeaders(validateHeaders)
                .setInitialBufferSize(initialBufferSize));
    }

    /**
     * Creates a new instance with the specified decoder options.
     *
     * @deprecated Prefer the {@link #HttpServerCodec(HttpDecoderConfig)} constructor,
     * to always enable header validation.
     */
    @Deprecated
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders,
                           int initialBufferSize, boolean allowDuplicateContentLengths) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setValidateHeaders(validateHeaders)
                .setInitialBufferSize(initialBufferSize)
                .setAllowDuplicateContentLengths(allowDuplicateContentLengths));
    }

    /**
     * Creates a new instance with the specified decoder options.
     *
     * @deprecated Prefer the {@link #HttpServerCodec(HttpDecoderConfig)} constructor,
     * to always enable header validation.
     */
    @Deprecated
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders,
                           int initialBufferSize, boolean allowDuplicateContentLengths, boolean allowPartialChunks) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setValidateHeaders(validateHeaders)
                .setInitialBufferSize(initialBufferSize)
                .setAllowDuplicateContentLengths(allowDuplicateContentLengths)
                .setAllowPartialChunks(allowPartialChunks));
    }

    /**
     * Creates a new instance with the specified decoder configuration.
     */
    public HttpServerCodec(HttpDecoderConfig config) {
        this(config, DEFAULT_MAX_PIPELINE_DEPTH);
    }

    /**
     * Creates a new instance with the specified decoder configuration.
     *
     * @param config the decoder configuration.
     * @param maxPipelineDepth the maximum number of requests that may be decoded while awaiting the
     *                         corresponding responses to be written, before decoding of further requests
     *                         is rejected with an {@link IllegalStateException}.
     */
    public HttpServerCodec(HttpDecoderConfig config, int maxPipelineDepth) {
        this.maxPipelineDepth = ObjectUtil.checkPositive(maxPipelineDepth, "maxPipelineDepth");
        init(new HttpServerRequestDecoder(config), new HttpServerResponseEncoder());
    }

    /**
     * Upgrades to another protocol from HTTP. Removes the {@link HttpRequestDecoder} and
     * {@link HttpResponseEncoder} from the pipeline.
     */
    @Override
    public void upgradeFrom(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(this);
    }

    private boolean enqueueMethod(HttpMethod method) {
        Queue<Byte> overflowQueue = methodOverflowQueue;
        int currentDepth = methodQueueSize + (overflowQueue != null ? overflowQueue.size() : 0);
        if (currentDepth >= maxPipelineDepth) {
            return false;
        }

        final byte flag;
        if (HttpMethod.HEAD.equals(method)) {
            flag = METHOD_FLAG_HEAD;
        } else if (HttpMethod.CONNECT.equals(method)) {
            flag = METHOD_FLAG_CONNECT;
        } else {
            flag = METHOD_FLAG_OTHER;
        }

        // Once we have overflow, always append there until it drains completely.
        if (overflowQueue != null) {
            overflowQueue.add(flag);
            return true;
        }

        if (methodQueueSize < INLINE_QUEUE_CAPACITY) {
            methodQueue |= (long) flag << (methodQueueSize << 1);
            methodQueueSize++;
        } else {
            overflowQueue = new ArrayDeque<>(4);
            overflowQueue.add(flag);
            methodOverflowQueue = overflowQueue;
        }
        return true;
    }

    private byte pollMethod() {
        if (methodQueueSize != 0) {
            //(methodQueue & ((1L << METHOD_FLAG_BITS) - 1))
            byte flag = (byte) (methodQueue & 0x3L);
            methodQueue >>>= METHOD_FLAG_BITS;
            methodQueueSize--;
            return flag;
        }

        Queue<Byte> overflowQueue = methodOverflowQueue;
        if (overflowQueue != null) {
            Byte flag = overflowQueue.poll();
            if (overflowQueue.isEmpty()) {
                methodOverflowQueue = null;
            }
            return flag != null ? flag : METHOD_FLAG_OTHER;
        }

        return METHOD_FLAG_OTHER;
    }

    private final class HttpServerRequestDecoder extends HttpRequestDecoder {
        private boolean discard;

        HttpServerRequestDecoder(HttpDecoderConfig config) {
            super(config);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
            if (discard) {
                buffer.skipBytes(buffer.readableBytes());
                return;
            }
            int oldSize = out.size();
            super.decode(ctx, buffer, out);
            int size = out.size();
            for (int i = oldSize; i < size; i++) {
                Object obj = out.get(i);
                if (obj instanceof HttpRequest) {
                    if (!enqueueMethod(((HttpRequest) obj).method())) {
                        // We hit the limit, let's discard everything and release everything and also ensure
                        // we close the connection once the first response is written back.
                        mustCloseAfterResponse = true;
                        discard = true;
                        ReferenceCountUtil.release(obj);
                        while (++i < size) {
                            ReferenceCountUtil.release(out.get(i));
                        }
                        out.clear();
                        throw new IllegalStateException("maxPipelineDepth exceeded: " + maxPipelineDepth);
                    }
                }
            }
        }

        @Override
        protected void handleTransferEncodingChunkedWithContentLength(HttpMessage message) {
            super.handleTransferEncodingChunkedWithContentLength(message);
            mustCloseAfterResponse = true;
        }
    }

    private final class HttpServerResponseEncoder extends HttpResponseEncoder {

        private byte methodFlag;

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (mustCloseAfterResponse && msg instanceof LastHttpContent) {
                mustCloseAfterResponse = false;
                promise = promise.unvoid().addListener(ChannelFutureListener.CLOSE);
            }
            super.write(ctx, msg, promise);
        }

        @Override
        protected void sanitizeHeadersBeforeEncode(HttpResponse msg, boolean isAlwaysEmpty) {
            if (!isAlwaysEmpty && methodFlag == METHOD_FLAG_CONNECT
                    && msg.status().codeClass() == HttpStatusClass.SUCCESS) {
                // Stripping Transfer-Encoding:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.1
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                return;
            }

            super.sanitizeHeadersBeforeEncode(msg, isAlwaysEmpty);
        }

        @Override
        protected boolean isContentAlwaysEmpty(HttpResponse msg) {
            if (msg.status().codeClass() == HttpStatusClass.INFORMATIONAL) {
                // An informational response should be excluded from paired comparison. This covers 101 as well:
                // once the protocol is switched this handler is removed from the pipeline, so the entry that is
                // left behind goes away with it. Just delegate to super method which has all the needed handling.
                return super.isContentAlwaysEmpty(msg);
            }
            methodFlag = pollMethod();
            return methodFlag == METHOD_FLAG_HEAD || super.isContentAlwaysEmpty(msg);
        }
    }
}
