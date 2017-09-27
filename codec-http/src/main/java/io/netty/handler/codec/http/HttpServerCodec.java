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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * A combination of {@link HttpRequestDecoder} and {@link HttpResponseEncoder}
 * which enables easier server side HTTP implementation.
 *
 * @see HttpClientCodec
 */
public final class HttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder>
        implements HttpServerUpgradeHandler.SourceCodec {

    /** A queue that is used for correlating a request and a response. */
    private final Queue<HttpMethod> queue = new ArrayDeque<HttpMethod>();

    /**
     * Creates a new instance with the default decoder options
     * ({@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}).
     */
    public HttpServerCodec() {
        this(4096, 8192, 8192);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
        init(new HttpServerRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize),
                new HttpServerResponseEncoder());
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders) {
        init(new HttpServerRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders),
                new HttpServerResponseEncoder());
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders,
                           int initialBufferSize) {
        init(
          new HttpServerRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize,
                  validateHeaders, initialBufferSize),
          new HttpServerResponseEncoder());
    }

    /**
     * Upgrades to another protocol from HTTP. Removes the {@link HttpRequestDecoder} and
     * {@link HttpResponseEncoder} from the pipeline.
     */
    @Override
    public void upgradeFrom(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(this);
    }

    private final class HttpServerRequestDecoder extends HttpRequestDecoder {
        public HttpServerRequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize);
        }

        public HttpServerRequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
                                        boolean validateHeaders) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders);
        }

        public HttpServerRequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
                                        boolean validateHeaders, int initialBufferSize) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders, initialBufferSize);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
            int oldSize = out.size();
            super.decode(ctx, buffer, out);
            int size = out.size();
            for (int i = oldSize; i < size; i++) {
                Object obj = out.get(i);
                if (obj instanceof HttpRequest) {
                    queue.add(((HttpRequest) obj).method());
                }
            }
        }
    }

    private final class HttpServerResponseEncoder extends HttpResponseEncoder {

        private HttpMethod method;

        @Override
        protected void sanitizeHeadersBeforeEncode(HttpResponse msg, boolean isAlwaysEmpty) {
            if (!isAlwaysEmpty && method == HttpMethod.CONNECT && msg.status().codeClass() == HttpStatusClass.SUCCESS) {
                // Stripping Transfer-Encoding:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.1
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                return;
            }

            super.sanitizeHeadersBeforeEncode(msg, isAlwaysEmpty);
        }

        @Override
        protected boolean isContentAlwaysEmpty(@SuppressWarnings("unused") HttpResponse msg) {
            method = queue.poll();
            return HttpMethod.HEAD.equals(method) || super.isContentAlwaysEmpty(msg);
        }
    }
}
