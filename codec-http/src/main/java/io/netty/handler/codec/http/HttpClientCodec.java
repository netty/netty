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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_ALLOW_PARTIAL_CHUNKS;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_VALIDATE_HEADERS;

/**
 * A combination of {@link HttpRequestEncoder} and {@link HttpResponseDecoder}
 * which enables easier client side HTTP implementation. {@link HttpClientCodec}
 * provides additional state management for <tt>HEAD</tt> and <tt>CONNECT</tt>
 * requests, which {@link HttpResponseDecoder} lacks.  Please refer to
 * {@link HttpResponseDecoder} to learn what additional state management needs
 * to be done for <tt>HEAD</tt> and <tt>CONNECT</tt> and why
 * {@link HttpResponseDecoder} can not handle it by itself.
 *
 * If the {@link Channel} is closed and there are missing responses,
 * a {@link PrematureChannelClosureException} is thrown.
 *
 * @see HttpServerCodec
 */
public final class HttpClientCodec extends CombinedChannelDuplexHandler<HttpResponseDecoder, HttpRequestEncoder>
        implements HttpClientUpgradeHandler.SourceCodec {
    public static final boolean DEFAULT_FAIL_ON_MISSING_RESPONSE = false;
    public static final boolean DEFAULT_PARSE_HTTP_AFTER_CONNECT_REQUEST = false;

    /** A queue that is used for correlating a request and a response. */
    private final Queue<HttpMethod> queue = new ArrayDeque<HttpMethod>();
    private final boolean parseHttpAfterConnectRequest;

    /** If true, decoding stops (i.e. pass-through) */
    private boolean done;

    private final AtomicLong requestResponseCounter = new AtomicLong();
    private final boolean failOnMissingResponse;

    /**
     * Creates a new instance with the default decoder options
     * ({@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}).
     */
    public HttpClientCodec() {
        this(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE,
             DEFAULT_FAIL_ON_MISSING_RESPONSE);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpClientCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, DEFAULT_FAIL_ON_MISSING_RESPONSE);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpClientCodec(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean failOnMissingResponse) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, failOnMissingResponse, DEFAULT_VALIDATE_HEADERS);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpClientCodec(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean failOnMissingResponse,
            boolean validateHeaders) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, failOnMissingResponse, validateHeaders,
             DEFAULT_PARSE_HTTP_AFTER_CONNECT_REQUEST);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpClientCodec(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean failOnMissingResponse,
            boolean validateHeaders, boolean parseHttpAfterConnectRequest) {
        init(new Decoder(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders), new Encoder());
        this.failOnMissingResponse = failOnMissingResponse;
        this.parseHttpAfterConnectRequest = parseHttpAfterConnectRequest;
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpClientCodec(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean failOnMissingResponse,
            boolean validateHeaders, int initialBufferSize) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, failOnMissingResponse, validateHeaders,
             initialBufferSize, DEFAULT_PARSE_HTTP_AFTER_CONNECT_REQUEST);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpClientCodec(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean failOnMissingResponse,
            boolean validateHeaders, int initialBufferSize, boolean parseHttpAfterConnectRequest) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, failOnMissingResponse, validateHeaders,
             initialBufferSize, parseHttpAfterConnectRequest, DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpClientCodec(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean failOnMissingResponse,
            boolean validateHeaders, int initialBufferSize, boolean parseHttpAfterConnectRequest,
            boolean allowDuplicateContentLengths) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, failOnMissingResponse, validateHeaders,
            initialBufferSize, parseHttpAfterConnectRequest, allowDuplicateContentLengths,
            DEFAULT_ALLOW_PARTIAL_CHUNKS);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpClientCodec(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean failOnMissingResponse,
            boolean validateHeaders, int initialBufferSize, boolean parseHttpAfterConnectRequest,
            boolean allowDuplicateContentLengths, boolean allowPartialChunks) {
        init(new Decoder(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders, initialBufferSize,
                         allowDuplicateContentLengths, allowPartialChunks),
             new Encoder());
        this.parseHttpAfterConnectRequest = parseHttpAfterConnectRequest;
        this.failOnMissingResponse = failOnMissingResponse;
    }

    /**
     * Prepares to upgrade to another protocol from HTTP. Disables the {@link Encoder}.
     */
    @Override
    public void prepareUpgradeFrom(ChannelHandlerContext ctx) {
        ((Encoder) outboundHandler()).upgraded = true;
    }

    /**
     * Upgrades to another protocol from HTTP. Removes the {@link Decoder} and {@link Encoder} from
     * the pipeline.
     */
    @Override
    public void upgradeFrom(ChannelHandlerContext ctx) {
        final ChannelPipeline p = ctx.pipeline();
        p.remove(this);
    }

    public void setSingleDecode(boolean singleDecode) {
        inboundHandler().setSingleDecode(singleDecode);
    }

    public boolean isSingleDecode() {
        return inboundHandler().isSingleDecode();
    }

    private final class Encoder extends HttpRequestEncoder {

        boolean upgraded;

        @Override
        protected void encode(
                ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {

            if (upgraded) {
                // HttpObjectEncoder overrides .write and does not release msg, so we don't need to retain it here
                out.add(msg);
                return;
            }

            if (msg instanceof HttpRequest) {
                queue.offer(((HttpRequest) msg).method());
            }

            super.encode(ctx, msg, out);

            if (failOnMissingResponse && !done) {
                // check if the request is chunked if so do not increment
                if (msg instanceof LastHttpContent) {
                    // increment as its the last chunk
                    requestResponseCounter.incrementAndGet();
                }
            }
        }
    }

    private final class Decoder extends HttpResponseDecoder {
        Decoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders);
        }

        Decoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders,
                int initialBufferSize, boolean allowDuplicateContentLengths, boolean allowPartialChunks) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders, initialBufferSize,
                  allowDuplicateContentLengths, allowPartialChunks);
        }

        @Override
        protected void decode(
                ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
            if (done) {
                int readable = actualReadableBytes();
                if (readable == 0) {
                    // if non is readable just return null
                    // https://github.com/netty/netty/issues/1159
                    return;
                }
                out.add(buffer.readBytes(readable));
            } else {
                int oldSize = out.size();
                super.decode(ctx, buffer, out);
                if (failOnMissingResponse) {
                    int size = out.size();
                    for (int i = oldSize; i < size; i++) {
                        decrement(out.get(i));
                    }
                }
            }
        }

        private void decrement(Object msg) {
            if (msg == null) {
                return;
            }

            // check if it's an Header and its transfer encoding is not chunked.
            if (msg instanceof LastHttpContent) {
                requestResponseCounter.decrementAndGet();
            }
        }

        @Override
        protected boolean isContentAlwaysEmpty(HttpMessage msg) {
            // Get the method of the HTTP request that corresponds to the
            // current response.
            //
            // Even if we do not use the method to compare we still need to poll it to ensure we keep
            // request / response pairs in sync.
            HttpMethod method = queue.poll();

            final HttpResponseStatus status = ((HttpResponse) msg).status();
            final HttpStatusClass statusClass = status.codeClass();
            final int statusCode = status.code();
            if (statusClass == HttpStatusClass.INFORMATIONAL) {
                // An informational response should be excluded from paired comparison.
                // Just delegate to super method which has all the needed handling.
                return super.isContentAlwaysEmpty(msg);
            }

            // If the remote peer did for example send multiple responses for one request (which is not allowed per
            // spec but may still be possible) method will be null so guard against it.
            if (method != null) {
                char firstChar = method.name().charAt(0);
                switch (firstChar) {
                    case 'H':
                        // According to 4.3, RFC2616:
                        // All responses to the HEAD request method MUST NOT include a
                        // message-body, even though the presence of entity-header fields
                        // might lead one to believe they do.
                        if (HttpMethod.HEAD.equals(method)) {
                            return true;

                            // The following code was inserted to work around the servers
                            // that behave incorrectly.  It has been commented out
                            // because it does not work with well behaving servers.
                            // Please note, even if the 'Transfer-Encoding: chunked'
                            // header exists in the HEAD response, the response should
                            // have absolutely no content.
                            //
                            //// Interesting edge case:
                            //// Some poorly implemented servers will send a zero-byte
                            //// chunk if Transfer-Encoding of the response is 'chunked'.
                            ////
                            //// return !msg.isChunked();
                        }
                        break;
                    case 'C':
                        // Successful CONNECT request results in a response with empty body.
                        if (statusCode == 200) {
                            if (HttpMethod.CONNECT.equals(method)) {
                                // Proxy connection established - Parse HTTP only if configured by
                                // parseHttpAfterConnectRequest, else pass through.
                                if (!parseHttpAfterConnectRequest) {
                                    done = true;
                                    queue.clear();
                                }
                                return true;
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
            return super.isContentAlwaysEmpty(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx)
                throws Exception {
            super.channelInactive(ctx);

            if (failOnMissingResponse) {
                long missingResponses = requestResponseCounter.get();
                if (missingResponses > 0) {
                    ctx.fireExceptionCaught(new PrematureChannelClosureException(
                            "channel gone inactive with " + missingResponses +
                            " missing response(s)"));
                }
            }
        }
    }
}
