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
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

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
 * converts them into {@link ByteBuf}s.
 */
public abstract class HttpContentEncoder extends MessageToMessageCodec<HttpRequest, HttpObject> {

    private enum State {
        PASS_THROUGH,
        AWAIT_HEADERS,
        AWAIT_CONTENT
    }

    private static final CharSequence ZERO_LENGTH_HEAD = "HEAD";
    private static final CharSequence ZERO_LENGTH_CONNECT = "CONNECT";

    private final Queue<CharSequence> acceptEncodingQueue = new ArrayDeque<CharSequence>();
    private EmbeddedChannel encoder;
    private State state = State.AWAIT_HEADERS;

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof HttpContent || msg instanceof HttpResponse;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpRequest msg, List<Object> out) throws Exception {
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
        out.add(ReferenceCountUtil.retain(msg));
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        final boolean isFull = msg instanceof HttpResponse && msg instanceof LastHttpContent;
        switch (state) {
            case AWAIT_HEADERS: {
                ensureHeaders(msg);
                assert encoder == null;

                final HttpResponse res = (HttpResponse) msg;
                final int code = res.status().code();
                final HttpStatusClass codeClass = res.status().codeClass();
                final CharSequence acceptEncoding;
                if (codeClass == HttpStatusClass.INFORMATIONAL) {
                    // We need to not poll the encoding when response with 1xx codes as another response will follow
                    // for the issued request.
                    // See https://github.com/netty/netty/issues/12904 and https://github.com/netty/netty/issues/4079
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
                    if (isFull) {
                        out.add(ReferenceCountUtil.retain(res));
                    } else {
                        out.add(ReferenceCountUtil.retain(res));
                        // Pass through all following contents.
                        state = State.PASS_THROUGH;
                    }
                    break;
                }

                if (isFull) {
                    // Pass through the full response with empty content and continue waiting for the next resp.
                    if (!((ByteBufHolder) res).content().isReadable()) {
                        out.add(ReferenceCountUtil.retain(res));
                        break;
                    }
                }

                // Prepare to encode the content.
                final Result result = beginEncode(res, acceptEncoding.toString());

                // If unable to encode, pass through.
                if (result == null) {
                    if (isFull) {
                        out.add(ReferenceCountUtil.retain(res));
                    } else {
                        out.add(ReferenceCountUtil.retain(res));
                        // Pass through all following contents.
                        state = State.PASS_THROUGH;
                    }
                    break;
                }

                encoder = result.contentEncoder();

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
                    encodeFullResponse(newRes, (HttpContent) res, out);
                    break;
                } else {
                    // Make the response chunked to simplify content transformation.
                    res.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                    res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

                    out.add(ReferenceCountUtil.retain(res));
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
                if (encodeContent((HttpContent) msg, out)) {
                    state = State.AWAIT_HEADERS;
                }
                break;
            }
            case PASS_THROUGH: {
                ensureContent(msg);
                out.add(ReferenceCountUtil.retain(msg));
                // Passed through all following contents of the current response.
                if (msg instanceof LastHttpContent) {
                    state = State.AWAIT_HEADERS;
                }
                break;
            }
        }
    }

    private void encodeFullResponse(HttpResponse newRes, HttpContent content, List<Object> out) {
        int existingMessages = out.size();
        encodeContent(content, out);

        if (HttpUtil.isContentLengthSet(newRes)) {
            // adjust the content-length header
            int messageSize = 0;
            for (int i = existingMessages; i < out.size(); i++) {
                Object item = out.get(i);
                if (item instanceof HttpContent) {
                    messageSize += ((HttpContent) item).content().readableBytes();
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

    private boolean encodeContent(HttpContent c, List<Object> out) {
        ByteBuf content = c.content();

        encode(content, out);

        if (c instanceof LastHttpContent) {
            finishEncode(out);
            LastHttpContent last = (LastHttpContent) c;

            // Generate an additional chunk if the decoder produced
            // the last product on closure,
            HttpHeaders headers = last.trailingHeaders();
            if (headers.isEmpty()) {
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
            } else {
                out.add(new ComposedLastHttpContent(headers, DecoderResult.SUCCESS));
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
        if (encoder != null) {
            // Clean-up the previous encoder if not cleaned up correctly.
            encoder.finishAndReleaseAll();
            encoder = null;
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

    private void encode(ByteBuf in, List<Object> out) {
        // call retain here as it will call release after its written to the channel
        encoder.writeOutbound(in.retain());
        fetchEncoderOutput(out);
    }

    private void finishEncode(List<Object> out) {
        if (encoder.finish()) {
            fetchEncoderOutput(out);
        }
        encoder = null;
    }

    private void fetchEncoderOutput(List<Object> out) {
        for (;;) {
            ByteBuf buf = encoder.readOutbound();
            if (buf == null) {
                break;
            }
            if (!buf.isReadable()) {
                buf.release();
                continue;
            }
            out.add(new DefaultHttpContent(buf));
        }
    }

    public static final class Result {
        private final String targetContentEncoding;
        private final EmbeddedChannel contentEncoder;

        public Result(String targetContentEncoding, EmbeddedChannel contentEncoder) {
            this.targetContentEncoding = ObjectUtil.checkNotNull(targetContentEncoding, "targetContentEncoding");
            this.contentEncoder = ObjectUtil.checkNotNull(contentEncoder, "contentEncoder");
        }

        public String targetContentEncoding() {
            return targetContentEncoding;
        }

        public EmbeddedChannel contentEncoder() {
            return contentEncoder;
        }
    }
}
