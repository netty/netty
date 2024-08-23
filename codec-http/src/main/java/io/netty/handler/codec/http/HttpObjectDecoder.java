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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.StringUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Decodes {@link ByteBuf}s into {@link HttpMessage}s and
 * {@link HttpContent}s.
 *
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Default value</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>{@value #DEFAULT_MAX_INITIAL_LINE_LENGTH}</td>
 * <td>The maximum length of the initial line
 *     (e.g. {@code "GET / HTTP/1.0"} or {@code "HTTP/1.0 200 OK"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongHttpLineException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>{@value #DEFAULT_MAX_HEADER_SIZE}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongHttpHeaderException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxChunkSize}</td>
 * <td>{@value #DEFAULT_MAX_CHUNK_SIZE}</td>
 * <td>The maximum length of the content or each chunk.  If the content length
 *     (or the length of each chunk) exceeds this value, the content or chunk
 *     will be split into multiple {@link HttpContent}s whose length is
 *     {@code maxChunkSize} at maximum.</td>
 * </tr>
 * </table>
 *
 * <h3>Parameters that control parsing behavior</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Default value</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code allowDuplicateContentLengths}</td>
 * <td>{@value #DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS}</td>
 * <td>When set to {@code false}, will reject any messages that contain multiple Content-Length header fields.
 *     When set to {@code true}, will allow multiple Content-Length headers only if they are all the same decimal value.
 *     The duplicated field-values will be replaced with a single valid Content-Length field.
 *     See <a href="https://tools.ietf.org/html/rfc7230#section-3.3.2">RFC 7230, Section 3.3.2</a>.</td>
 * </tr>
 * <tr>
 * <td>{@code allowPartialChunks}</td>
 * <td>{@value #DEFAULT_ALLOW_PARTIAL_CHUNKS}</td>
 * <td>If the length of a chunk exceeds the {@link ByteBuf}s readable bytes and {@code allowPartialChunks}
 *     is set to {@code true}, the chunk will be split into multiple {@link HttpContent}s.
 *     Otherwise, if the chunk size does not exceed {@code maxChunkSize} and {@code allowPartialChunks}
 *     is set to {@code false}, the {@link ByteBuf} is not decoded into an {@link HttpContent} until
 *     the readable bytes are greater or equal to the chunk size.</td>
 * </tr>
 * </table>
 *
 * <h3>Chunked Content</h3>
 *
 * If the content of an HTTP message is greater than {@code maxChunkSize} or
 * the transfer encoding of the HTTP message is 'chunked', this decoder
 * generates one {@link HttpMessage} instance and its following
 * {@link HttpContent}s per single HTTP message to avoid excessive memory
 * consumption. For example, the following HTTP message:
 * <pre>
 * GET / HTTP/1.1
 * Transfer-Encoding: chunked
 *
 * 1a
 * abcdefghijklmnopqrstuvwxyz
 * 10
 * 1234567890abcdef
 * 0
 * Content-MD5: ...
 * <i>[blank line]</i>
 * </pre>
 * triggers {@link HttpRequestDecoder} to generate 3 objects:
 * <ol>
 * <li>An {@link HttpRequest},</li>
 * <li>The first {@link HttpContent} whose content is {@code 'abcdefghijklmnopqrstuvwxyz'},</li>
 * <li>The second {@link LastHttpContent} whose content is {@code '1234567890abcdef'}, which marks
 * the end of the content.</li>
 * </ol>
 *
 * If you prefer not to handle {@link HttpContent}s by yourself for your
 * convenience, insert {@link HttpObjectAggregator} after this decoder in the
 * {@link ChannelPipeline}.  However, please note that your server might not
 * be as memory efficient as without the aggregator.
 *
 * <h3>Extensibility</h3>
 *
 * Please note that this decoder is designed to be extended to implement
 * a protocol derived from HTTP, such as
 * <a href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 * To implement the decoder of such a derived protocol, extend this class and
 * implement all abstract methods properly.
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
 */
public abstract class HttpObjectDecoder extends ByteToMessageDecoder {
    public static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;
    public static final int DEFAULT_MAX_HEADER_SIZE = 8192;
    public static final boolean DEFAULT_CHUNKED_SUPPORTED = true;
    public static final boolean DEFAULT_ALLOW_PARTIAL_CHUNKS = true;
    public static final int DEFAULT_MAX_CHUNK_SIZE = 8192;
    public static final boolean DEFAULT_VALIDATE_HEADERS = true;
    public static final int DEFAULT_INITIAL_BUFFER_SIZE = 128;
    public static final boolean DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS = false;
    private final int maxChunkSize;
    private final boolean chunkedSupported;
    private final boolean allowPartialChunks;
    /**
     * This field is no longer used. It is only kept around for backwards compatibility purpose.
     */
    @Deprecated
    protected final boolean validateHeaders;
    protected final HttpHeadersFactory headersFactory;
    protected final HttpHeadersFactory trailersFactory;
    private final boolean allowDuplicateContentLengths;
    private final ByteBuf parserScratchBuffer;
    private final HeaderParser headerParser;
    private final LineParser lineParser;

    private HttpMessage message;
    private long chunkSize;
    private long contentLength = Long.MIN_VALUE;
    private boolean chunked;
    private boolean isSwitchingToNonHttp1Protocol;

    private final AtomicBoolean resetRequested = new AtomicBoolean();

    // These will be updated by splitHeader(...)
    private AsciiString name;
    private String value;
    private LastHttpContent trailer;

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        try {
            parserScratchBuffer.release();
        } finally {
            super.handlerRemoved0(ctx);
        }
    }

    /**
     * The internal state of {@link HttpObjectDecoder}.
     * <em>Internal use only</em>.
     */
    private enum State {
        SKIP_CONTROL_CHARS,
        READ_INITIAL,
        READ_HEADER,
        READ_VARIABLE_LENGTH_CONTENT,
        READ_FIXED_LENGTH_CONTENT,
        READ_CHUNK_SIZE,
        READ_CHUNKED_CONTENT,
        READ_CHUNK_DELIMITER,
        READ_CHUNK_FOOTER,
        BAD_MESSAGE,
        UPGRADED
    }

    private State currentState = State.SKIP_CONTROL_CHARS;

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096)}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}.
     */
    protected HttpObjectDecoder() {
        this(new HttpDecoderConfig());
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @deprecated Use {@link #HttpObjectDecoder(HttpDecoderConfig)} instead.
     */
    @Deprecated
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean chunkedSupported) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setChunkedSupported(chunkedSupported));
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @deprecated Use {@link #HttpObjectDecoder(HttpDecoderConfig)} instead.
     */
    @Deprecated
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setChunkedSupported(chunkedSupported)
                .setValidateHeaders(validateHeaders));
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @deprecated Use {@link #HttpObjectDecoder(HttpDecoderConfig)} instead.
     */
    @Deprecated
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders, int initialBufferSize) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setChunkedSupported(chunkedSupported)
                .setValidateHeaders(validateHeaders)
                .setInitialBufferSize(initialBufferSize));
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @deprecated Use {@link #HttpObjectDecoder(HttpDecoderConfig)} instead.
     */
    @Deprecated
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders, int initialBufferSize,
            boolean allowDuplicateContentLengths) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setChunkedSupported(chunkedSupported)
                .setValidateHeaders(validateHeaders)
                .setInitialBufferSize(initialBufferSize)
                .setAllowDuplicateContentLengths(allowDuplicateContentLengths));
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @deprecated Use {@link #HttpObjectDecoder(HttpDecoderConfig)} instead.
     */
    @Deprecated
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders, int initialBufferSize,
            boolean allowDuplicateContentLengths, boolean allowPartialChunks) {
        this(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setChunkedSupported(chunkedSupported)
                .setValidateHeaders(validateHeaders)
                .setInitialBufferSize(initialBufferSize)
                .setAllowDuplicateContentLengths(allowDuplicateContentLengths)
                .setAllowPartialChunks(allowPartialChunks));
    }

    /**
     * Creates a new instance with the specified configuration.
     */
    protected HttpObjectDecoder(HttpDecoderConfig config) {
        checkNotNull(config, "config");

        parserScratchBuffer = Unpooled.buffer(config.getInitialBufferSize());
        lineParser = new LineParser(parserScratchBuffer, config.getMaxInitialLineLength());
        headerParser = new HeaderParser(parserScratchBuffer, config.getMaxHeaderSize());
        maxChunkSize = config.getMaxChunkSize();
        chunkedSupported = config.isChunkedSupported();
        headersFactory = config.getHeadersFactory();
        trailersFactory = config.getTrailersFactory();
        validateHeaders = isValidating(headersFactory);
        allowDuplicateContentLengths = config.isAllowDuplicateContentLengths();
        allowPartialChunks = config.isAllowPartialChunks();
    }

    protected boolean isValidating(HttpHeadersFactory headersFactory) {
        if (headersFactory instanceof DefaultHttpHeadersFactory) {
            DefaultHttpHeadersFactory builder = (DefaultHttpHeadersFactory) headersFactory;
            return builder.isValidatingHeaderNames() || builder.isValidatingHeaderValues();
        }
        return true; // We can't actually tell in this case, but we assume some validation is taking place.
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        if (resetRequested.get()) {
            resetNow();
        }

        switch (currentState) {
        case SKIP_CONTROL_CHARS:
            // Fall-through
        case READ_INITIAL: try {
            ByteBuf line = lineParser.parse(buffer);
            if (line == null) {
                return;
            }
            final String[] initialLine = splitInitialLine(line);
            assert initialLine.length == 3 : "initialLine::length must be 3";

            message = createMessage(initialLine);
            currentState = State.READ_HEADER;
            // fall-through
        } catch (Exception e) {
            out.add(invalidMessage(message, buffer, e));
            return;
        }
        case READ_HEADER: try {
            State nextState = readHeaders(buffer);
            if (nextState == null) {
                return;
            }
            currentState = nextState;
            switch (nextState) {
            case SKIP_CONTROL_CHARS:
                // fast-path
                // No content is expected.
                addCurrentMessage(out);
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                resetNow();
                return;
            case READ_CHUNK_SIZE:
                if (!chunkedSupported) {
                    throw new IllegalArgumentException("Chunked messages not supported");
                }
                // Chunked encoding - generate HttpMessage first.  HttpChunks will follow.
                addCurrentMessage(out);
                return;
            default:
                /*
                 * RFC 7230, 3.3.3 (https://tools.ietf.org/html/rfc7230#section-3.3.3) states that if a
                 * request does not have either a transfer-encoding or a content-length header then the message body
                 * length is 0. However, for a response the body length is the number of octets received prior to the
                 * server closing the connection. So we treat this as variable length chunked encoding.
                 */
                if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                    addCurrentMessage(out);
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                    resetNow();
                    return;
                }

                assert nextState == State.READ_FIXED_LENGTH_CONTENT ||
                        nextState == State.READ_VARIABLE_LENGTH_CONTENT;

                addCurrentMessage(out);

                if (nextState == State.READ_FIXED_LENGTH_CONTENT) {
                    // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT state reads data chunk by chunk.
                    chunkSize = contentLength;
                }

                // We return here, this forces decode to be called again where we will decode the content
                return;
            }
        } catch (Exception e) {
            out.add(invalidMessage(message, buffer, e));
            return;
        }
        case READ_VARIABLE_LENGTH_CONTENT: {
            // Keep reading data as a chunk until the end of connection is reached.
            int toRead = Math.min(buffer.readableBytes(), maxChunkSize);
            if (toRead > 0) {
                ByteBuf content = buffer.readRetainedSlice(toRead);
                out.add(new DefaultHttpContent(content));
            }
            return;
        }
        case READ_FIXED_LENGTH_CONTENT: {
            int readLimit = buffer.readableBytes();

            // Check if the buffer is readable first as we use the readable byte count
            // to create the HttpChunk. This is needed as otherwise we may end up with
            // create an HttpChunk instance that contains an empty buffer and so is
            // handled like it is the last HttpChunk.
            //
            // See https://github.com/netty/netty/issues/433
            if (readLimit == 0) {
                return;
            }

            int toRead = Math.min(readLimit, maxChunkSize);
            if (toRead > chunkSize) {
                toRead = (int) chunkSize;
            }
            ByteBuf content = buffer.readRetainedSlice(toRead);
            chunkSize -= toRead;

            if (chunkSize == 0) {
                // Read all content.
                out.add(new DefaultLastHttpContent(content, trailersFactory));
                resetNow();
            } else {
                out.add(new DefaultHttpContent(content));
            }
            return;
        }
        /*
         * everything else after this point takes care of reading chunked content. basically, read chunk size,
         * read chunk, read and ignore the CRLF and repeat until 0
         */
        case READ_CHUNK_SIZE: try {
            ByteBuf line = lineParser.parse(buffer);
            if (line == null) {
                return;
            }
            int chunkSize = getChunkSize(line.array(), line.arrayOffset() + line.readerIndex(), line.readableBytes());
            this.chunkSize = chunkSize;
            if (chunkSize == 0) {
                currentState = State.READ_CHUNK_FOOTER;
                return;
            }
            currentState = State.READ_CHUNKED_CONTENT;
            // fall-through
        } catch (Exception e) {
            out.add(invalidChunk(buffer, e));
            return;
        }
        case READ_CHUNKED_CONTENT: {
            assert chunkSize <= Integer.MAX_VALUE;
            int toRead = Math.min((int) chunkSize, maxChunkSize);
            if (!allowPartialChunks && buffer.readableBytes() < toRead) {
                return;
            }
            toRead = Math.min(toRead, buffer.readableBytes());
            if (toRead == 0) {
                return;
            }
            HttpContent chunk = new DefaultHttpContent(buffer.readRetainedSlice(toRead));
            chunkSize -= toRead;

            out.add(chunk);

            if (chunkSize != 0) {
                return;
            }
            currentState = State.READ_CHUNK_DELIMITER;
            // fall-through
        }
        case READ_CHUNK_DELIMITER: {
            final int wIdx = buffer.writerIndex();
            int rIdx = buffer.readerIndex();
            while (wIdx > rIdx) {
                byte next = buffer.getByte(rIdx++);
                if (next == HttpConstants.LF) {
                    currentState = State.READ_CHUNK_SIZE;
                    break;
                }
            }
            buffer.readerIndex(rIdx);
            return;
        }
        case READ_CHUNK_FOOTER: try {
            LastHttpContent trailer = readTrailingHeaders(buffer);
            if (trailer == null) {
                return;
            }
            out.add(trailer);
            resetNow();
            return;
        } catch (Exception e) {
            out.add(invalidChunk(buffer, e));
            return;
        }
        case BAD_MESSAGE: {
            // Keep discarding until disconnection.
            buffer.skipBytes(buffer.readableBytes());
            break;
        }
        case UPGRADED: {
            int readableBytes = buffer.readableBytes();
            if (readableBytes > 0) {
                // Keep on consuming as otherwise we may trigger an DecoderException,
                // other handler will replace this codec with the upgraded protocol codec to
                // take the traffic over at some point then.
                // See https://github.com/netty/netty/issues/2173
                out.add(buffer.readBytes(readableBytes));
            }
            break;
        }
        default:
            break;
        }
    }

    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        super.decodeLast(ctx, in, out);

        if (resetRequested.get()) {
            // If a reset was requested by decodeLast() we need to do it now otherwise we may produce a
            // LastHttpContent while there was already one.
            resetNow();
        }

        // Handle the last unfinished message.
        switch (currentState) {
            case READ_VARIABLE_LENGTH_CONTENT:
                if (!chunked && !in.isReadable()) {
                    // End of connection.
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                    resetNow();
                }
                return;
            case READ_HEADER:
                // If we are still in the state of reading headers we need to create a new invalid message that
                // signals that the connection was closed before we received the headers.
                out.add(invalidMessage(message, Unpooled.EMPTY_BUFFER,
                        new PrematureChannelClosureException("Connection closed before received headers")));
                resetNow();
                return;
            case READ_CHUNK_DELIMITER: // fall-trough
            case READ_CHUNK_FOOTER: // fall-trough
            case READ_CHUNKED_CONTENT: // fall-trough
            case READ_CHUNK_SIZE: // fall-trough
            case READ_FIXED_LENGTH_CONTENT:
                // Check if the closure of the connection signifies the end of the content.
                boolean prematureClosure;
                if (isDecodingRequest() || chunked) {
                    // The last request did not wait for a response.
                    prematureClosure = true;
                } else {
                    // Compare the length of the received content and the 'Content-Length' header.
                    // If the 'Content-Length' header is absent, the length of the content is determined by the end of
                    // the connection, so it is perfectly fine.
                    prematureClosure = contentLength > 0;
                }
                if (!prematureClosure) {
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                }
                resetNow();
                return;
            case SKIP_CONTROL_CHARS: // fall-trough
            case READ_INITIAL:// fall-trough
            case BAD_MESSAGE: // fall-trough
            case UPGRADED: // fall-trough
                // Do nothing
                break;
            default:
                throw new IllegalStateException("Unhandled state " + currentState);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpExpectationFailedEvent) {
            switch (currentState) {
            case READ_FIXED_LENGTH_CONTENT:
            case READ_VARIABLE_LENGTH_CONTENT:
            case READ_CHUNK_SIZE:
                reset();
                break;
            default:
                break;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    private void addCurrentMessage(List<Object> out) {
        HttpMessage message = this.message;
        assert message != null;
        this.message = null;
        out.add(message);
    }

    protected boolean isContentAlwaysEmpty(HttpMessage msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) msg;
            final HttpResponseStatus status = res.status();
            final int code = status.code();
            final HttpStatusClass statusClass = status.codeClass();

            // Correctly handle return codes of 1xx.
            //
            // See:
            //     - https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html Section 4.4
            //     - https://github.com/netty/netty/issues/222
            if (statusClass == HttpStatusClass.INFORMATIONAL) {
                // One exception: Hixie 76 websocket handshake response
                return !(code == 101 && !res.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT)
                         && res.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true));
            }

            switch (code) {
            case 204: case 304:
                return true;
            default:
                return false;
            }
        }
        return false;
    }

    /**
     * Returns true if the server switched to a different protocol than HTTP/1.0 or HTTP/1.1, e.g. HTTP/2 or Websocket.
     * Returns false if the upgrade happened in a different layer, e.g. upgrade from HTTP/1.1 to HTTP/1.1 over TLS.
     */
    protected boolean isSwitchingToNonHttp1Protocol(HttpResponse msg) {
        if (msg.status().code() != HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
            return false;
        }
        String newProtocol = msg.headers().get(HttpHeaderNames.UPGRADE);
        return newProtocol == null ||
                !newProtocol.contains(HttpVersion.HTTP_1_0.text()) &&
                !newProtocol.contains(HttpVersion.HTTP_1_1.text());
    }

    /**
     * Resets the state of the decoder so that it is ready to decode a new message.
     * This method is useful for handling a rejected request with {@code Expect: 100-continue} header.
     */
    public void reset() {
        resetRequested.lazySet(true);
    }

    private void resetNow() {
        message = null;
        name = null;
        value = null;
        contentLength = Long.MIN_VALUE;
        chunked = false;
        lineParser.reset();
        headerParser.reset();
        trailer = null;
        if (isSwitchingToNonHttp1Protocol) {
            isSwitchingToNonHttp1Protocol = false;
            currentState = State.UPGRADED;
            return;
        }

        resetRequested.lazySet(false);
        currentState = State.SKIP_CONTROL_CHARS;
    }

    private HttpMessage invalidMessage(HttpMessage current, ByteBuf in, Exception cause) {
        currentState = State.BAD_MESSAGE;
        message = null;
        trailer = null;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());

        if (current == null) {
            current = createInvalidMessage();
        }
        current.setDecoderResult(DecoderResult.failure(cause));

        return current;
    }

    private HttpContent invalidChunk(ByteBuf in, Exception cause) {
        currentState = State.BAD_MESSAGE;
        message = null;
        trailer = null;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());

        HttpContent chunk = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        chunk.setDecoderResult(DecoderResult.failure(cause));
        return chunk;
    }

    private State readHeaders(ByteBuf buffer) {
        final HttpMessage message = this.message;
        final HttpHeaders headers = message.headers();

        final HeaderParser headerParser = this.headerParser;

        ByteBuf line = headerParser.parse(buffer);
        if (line == null) {
            return null;
        }
        int lineLength = line.readableBytes();
        while (lineLength > 0) {
            final byte[] lineContent = line.array();
            final int startLine = line.arrayOffset() + line.readerIndex();
            final byte firstChar = lineContent[startLine];
            if (name != null && (firstChar == ' ' || firstChar == '\t')) {
                //please do not make one line from below code
                //as it breaks +XX:OptimizeStringConcat optimization
                String trimmedLine = langAsciiString(lineContent, startLine, lineLength).trim();
                String valueStr = value;
                value = valueStr + ' ' + trimmedLine;
            } else {
                if (name != null) {
                    headers.add(name, value);
                }
                splitHeader(lineContent, startLine, lineLength);
            }

            line = headerParser.parse(buffer);
            if (line == null) {
                return null;
            }
            lineLength = line.readableBytes();
        }

        // Add the last header.
        if (name != null) {
            headers.add(name, value);
        }

        // reset name and value fields
        name = null;
        value = null;

        // Done parsing initial line and headers. Set decoder result.
        HttpMessageDecoderResult decoderResult = new HttpMessageDecoderResult(lineParser.size, headerParser.size);
        message.setDecoderResult(decoderResult);

        List<String> contentLengthFields = headers.getAll(HttpHeaderNames.CONTENT_LENGTH);
        if (!contentLengthFields.isEmpty()) {
            HttpVersion version = message.protocolVersion();
            boolean isHttp10OrEarlier = version.majorVersion() < 1 || (version.majorVersion() == 1
                    && version.minorVersion() == 0);
            // Guard against multiple Content-Length headers as stated in
            // https://tools.ietf.org/html/rfc7230#section-3.3.2:
            contentLength = HttpUtil.normalizeAndGetContentLength(contentLengthFields,
                    isHttp10OrEarlier, allowDuplicateContentLengths);
            if (contentLength != -1) {
                String lengthValue = contentLengthFields.get(0).trim();
                if (contentLengthFields.size() > 1 || // don't unnecessarily re-order headers
                        !lengthValue.equals(Long.toString(contentLength))) {
                    headers.set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
                }
            }
        } else {
            // We know the content length if it's a Web Socket message even if
            // Content-Length header is missing.
            contentLength = HttpUtil.getWebSocketContentLength(message);
        }
        if (!isDecodingRequest() && message instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) message;
            this.isSwitchingToNonHttp1Protocol = isSwitchingToNonHttp1Protocol(res);
        }
        if (isContentAlwaysEmpty(message)) {
            HttpUtil.setTransferEncodingChunked(message, false);
            return State.SKIP_CONTROL_CHARS;
        }
        if (HttpUtil.isTransferEncodingChunked(message)) {
            this.chunked = true;
            if (!contentLengthFields.isEmpty() && message.protocolVersion() == HttpVersion.HTTP_1_1) {
                handleTransferEncodingChunkedWithContentLength(message);
            }
            return State.READ_CHUNK_SIZE;
        }
        if (contentLength >= 0) {
            return State.READ_FIXED_LENGTH_CONTENT;
        }
        return State.READ_VARIABLE_LENGTH_CONTENT;
    }

    /**
     * Invoked when a message with both a "Transfer-Encoding: chunked" and a "Content-Length" header field is detected.
     * The default behavior is to <i>remove</i> the Content-Length field, but this method could be overridden
     * to change the behavior (to, e.g., throw an exception and produce an invalid message).
     * <p>
     * See: https://tools.ietf.org/html/rfc7230#section-3.3.3
     * <pre>
     *     If a message is received with both a Transfer-Encoding and a
     *     Content-Length header field, the Transfer-Encoding overrides the
     *     Content-Length.  Such a message might indicate an attempt to
     *     perform request smuggling (Section 9.5) or response splitting
     *     (Section 9.4) and ought to be handled as an error.  A sender MUST
     *     remove the received Content-Length field prior to forwarding such
     *     a message downstream.
     * </pre>
     * Also see:
     * https://github.com/apache/tomcat/blob/b693d7c1981fa7f51e58bc8c8e72e3fe80b7b773/
     * java/org/apache/coyote/http11/Http11Processor.java#L747-L755
     * https://github.com/nginx/nginx/blob/0ad4393e30c119d250415cb769e3d8bc8dce5186/
     * src/http/ngx_http_request.c#L1946-L1953
     */
    protected void handleTransferEncodingChunkedWithContentLength(HttpMessage message) {
        message.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
        contentLength = Long.MIN_VALUE;
    }

    private LastHttpContent readTrailingHeaders(ByteBuf buffer) {
        final HeaderParser headerParser = this.headerParser;
        ByteBuf line = headerParser.parse(buffer);
        if (line == null) {
            return null;
        }
        LastHttpContent trailer = this.trailer;
        int lineLength = line.readableBytes();
        if (lineLength == 0 && trailer == null) {
            // We have received the empty line which signals the trailer is complete and did not parse any trailers
            // before. Just return an empty last content to reduce allocations.
            return LastHttpContent.EMPTY_LAST_CONTENT;
        }

        CharSequence lastHeader = null;
        if (trailer == null) {
            trailer = this.trailer = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, trailersFactory);
        }
        while (lineLength > 0) {
            final byte[] lineContent = line.array();
            final int startLine = line.arrayOffset() + line.readerIndex();
            final byte firstChar = lineContent[startLine];
            if (lastHeader != null && (firstChar == ' ' || firstChar == '\t')) {
                List<String> current = trailer.trailingHeaders().getAll(lastHeader);
                if (!current.isEmpty()) {
                    int lastPos = current.size() - 1;
                    //please do not make one line from below code
                    //as it breaks +XX:OptimizeStringConcat optimization
                    String lineTrimmed = langAsciiString(lineContent, startLine, line.readableBytes()).trim();
                    String currentLastPos = current.get(lastPos);
                    current.set(lastPos, currentLastPos + lineTrimmed);
                }
            } else {
                splitHeader(lineContent, startLine, lineLength);
                AsciiString headerName = name;
                if (!HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(headerName) &&
                        !HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(headerName) &&
                        !HttpHeaderNames.TRAILER.contentEqualsIgnoreCase(headerName)) {
                    trailer.trailingHeaders().add(headerName, value);
                }
                lastHeader = name;
                // reset name and value fields
                name = null;
                value = null;
            }
            line = headerParser.parse(buffer);
            if (line == null) {
                return null;
            }
            lineLength = line.readableBytes();
        }

        this.trailer = null;
        return trailer;
    }

    protected abstract boolean isDecodingRequest();
    protected abstract HttpMessage createMessage(String[] initialLine) throws Exception;
    protected abstract HttpMessage createInvalidMessage();

    /**
     * It skips any whitespace char and return the number of skipped bytes.
     */
    private static int skipWhiteSpaces(byte[] hex, int start, int length) {
        for (int i = 0; i < length; i++) {
            if (!isWhitespace(hex[start + i])) {
                return i;
            }
        }
        return length;
    }

    private static int getChunkSize(byte[] hex, int start, int length) {
        // trim the leading bytes if white spaces, if any
        final int skipped = skipWhiteSpaces(hex, start, length);
        if (skipped == length) {
            // empty case
            throw new NumberFormatException();
        }
        start += skipped;
        length -= skipped;
        int result = 0;
        for (int i = 0; i < length; i++) {
            final int digit = StringUtil.decodeHexNibble(hex[start + i]);
            if (digit == -1) {
                // uncommon path
                final byte b = hex[start + i];
                if (b == ';' || isControlOrWhitespaceAsciiChar(b)) {
                    if (i == 0) {
                        // empty case
                        throw new NumberFormatException("Empty chunk size");
                    }
                    return result;
                }
                // non-hex char fail-fast path
                throw new NumberFormatException("Invalid character in chunk size");
            }
            result *= 16;
            result += digit;
            if (result < 0) {
                throw new NumberFormatException("Chunk size overflow: " + result);
            }
        }
        return result;
    }

    private String[] splitInitialLine(ByteBuf asciiBuffer) {
        final byte[] asciiBytes = asciiBuffer.array();

        final int arrayOffset = asciiBuffer.arrayOffset();

        final int startContent = arrayOffset + asciiBuffer.readerIndex();

        final int end = startContent + asciiBuffer.readableBytes();

        byte lastByte = asciiBytes[end - 1];
        if (isControlOrWhitespaceAsciiChar(lastByte)) {
            if (isDecodingRequest() || !isOWS(lastByte)) {
                // There should no extra control or whitespace char in case of a request.
                // In case of a response there might be a SP if there is no reason-phrase given.
                // See
                //  - https://datatracker.ietf.org/doc/html/rfc2616#section-5.1
                //  - https://datatracker.ietf.org/doc/html/rfc9112#name-status-line
                throw new IllegalArgumentException(
                        "Illegal character in request line: 0x" + Integer.toHexString(lastByte));
            }
        }

        final int aStart = findNonSPLenient(asciiBytes, startContent, end);
        final int aEnd = findSPLenient(asciiBytes, aStart, end);

        final int bStart = findNonSPLenient(asciiBytes, aEnd, end);
        final int bEnd = findSPLenient(asciiBytes, bStart, end);

        final int cStart = findNonSPLenient(asciiBytes, bEnd, end);
        final int cEnd = findEndOfString(asciiBytes, Math.max(cStart - 1, startContent), end);

        return new String[]{
                splitFirstWordInitialLine(asciiBytes, aStart, aEnd - aStart),
                splitSecondWordInitialLine(asciiBytes, bStart, bEnd - bStart),
                cStart < cEnd ? splitThirdWordInitialLine(asciiBytes, cStart, cEnd - cStart) : StringUtil.EMPTY_STRING};
    }

    protected String splitFirstWordInitialLine(final byte[] asciiContent, int start, int length) {
        return langAsciiString(asciiContent, start, length);
    }

    protected String splitSecondWordInitialLine(final byte[] asciiContent, int start, int length) {
        return langAsciiString(asciiContent, start, length);
    }

    protected String splitThirdWordInitialLine(final byte[] asciiContent, int start, int length) {
        return langAsciiString(asciiContent, start, length);
    }

    /**
     * This method shouldn't exist: look at https://bugs.openjdk.org/browse/JDK-8295496 for more context
     */
    private static String langAsciiString(final byte[] asciiContent, int start, int length) {
        if (length == 0) {
            return StringUtil.EMPTY_STRING;
        }
        // DON'T REMOVE: it helps JIT to use a simpler intrinsic stub for System::arrayCopy based on the call-site
        if (start == 0) {
            if (length == asciiContent.length) {
                return new String(asciiContent, 0, 0, asciiContent.length);
            }
            return new String(asciiContent, 0, 0, length);
        }
        return new String(asciiContent, 0, start, length);
    }

    private void splitHeader(byte[] line, int start, int length) {
        final int end = start + length;
        int nameEnd;
        final int nameStart = start;
        // hoist this load out of the loop, because it won't change!
        final boolean isDecodingRequest = isDecodingRequest();
        for (nameEnd = nameStart; nameEnd < end; nameEnd ++) {
            byte ch = line[nameEnd];
            // https://tools.ietf.org/html/rfc7230#section-3.2.4
            //
            // No whitespace is allowed between the header field-name and colon. In
            // the past, differences in the handling of such whitespace have led to
            // security vulnerabilities in request routing and response handling. A
            // server MUST reject any received request message that contains
            // whitespace between a header field-name and colon with a response code
            // of 400 (Bad Request). A proxy MUST remove any such whitespace from a
            // response message before forwarding the message downstream.
            if (ch == ':' ||
                    // In case of decoding a request we will just continue processing and header validation
                    // is done in the DefaultHttpHeaders implementation.
                    //
                    // In the case of decoding a response we will "skip" the whitespace.
                    (!isDecodingRequest && isOWS(ch))) {
                break;
            }
        }

        if (nameEnd == end) {
            // There was no colon present at all.
            throw new IllegalArgumentException("No colon found");
        }
        int colonEnd;
        for (colonEnd = nameEnd; colonEnd < end; colonEnd ++) {
            if (line[colonEnd] == ':') {
                colonEnd ++;
                break;
            }
        }
        name = splitHeaderName(line, nameStart, nameEnd - nameStart);
        final int valueStart = findNonWhitespace(line, colonEnd, end);
        if (valueStart == end) {
            value = StringUtil.EMPTY_STRING;
        } else {
            final int valueEnd = findEndOfString(line, start, end);
            // no need to make uses of the ByteBuf's toString ASCII method here, and risk to get JIT confused
            value = langAsciiString(line, valueStart, valueEnd - valueStart);
        }
    }

    protected AsciiString splitHeaderName(byte[] sb, int start, int length) {
        return new AsciiString(sb, start, length, true);
    }

    private static int findNonSPLenient(byte[] sb, int offset, int end) {
        for (int result = offset; result < end; ++result) {
            byte c = sb[result];
            // See https://tools.ietf.org/html/rfc7230#section-3.5
            if (isSPLenient(c)) {
                continue;
            }
            if (isWhitespace(c)) {
                // Any other whitespace delimiter is invalid
                throw new IllegalArgumentException("Invalid separator");
            }
            return result;
        }
        return end;
    }

    private static int findSPLenient(byte[] sb, int offset, int end) {
        for (int result = offset; result < end; ++result) {
            if (isSPLenient(sb[result])) {
                return result;
            }
        }
        return end;
    }

    private static final boolean[] SP_LENIENT_BYTES;
    private static final boolean[] LATIN_WHITESPACE;

    static {
        // See https://tools.ietf.org/html/rfc7230#section-3.5
        SP_LENIENT_BYTES = new boolean[256];
        SP_LENIENT_BYTES[128 + ' '] = true;
        SP_LENIENT_BYTES[128 + 0x09] = true;
        SP_LENIENT_BYTES[128 + 0x0B] = true;
        SP_LENIENT_BYTES[128 + 0x0C] = true;
        SP_LENIENT_BYTES[128 + 0x0D] = true;
        // TO SAVE PERFORMING Character::isWhitespace ceremony
        LATIN_WHITESPACE = new boolean[256];
        for (byte b = Byte.MIN_VALUE; b < Byte.MAX_VALUE; b++) {
            LATIN_WHITESPACE[128 + b] = Character.isWhitespace(b);
        }
    }

    private static boolean isSPLenient(byte c) {
        // See https://tools.ietf.org/html/rfc7230#section-3.5
        return SP_LENIENT_BYTES[c + 128];
    }

    private static boolean isWhitespace(byte b) {
        return LATIN_WHITESPACE[b + 128];
    }

    private static int findNonWhitespace(byte[] sb, int offset, int end) {
        for (int result = offset; result < end; ++result) {
            byte c = sb[result];
            if (!isWhitespace(c)) {
                return result;
            } else if (!isOWS(c)) {
                // Only OWS is supported for whitespace
                throw new IllegalArgumentException("Invalid separator, only a single space or horizontal tab allowed," +
                        " but received a '" + c + "' (0x" + Integer.toHexString(c) + ")");
            }
        }
        return end;
    }

    private static int findEndOfString(byte[] sb, int start, int end) {
        for (int result = end - 1; result > start; --result) {
            if (!isOWS(sb[result])) {
                return result + 1;
            }
        }
        return 0;
    }

    private static boolean isOWS(byte ch) {
        return ch == ' ' || ch == 0x09;
    }

    private static class HeaderParser {
        protected final ByteBuf seq;
        protected final int maxLength;
        int size;

        HeaderParser(ByteBuf seq, int maxLength) {
            this.seq = seq;
            this.maxLength = maxLength;
        }

        public ByteBuf parse(ByteBuf buffer) {
            final int readableBytes = buffer.readableBytes();
            final int readerIndex = buffer.readerIndex();
            final int maxBodySize = maxLength - size;
            assert maxBodySize >= 0;
            // adding 2 to account for both CR (if present) and LF
            // don't remove 2L: it's key to cover maxLength = Integer.MAX_VALUE
            final long maxBodySizeWithCRLF = maxBodySize + 2L;
            final int toProcess = (int) Math.min(maxBodySizeWithCRLF, readableBytes);
            final int toIndexExclusive = readerIndex + toProcess;
            assert toIndexExclusive >= readerIndex;
            final int indexOfLf = buffer.indexOf(readerIndex, toIndexExclusive, HttpConstants.LF);
            if (indexOfLf == -1) {
                if (readableBytes > maxBodySize) {
                    // TODO: Respond with Bad Request and discard the traffic
                    //    or close the connection.
                    //       No need to notify the upstream handlers - just log.
                    //       If decoding a response, just throw an exception.
                    throw newException(maxLength);
                }
                return null;
            }
            final int endOfSeqIncluded;
            if (indexOfLf > readerIndex && buffer.getByte(indexOfLf - 1) == HttpConstants.CR) {
                // Drop CR if we had a CRLF pair
                endOfSeqIncluded = indexOfLf - 1;
            } else {
                endOfSeqIncluded = indexOfLf;
            }
            final int newSize = endOfSeqIncluded - readerIndex;
            if (newSize == 0) {
                seq.clear();
                buffer.readerIndex(indexOfLf + 1);
                return seq;
            }
            int size = this.size + newSize;
            if (size > maxLength) {
                throw newException(maxLength);
            }
            this.size = size;
            seq.clear();
            seq.writeBytes(buffer, readerIndex, newSize);
            buffer.readerIndex(indexOfLf + 1);
            return seq;
        }

        public void reset() {
            size = 0;
        }

        protected TooLongFrameException newException(int maxLength) {
            return new TooLongHttpHeaderException("HTTP header is larger than " + maxLength + " bytes.");
        }
    }

    private final class LineParser extends HeaderParser {

        LineParser(ByteBuf seq, int maxLength) {
            super(seq, maxLength);
        }

        @Override
        public ByteBuf parse(ByteBuf buffer) {
            // Suppress a warning because HeaderParser.reset() is supposed to be called
            reset();
            final int readableBytes = buffer.readableBytes();
            if (readableBytes == 0) {
                return null;
            }
            final int readerIndex = buffer.readerIndex();
            if (currentState == State.SKIP_CONTROL_CHARS && skipControlChars(buffer, readableBytes, readerIndex)) {
                return null;
            }
            return super.parse(buffer);
        }

        private boolean skipControlChars(ByteBuf buffer, int readableBytes, int readerIndex) {
            assert currentState == State.SKIP_CONTROL_CHARS;
            final int maxToSkip = Math.min(maxLength, readableBytes);
            final int firstNonControlIndex = buffer.forEachByte(readerIndex, maxToSkip, SKIP_CONTROL_CHARS_BYTES);
            if (firstNonControlIndex == -1) {
                buffer.skipBytes(maxToSkip);
                if (readableBytes > maxLength) {
                    throw newException(maxLength);
                }
                return true;
            }
            // from now on we don't care about control chars
            buffer.readerIndex(firstNonControlIndex);
            currentState = State.READ_INITIAL;
            return false;
        }

        @Override
        protected TooLongFrameException newException(int maxLength) {
            return new TooLongHttpLineException("An HTTP line is larger than " + maxLength + " bytes.");
        }
    }

    private static final boolean[] ISO_CONTROL_OR_WHITESPACE;

    static {
        ISO_CONTROL_OR_WHITESPACE = new boolean[256];
        for (byte b = Byte.MIN_VALUE; b < Byte.MAX_VALUE; b++) {
            ISO_CONTROL_OR_WHITESPACE[128 + b] = Character.isISOControl(b) || isWhitespace(b);
        }
    }

    private static final ByteProcessor SKIP_CONTROL_CHARS_BYTES = new ByteProcessor() {

        @Override
        public boolean process(byte value) {
            return ISO_CONTROL_OR_WHITESPACE[128 + value];
        }
    };

    private static boolean isControlOrWhitespaceAsciiChar(byte b) {
        return ISO_CONTROL_OR_WHITESPACE[128 + b];
    }
}
