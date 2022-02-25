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
package io.netty5.handler.codec.http;

import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.adaptor.ByteBufAdaptor;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.MessageToMessageCodec;
import io.netty5.handler.codec.compression.Compressor;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.internal.StringUtil;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.extractOrCopy;
import static io.netty5.handler.codec.http.HttpHeaderNames.ACCEPT_ENCODING;
import static java.util.Objects.requireNonNull;

/**
 * Encodes the content of the outbound {@link HttpResponse} and {@link HttpContent}.
 * The original content is replaced with the new content encoded by the
 * {@link EmbeddedChannel}, which is created by {@link #beginEncode(HttpResponse, String)}.
 * Once encoding is finished, the value of the <tt>'Content-Encoding'</tt> header
 * is set to the target content encoding, as returned by
 * {@link #beginEncode(HttpResponse, String)}.
 * Also, the <tt>'Content-Length'</tt> header is updated to the length of the
 * encoded content.  If there is no supported or allowed encoding in the
 * corresponding {@link HttpRequest}'s {@code "Accept-Encoding"} header,
 * {@link #beginEncode(HttpResponse, String)} should return {@code null} so that
 * no encoding occurs (i.e. pass-through).
 * <p>
 * Please note that this is an abstract class.  You have to extend this class
 * and implement {@link #beginEncode(HttpResponse, String)} properly to make
 * this class functional.  For example, refer to the source code of
 * {@link HttpContentCompressor}.
 * <p>
 * This handler must be placed after {@link HttpObjectEncoder} in the pipeline
 * so that this handler can intercept HTTP responses before {@link HttpObjectEncoder}
 * converts them into {@link Buffer}s.
 */
public abstract class HttpContentEncoder extends MessageToMessageCodec<HttpRequest, HttpObject> {

    private enum State {
        PASS_THROUGH,
        AWAIT_HEADERS,
        AWAIT_CONTENT
    }

    private static final CharSequence ZERO_LENGTH_HEAD = "HEAD";
    private static final CharSequence ZERO_LENGTH_CONNECT = "CONNECT";
    private static final int CONTINUE_CODE = HttpResponseStatus.CONTINUE.code();

    private final Queue<CharSequence> acceptEncodingQueue = new ArrayDeque<>();
    private Compressor compressor;
    private State state = State.AWAIT_HEADERS;

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof HttpContent || msg instanceof HttpResponse;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
        CharSequence acceptEncoding;
        List<String> acceptEncodingHeaders = msg.headers().getAll(ACCEPT_ENCODING);
        switch (acceptEncodingHeaders.size()) {
        case 0:
            acceptEncoding = HttpContentDecoder.IDENTITY;
            break;
        case 1:
            acceptEncoding = acceptEncodingHeaders.get(0);
            break;
        default:
            // Multiple message-header fields https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2
            acceptEncoding = StringUtil.join(",", acceptEncodingHeaders);
            break;
        }

        HttpMethod method = msg.method();
        if (HttpMethod.HEAD.equals(method)) {
            acceptEncoding = ZERO_LENGTH_HEAD;
        } else if (HttpMethod.CONNECT.equals(method)) {
            acceptEncoding = ZERO_LENGTH_CONNECT;
        }

        acceptEncodingQueue.add(acceptEncoding);
        ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        final boolean isFull = msg instanceof HttpResponse && msg instanceof LastHttpContent;
        switch (state) {
            case AWAIT_HEADERS: {
                ensureHeaders(msg);
                assert compressor == null;
                assert msg instanceof HttpResponse;

                final HttpResponse res = (HttpResponse) msg;
                final int code = res.status().code();
                final CharSequence acceptEncoding;
                if (code == CONTINUE_CODE) {
                    // We need to not poll the encoding when response with CONTINUE as another response will follow
                    // for the issued request. See https://github.com/netty/netty/issues/4079
                    acceptEncoding = null;
                } else {
                    // Get the list of encodings accepted by the peer.
                    acceptEncoding = acceptEncodingQueue.poll();
                    if (acceptEncoding == null) {
                        throw new IllegalStateException("cannot send more responses than requests");
                    }
                }

                /*
                 * per rfc2616 4.3 Message Body
                 * All 1xx (informational), 204 (no content), and 304 (not modified) responses MUST NOT include a
                 * message-body. All other responses do include a message-body, although it MAY be of zero length.
                 *
                 * 9.4 HEAD
                 * The HEAD method is identical to GET except that the server MUST NOT return a message-body
                 * in the response.
                 *
                 * Also we should pass through HTTP/1.0 as transfer-encoding: chunked is not supported.
                 *
                 * See https://github.com/netty/netty/issues/5382
                 */
                if (isPassthru(res.protocolVersion(), code, acceptEncoding)) {
                    out.add(res);
                    if (!isFull) {
                        // Pass through all following contents.
                        state = State.PASS_THROUGH;
                    }
                    break;
                }

                // Pass through the full response with empty content and continue waiting for the next resp.
                if (isFull && ((LastHttpContent<?>) res).payload().readableBytes() == 0) {
                    out.add(res);
                    break;
                }

                // Prepare to encode the content.
                assert acceptEncoding != null;
                final Result result = beginEncode(res, acceptEncoding.toString());

                // If unable to encode, pass through.
                if (result == null) {
                    out.add(res);
                    if (!isFull) {
                        // Pass through all following contents.
                        state = State.PASS_THROUGH;
                    }
                    break;
                }

                compressor = result.contentCompressor();

                // Encode the content and remove or replace the existing headers
                // so that the message looks like a decoded message.
                res.headers().set(HttpHeaderNames.CONTENT_ENCODING, result.targetContentEncoding());

                // Output the rewritten response.
                if (isFull) {
                    // Convert full message into unfull one.
                    HttpResponse newRes = new DefaultHttpResponse(res.protocolVersion(), res.status());
                    newRes.headers().set(res.headers());
                    out.add(newRes);

                    ensureContent(res);
                    encodeFullResponse(ctx, newRes, (HttpContent<?>) res, out);
                    break;
                } else {
                    // Make the response chunked to simplify content transformation.
                    res.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                    res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

                    out.add(res);
                    state = State.AWAIT_CONTENT;
                    if (!(msg instanceof HttpContent)) {
                        // only break out the switch statement if we have not content to process
                        // See https://github.com/netty/netty/issues/2006
                        break;
                    }
                    // Fall through to encode the content
                }
            }
            case AWAIT_CONTENT: {
                ensureContent(msg);
                if (encodeContent(ctx, (HttpContent<?>) msg, out)) {
                    state = State.AWAIT_HEADERS;
                }
                break;
            }
            case PASS_THROUGH: {
                ensureContent(msg);
                out.add(msg);
                // Passed through all following contents of the current response.
                if (msg instanceof LastHttpContent) {
                    state = State.AWAIT_HEADERS;
                }
                break;
            }
        }
    }

    private void encodeFullResponse(ChannelHandlerContext ctx, HttpResponse newRes, HttpContent<?> content,
                                    List<Object> out) {
        int existingMessages = out.size();
        encodeContent(ctx, content, out);

        if (HttpUtil.isContentLengthSet(newRes)) {
            // adjust the content-length header
            int messageSize = 0;
            for (int i = existingMessages; i < out.size(); i++) {
                Object item = out.get(i);
                if (item instanceof HttpContent) {
                    messageSize += ((HttpContent<?>) item).payload().readableBytes();
                }
            }
            HttpUtil.setContentLength(newRes, messageSize);
        } else {
            newRes.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }
    }

    private static boolean isPassthru(HttpVersion version, int code, CharSequence httpMethod) {
        return code < 200 || code == 204 || code == 304 ||
               (httpMethod == ZERO_LENGTH_HEAD || (httpMethod == ZERO_LENGTH_CONNECT && code == 200)) ||
                version == HttpVersion.HTTP_1_0;
    }

    private static void ensureHeaders(HttpObject msg) {
        if (!(msg instanceof HttpResponse)) {
            throw new IllegalStateException(
                    "unexpected message type: " +
                    msg.getClass().getName() + " (expected: " + HttpResponse.class.getSimpleName() + ')');
        }
    }

    private static void ensureContent(HttpObject msg) {
        if (!(msg instanceof HttpContent)) {
            throw new IllegalStateException(
                    "unexpected message type: " +
                    msg.getClass().getName() + " (expected: " + HttpContent.class.getSimpleName() + ')');
        }
    }

    private boolean encodeContent(ChannelHandlerContext ctx, HttpContent<?> c, List<Object> out) {
        Buffer content = c.payload();

        encode(content, ctx.alloc(), ctx.bufferAllocator(), out);

        if (c instanceof LastHttpContent) {
            finishEncode(ctx.alloc(), ctx.bufferAllocator(), out);
            LastHttpContent<?> last = (LastHttpContent<?>) c;

            // Generate an additional chunk if the decoder produced
            // the last product on closure,
            HttpHeaders headers = last.trailingHeaders();
            if (headers.isEmpty()) {
                out.add(new EmptyLastHttpContent(ctx.bufferAllocator()));
            } else {
                out.add(new DefaultLastHttpContent(ctx.bufferAllocator().allocate(0), headers));
            }
            return true;
        }
        return false;
    }

    /**
     * Prepare to encode the HTTP message content.
     *
     * @param httpResponse
     *        the http response
     * @param acceptEncoding
     *        the value of the {@code "Accept-Encoding"} header
     *
     * @return the result of preparation, which is composed of the determined
     *         target content encoding and a new {@link EmbeddedChannel} that
     *         encodes the content into the target content encoding.
     *         {@code null} if {@code acceptEncoding} is unsupported or rejected
     *         and thus the content should be handled as-is (i.e. no encoding).
     */
    protected abstract Result beginEncode(HttpResponse httpResponse, String acceptEncoding) throws Exception;

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanupSafely(ctx);
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanupSafely(ctx);
        super.channelInactive(ctx);
    }

    private void cleanup() {
        if (compressor != null) {
            // Clean-up the previous encoder if not cleaned up correctly.
            try {
                compressor.close();
            } finally {
                compressor = null;
            }
        }
    }

    private void cleanupSafely(ChannelHandlerContext ctx) {
        try {
            cleanup();
        } catch (Throwable cause) {
            // If cleanup throws any error we need to propagate it through the pipeline
            // so we don't fail to propagate pipeline events.
            ctx.fireExceptionCaught(cause);
        }
    }

    private void encode(Buffer in, ByteBufAllocator byteBufAllocator, BufferAllocator allocator, List<Object> out) {
        Buffer compressed = extractOrCopy(allocator, compressor.compress(ByteBufAdaptor.intoByteBuf(in),
                byteBufAllocator));
        if (compressed.readableBytes() == 0) {
            compressed.close();
            return;
        }
        out.add(new DefaultHttpContent(compressed));
    }

    private void finishEncode(ByteBufAllocator byteBufAllocator, BufferAllocator allocator, List<Object> out) {
        Buffer trailer = extractOrCopy(allocator, compressor.finish(byteBufAllocator));
        if (trailer.readableBytes() == 0) {
            trailer.close();
            return;
        }
        out.add(new DefaultHttpContent(trailer));
        compressor = null;
    }

    public static final class Result {
        private final String targetContentEncoding;
        private final Compressor contentCompressor;

        public Result(String targetContentEncoding, Compressor contentCompressor) {
            requireNonNull(targetContentEncoding, "targetContentEncoding");
            requireNonNull(contentCompressor, "contentCompressor");

            this.targetContentEncoding = targetContentEncoding;
            this.contentCompressor = contentCompressor;
        }

        public String targetContentEncoding() {
            return targetContentEncoding;
        }

        public Compressor contentCompressor() {
            return contentCompressor;
        }
    }
}
