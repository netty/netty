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

import io.netty.buffer.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;

/**
 * Encodes the content of the outbound {@link HttpResponse} and {@link HttpContent}.
 * The original content is replaced with the new content encoded by the
 * {@link EmbeddedByteChannel}, which is created by {@link #beginEncode(HttpResponse, String)}.
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

    private final Queue<String> acceptEncodingQueue = new ArrayDeque<String>();
    private String acceptEncoding;
    private EmbeddedByteChannel encoder;
    private State state = State.AWAIT_HEADERS;

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof HttpContent || msg instanceof HttpResponse;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpRequest msg, MessageBuf<Object> out)
            throws Exception {
        String acceptedEncoding = msg.headers().get(HttpHeaders.Names.ACCEPT_ENCODING);
        if (acceptedEncoding == null) {
            acceptedEncoding = HttpHeaders.Values.IDENTITY;
        }
        acceptEncodingQueue.add(acceptedEncoding);
        out.add(BufUtil.retain(msg));
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject msg, MessageBuf<Object> out) throws Exception {
        final boolean isFull = msg instanceof HttpResponse && msg instanceof LastHttpContent;
        switch (state) {
            case AWAIT_HEADERS: {
                ensureHeaders(msg);
                assert encoder == null;

                final HttpResponse res = (HttpResponse) msg;

                if (res.getStatus().code() == 100) {
                    if (isFull) {
                        out.add(BufUtil.retain(res));
                    } else {
                        out.add(res);
                        // Pass through all following contents.
                        state = State.PASS_THROUGH;
                    }
                    break;
                }

                // Get the list of encodings accepted by the peer.
                acceptEncoding = acceptEncodingQueue.poll();
                if (acceptEncoding == null) {
                    throw new IllegalStateException("cannot send more responses than requests");
                }

                if (isFull) {
                    // Pass through the full response with empty content and continue waiting for the the next resp.
                    if (!((ByteBufHolder) res).content().isReadable()) {
                        // Set the content length to 0.
                        res.headers().remove(Names.TRANSFER_ENCODING);
                        res.headers().set(Names.CONTENT_LENGTH, "0");
                        out.add(BufUtil.retain(res));
                        break;
                    }
                }

                // Prepare to encode the content.
                final Result result = beginEncode(res, acceptEncoding);

                // If unable to encode, pass through.
                if (result == null) {
                    if (isFull) {
                        // As an unchunked response
                        res.headers().remove(Names.TRANSFER_ENCODING);
                        res.headers().set(Names.CONTENT_LENGTH, ((ByteBufHolder) res).content().readableBytes());
                        out.add(BufUtil.retain(res));
                    } else {
                        // As a chunked response
                        res.headers().remove(Names.CONTENT_LENGTH);
                        res.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
                        out.add(res);
                        state = State.PASS_THROUGH;
                    }
                    break;
                }

                encoder = result.contentEncoder();

                // Encode the content and remove or replace the existing headers
                // so that the message looks like a decoded message.
                res.headers().set(Names.CONTENT_ENCODING, result.targetContentEncoding());

                // Make the response chunked to simplify content transformation.
                res.headers().remove(Names.CONTENT_LENGTH);
                res.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);

                // Output the rewritten response.
                if (isFull) {
                    // Convert full message into unfull one.
                    HttpResponse newRes = new DefaultHttpResponse(res.getProtocolVersion(), res.getStatus());
                    newRes.headers().set(res.headers());
                    out.add(newRes);
                    // Fall through to encode the content of the full response.
                } else {
                    out.add(res);
                    state = State.AWAIT_CONTENT;
                    break;
                }
            }
            case AWAIT_CONTENT: {
                ensureContent(msg);
                HttpContent[] encoded = encodeContent((HttpContent) msg);
                Collections.addAll(out, encoded);
                if (encoded[encoded.length - 1] instanceof LastHttpContent) {
                    state = State.AWAIT_HEADERS;
                }
                break;
            }
            case PASS_THROUGH: {
                ensureContent(msg);
                out.add(BufUtil.retain(msg));
                // Passed through all following contents of the current response.
                if (msg instanceof LastHttpContent) {
                    state = State.AWAIT_HEADERS;
                }
                break;
            }
        }
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

    private HttpContent[] encodeContent(HttpContent c) {
        ByteBuf newContent = Unpooled.buffer();
        ByteBuf content = c.content();

        encode(content, newContent);

        if (c instanceof LastHttpContent) {
            ByteBuf lastProduct = Unpooled.buffer();
            finishEncode(lastProduct);

            // Generate an additional chunk if the decoder produced
            // the last product on closure,
            if (lastProduct.isReadable()) {
                if (newContent.isReadable()) {
                    return new HttpContent[] {
                            new DefaultHttpContent(newContent), new DefaultLastHttpContent(lastProduct)};
                } else {
                    return new HttpContent[] { new DefaultLastHttpContent(lastProduct) };
                }
            } else {
                return new HttpContent[] { new DefaultLastHttpContent(newContent) };
            }
        }

        return new HttpContent[] { new DefaultHttpContent(newContent) };
    }

    /**
     * Prepare to encode the HTTP message content.
     *
     * @param headers
     *        the headers
     * @param acceptEncoding
     *        the value of the {@code "Accept-Encoding"} header
     *
     * @return the result of preparation, which is composed of the determined
     *         target content encoding and a new {@link EmbeddedByteChannel} that
     *         encodes the content into the target content encoding.
     *         {@code null} if {@code acceptEncoding} is unsupported or rejected
     *         and thus the content should be handled as-is (i.e. no encoding).
     */
    protected abstract Result beginEncode(HttpResponse headers, String acceptEncoding) throws Exception;

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

    private void cleanup() {
        if (encoder != null) {
            // Clean-up the previous encoder if not cleaned up correctly.
            finishEncode(Unpooled.buffer());
        }
    }

    private void encode(ByteBuf in, ByteBuf out) {
        // call retain here as it will call release after its written to the channel
        encoder.writeOutbound(in.retain());
        fetchEncoderOutput(out);
    }

    private void finishEncode(ByteBuf out) {
        if (encoder.finish()) {
            fetchEncoderOutput(out);
        }
        encoder = null;
    }

    private void fetchEncoderOutput(ByteBuf out) {
        for (;;) {
            ByteBuf buf = encoder.readOutbound();
            if (buf == null) {
                break;
            }
            out.writeBytes(buf);
        }
    }

    public static final class Result {
        private final String targetContentEncoding;
        private final EmbeddedByteChannel contentEncoder;

        public Result(String targetContentEncoding, EmbeddedByteChannel contentEncoder) {
            if (targetContentEncoding == null) {
                throw new NullPointerException("targetContentEncoding");
            }
            if (contentEncoder == null) {
                throw new NullPointerException("contentEncoder");
            }

            this.targetContentEncoding = targetContentEncoding;
            this.contentEncoder = contentEncoder;
        }

        public String targetContentEncoding() {
            return targetContentEncoding;
        }

        public EmbeddedByteChannel contentEncoder() {
            return contentEncoder;
        }
    }
}
