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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

import static io.netty.buffer.ByteBufUtil.readBytes;

/**
 * Decodes {@link ByteBuf}s into {@link HttpMessage}s and
 * {@link HttpContent}s.
 *
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>The maximum length of the initial line
 *     (e.g. {@code "GET / HTTP/1.0"} or {@code "HTTP/1.0 200 OK"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxChunkSize}</td>
 * <td>The maximum length of the content or each chunk.  If the content length
 *     (or the length of each chunk) exceeds this value, the content or chunk
 *     will be split into multiple {@link HttpContent}s whose length is
 *     {@code maxChunkSize} at maximum.</td>
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
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 * To implement the decoder of such a derived protocol, extend this class and
 * implement all abstract methods properly.
 */
public abstract class HttpObjectDecoder extends ByteToMessageDecoder {

    static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;
    static final int DEFAULT_MAX_HEADER_SIZE = 8192;
    static final int DEFAULT_MAX_CHUNK_SIZE = 8192;

    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int maxChunkSize;
    private final boolean chunkedSupported;
    protected final boolean validateHeaders;

    private HttpMessage message;
    private long chunkSize;
    private int headerSize;
    private int contentRead;
    private long contentLength = Long.MIN_VALUE;
    private State state = State.SKIP_CONTROL_CHARS;
    private final StringBuilder sb = new StringBuilder(128);

    /**
     * The internal state of {@link HttpObjectDecoder}.
     * <em>Internal use only</em>.
     */
    enum State {
        SKIP_CONTROL_CHARS,
        READ_INITIAL,
        READ_HEADER,
        READ_VARIABLE_LENGTH_CONTENT,
        READ_VARIABLE_LENGTH_CONTENT_AS_CHUNKS,
        READ_FIXED_LENGTH_CONTENT,
        READ_FIXED_LENGTH_CONTENT_AS_CHUNKS,
        READ_CHUNK_SIZE,
        READ_CHUNKED_CONTENT,
        READ_CHUNKED_CONTENT_AS_CHUNKS,
        READ_CHUNK_DELIMITER,
        READ_CHUNK_FOOTER,
        BAD_MESSAGE
    }

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}.
     */
    protected HttpObjectDecoder() {
        this(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_INITIAL_LINE_LENGTH, true, true);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders) {

        if (maxInitialLineLength <= 0) {
            throw new IllegalArgumentException(
                    "maxInitialLineLength must be a positive integer: " +
                    maxInitialLineLength);
        }
        if (maxHeaderSize <= 0) {
            throw new IllegalArgumentException(
                    "maxHeaderSize must be a positive integer: " +
                    maxHeaderSize);
        }
        if (maxChunkSize < 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be a positive integer: " +
                    maxChunkSize);
        }
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxHeaderSize = maxHeaderSize;
        this.maxChunkSize = maxChunkSize;
        this.chunkedSupported = chunkedSupported;
        this.validateHeaders = validateHeaders;
    }

    @Override
    public boolean isSingleDecode() {
        if (message == null && super.isSingleDecode()) {
            return true;
        }
        return false;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        switch (state) {
            case SKIP_CONTROL_CHARS: {
                if (!skipControlCharacters(buffer)) {
                    return;
                }
                state = State.READ_INITIAL;
                // FALL THROUGH
            }
            case READ_INITIAL: try {
                StringBuilder sb = this.sb;
                sb.setLength(0);

                HttpMessage msg = splitInitialLine(sb, buffer, maxInitialLineLength);
                if (msg == null) {
                    // not enough data
                    return;
                }

                message = msg;
                state = State.READ_HEADER;
                return;
            } catch (Exception e) {
                out.add(invalidMessage(e));
                return;
            }
            case READ_HEADER: try {
                State nextState = readHeaders(buffer);
                if (nextState == state) {
                    // was not able to consume whole header
                    return;
                }
                state = nextState;

                if (nextState == State.READ_CHUNK_SIZE) {
                    if (!chunkedSupported) {
                        throw new IllegalArgumentException("Chunked messages not supported");
                    }
                    out.add(message);
                    // Chunked encoding - generate HttpMessage first.  HttpChunks will follow.
                    return;
                }
                if (nextState == State.SKIP_CONTROL_CHARS) {
                    // No content is expected.
                    out.add(message);
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                    reset();
                    return;
                }
                long contentLength = contentLength();
                if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                    out.add(message);
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                    reset();
                    return;
                }

                switch (nextState) {
                    case READ_FIXED_LENGTH_CONTENT:
                        if (contentLength > maxChunkSize || HttpHeaders.is100ContinueExpected(message)) {
                            state = State.READ_FIXED_LENGTH_CONTENT_AS_CHUNKS;
                            // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT_AS_CHUNKS
                            // state reads data chunk by chunk.
                            chunkSize = contentLength;
                        }
                        break;
                    case READ_VARIABLE_LENGTH_CONTENT:
                        if (buffer.readableBytes() > maxChunkSize || HttpHeaders.is100ContinueExpected(message)) {
                            state = State.READ_VARIABLE_LENGTH_CONTENT_AS_CHUNKS;
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unexpected state: " + nextState);
                }
                out.add(message);
                return;
            } catch (Exception e) {
                out.add(invalidMessage(e));
                return;
            }
            case READ_VARIABLE_LENGTH_CONTENT: {
                int toRead = buffer.readableBytes();
                if (toRead == 0) {
                    // nothing to read
                    return;
                }
                if (toRead > maxChunkSize) {
                    toRead = maxChunkSize;
                }
                // TODO: Slice
                out.add(new DefaultHttpContent(readBytes(ctx.alloc(), buffer, toRead)));
                return;
            }
            case READ_VARIABLE_LENGTH_CONTENT_AS_CHUNKS: {
                // Keep reading data as a chunk until the end of connection is reached.
                int toRead = buffer.readableBytes();
                if (toRead == 0) {
                    // nothing to read
                    return;
                }
                if (toRead > maxChunkSize) {
                    toRead = maxChunkSize;
                }
                // TODO: Slice
                ByteBuf content = readBytes(ctx.alloc(), buffer, toRead);
                if (!buffer.isReadable()) {
                    out.add(new DefaultLastHttpContent(content));
                    reset();
                    return;
                }
                out.add(new DefaultHttpContent(content));
                return;
            }
            case READ_FIXED_LENGTH_CONTENT: {
                readFixedLengthContent(ctx, buffer, contentLength(), out);
                return;
            }
            case READ_FIXED_LENGTH_CONTENT_AS_CHUNKS: {
                long chunkSize = this.chunkSize;
                int toRead = buffer.readableBytes();

                // Check if the buffer is readable first as we use the readable byte count
                // to create the HttpChunk. This is needed as otherwise we may end up with
                // create a HttpChunk instance that contains an empty buffer and so is
                // handled like it is the last HttpChunk.
                //
                // See https://github.com/netty/netty/issues/433
                if (toRead == 0) {
                    return;
                }

                if (toRead > maxChunkSize) {
                    toRead = maxChunkSize;
                }
                if (toRead > chunkSize) {
                    toRead = (int) chunkSize;
                }
                // TODO: Slice
                ByteBuf content = readBytes(ctx.alloc(), buffer, toRead);
                if (chunkSize > toRead) {
                    chunkSize -= toRead;
                } else {
                    chunkSize = 0;
                }
                this.chunkSize = chunkSize;

                if (chunkSize == 0) {
                    // Read all content.
                    out.add(new DefaultLastHttpContent(content));
                    reset();
                    return;
                }
                out.add(new DefaultHttpContent(content));
                return;
            }
            /**
             * everything else after this point takes care of reading chunked content. basically, read chunk size,
             * read chunk, read and ignore the CRLF and repeat until 0
             */
            case READ_CHUNK_SIZE: try {
                StringBuilder line = readLine(buffer, maxInitialLineLength);

                if (line == null) {
                    // Not enough data
                    return;
                }

                int chunkSize = getChunkSize(line.toString());
                this.chunkSize = chunkSize;
                if (chunkSize == 0) {
                    state = State.READ_CHUNK_FOOTER;
                } else if (chunkSize > maxChunkSize) {
                    // A chunk is too large. Split them into multiple chunks again.
                    state = State.READ_CHUNKED_CONTENT_AS_CHUNKS;
                } else {
                    state = State.READ_CHUNKED_CONTENT;
                }
                return;
            } catch (Exception e) {
                out.add(invalidChunk(e));
                return;
            }
            case READ_CHUNKED_CONTENT: {
                assert chunkSize <= Integer.MAX_VALUE;
                if (buffer.readableBytes() < chunkSize) {
                    // not enough data
                    return;
                }
                // TODO: Slice
                HttpContent chunk = new DefaultHttpContent(readBytes(ctx.alloc(), buffer, (int) chunkSize));
                state = State.READ_CHUNK_DELIMITER;
                out.add(chunk);
                return;
            }
            case READ_CHUNKED_CONTENT_AS_CHUNKS: {
                int toRead = buffer.readableBytes();

                // Check if the buffer is readable first as we use the readable byte count
                // to create the HttpChunk. This is needed as otherwise we may end up with
                // create a HttpChunk instance that contains an empty buffer and so is
                // handled like it is the last HttpChunk.
                //
                // See https://github.com/netty/netty/issues/433
                if (toRead == 0) {
                    return;
                }

                assert chunkSize <= Integer.MAX_VALUE;
                int chunkSize = (int) this.chunkSize;
                if (toRead > maxChunkSize) {
                    toRead = maxChunkSize;
                }

                if (toRead > chunkSize) {
                    toRead = chunkSize;
                }

                HttpContent chunk = new DefaultHttpContent(readBytes(ctx.alloc(), buffer, toRead));
                if (chunkSize > toRead) {
                    chunkSize -= toRead;
                } else {
                    chunkSize = 0;
                }
                this.chunkSize = chunkSize;

                if (chunkSize == 0) {
                    // Read all content.
                    state = State.READ_CHUNK_DELIMITER;
                }

                out.add(chunk);
                return;
            }
            case READ_CHUNK_DELIMITER: {
                buffer.markReaderIndex();
                while (buffer.isReadable()) {
                    byte next = buffer.readByte();
                    if (next == HttpConstants.LF) {
                        state = State.READ_CHUNK_SIZE;
                        return;
                    }
                }
                // Try again later with more data
                // TODO: Optimize
                buffer.resetReaderIndex();
                return;
            }
            case READ_CHUNK_FOOTER: try {
                LastHttpContent trailer = readTrailingHeaders(buffer);
                if (trailer == null) {
                    // not enough data
                    return;
                }

                if (maxChunkSize == 0) {
                    // Chunked encoding disabled.
                } else {
                    // The last chunk, which is empty
                    out.add(trailer);
                }
                reset();

                return;
            } catch (Exception e) {
                out.add(invalidChunk(e));
                return;
            }
            case BAD_MESSAGE: {
                // Keep discarding until disconnection.
                buffer.skipBytes(buffer.readableBytes());
                return;
            }
            default: {
                throw new Error("Shouldn't reach here.");
            }
        }
    }

    private long contentLength() {
        if (contentLength == Long.MIN_VALUE) {
            contentLength = HttpHeaders.getContentLength(message, -1);
        }
        return contentLength;
    }

    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        decode(ctx, in, out);

        // Handle the last unfinished message.
        if (message != null) {
            // Get the length of the content received so far for the last message.
            HttpMessage message = this.message;

            int readable = in.readableBytes();
            // Check if the closure of the connection signifies the end of the content.
            boolean prematureClosure;
            if (isDecodingRequest()) {
                // The last request did not wait for a response.
                prematureClosure = true;
            } else {
                // Compare the length of the received content and the 'Content-Length' header.
                // If the 'Content-Length' header is absent, the length of the content is determined by the end of the
                // connection, so it is perfectly fine.
                long expectedContentLength = contentLength();
                prematureClosure = expectedContentLength >= 0 && contentRead + readable != expectedContentLength;
            }

            if (!prematureClosure) {
                if (readable == 0) {
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                } else {
                    out.add(new DefaultLastHttpContent(readBytes(ctx.alloc(), in, readable)));
                }
            }
        }
    }

    protected boolean isContentAlwaysEmpty(HttpMessage msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) msg;
            int code = res.getStatus().code();

            // Correctly handle return codes of 1xx.
            //
            // See:
            //     - http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html Section 4.4
            //     - https://github.com/netty/netty/issues/222
            if (code >= 100 && code < 200) {
                if (code == 101 && !res.headers().contains(HttpHeaders.Names.SEC_WEBSOCKET_ACCEPT)) {
                    // It's Hixie 76 websocket handshake response
                    return false;
                }
                return true;
            }

            switch (code) {
            case 204: case 205: case 304:
                return true;
            }
        }
        return false;
    }

    private void reset() {
        message = null;
        contentLength = Long.MIN_VALUE;
        contentRead = 0;
        state = State.SKIP_CONTROL_CHARS;
    }

    private HttpMessage invalidMessage(Exception cause) {
        state = State.BAD_MESSAGE;
        if (message != null) {
            message.setDecoderResult(DecoderResult.failure(cause));
        } else {
            message = createInvalidMessage();
            message.setDecoderResult(DecoderResult.failure(cause));
        }
        return message;
    }

    private HttpContent invalidChunk(Exception cause) {
        state = State.BAD_MESSAGE;
        HttpContent chunk = new DefaultHttpContent(Unpooled.EMPTY_BUFFER);
        chunk.setDecoderResult(DecoderResult.failure(cause));
        return chunk;
    }

    private static boolean skipControlCharacters(ByteBuf buffer) {
        while (buffer.isReadable()) {
            char c = (char) buffer.readUnsignedByte();
            if (!Character.isISOControl(c) &&
                !Character.isWhitespace(c)) {
                buffer.readerIndex(buffer.readerIndex() - 1);
                return true;
            }
        }
        return false;
    }

    private void readFixedLengthContent(ChannelHandlerContext ctx, ByteBuf buffer, long length, List<Object> out) {
        assert length <= Integer.MAX_VALUE;

        //we have a content-length so we just read the correct number of bytes
        int toRead = (int) length - contentRead;

        int readableBytes = buffer.readableBytes();
        if (toRead > readableBytes) {
            toRead = readableBytes;
        }

        contentRead += toRead;
        // TODO: Slice
        ByteBuf buf = readBytes(ctx.alloc(), buffer, toRead);
        if (contentRead < length) {
            out.add(new DefaultHttpContent(buf));
            return;
        }

        out.add(new DefaultLastHttpContent(buf));
        reset();
    }

    private State readHeaders(ByteBuf buffer) {
        final HttpMessage message = this.message;
        if (!parseHeaders(message.headers(), buffer)) {
            return state;
        }
        // this means we consumed the header completly

        if (isContentAlwaysEmpty(message)) {
            HttpHeaders.removeTransferEncodingChunked(message);
            return State.SKIP_CONTROL_CHARS;
        } else if (HttpHeaders.isTransferEncodingChunked(message)) {
            return State.READ_CHUNK_SIZE;
        } else if (contentLength() >= 0) {
            return State.READ_FIXED_LENGTH_CONTENT;
        } else {
            return State.READ_VARIABLE_LENGTH_CONTENT;
        }
    }

    private enum HeaderParseState {
        LINE_START,
        LINE_END,
        VALUE_START,
        VALUE_END,
        COMMA_END,
        NAME_START,
        NAME_END,
        HEADERS_END
    }

    private boolean parseHeaders(HttpHeaders headers, ByteBuf buffer) {
        // mark the index before try to start parsing and reset the StringBuilder
        StringBuilder sb = this.sb;
        sb.setLength(0);
        buffer.markReaderIndex();

        String name = null;
        HeaderParseState parseState = HeaderParseState.LINE_START;

        loop:
        while (buffer.isReadable()) {
            // Abort decoding if the header part is too large.
            if (headerSize++ >= maxHeaderSize) {
                // TODO: Respond with Bad Request and discard the traffic
                //    or close the connection.
                //       No need to notify the upstream handlers - just log.
                //       If decoding a response, just throw an exception.
                throw new TooLongFrameException(
                        "HTTP header is larger than " +
                                maxHeaderSize + " bytes.");
            }

            char next = (char) buffer.readByte();

            switch (parseState) {
                case LINE_START:
                    if (HttpConstants.CR == next) {
                        if (buffer.isReadable()) {
                            next = (char) buffer.readByte();
                            if (HttpConstants.LF == next) {
                                parseState = HeaderParseState.HEADERS_END;
                                break loop;
                            } else {
                                // consume
                            }
                        } else {
                            // not enough data
                            break loop;
                        }
                        break;
                    }
                    if (HttpConstants.LF == next) {
                        parseState = HeaderParseState.HEADERS_END;
                        break loop;
                    }
                    parseState = HeaderParseState.NAME_START;
                    // FALL THROUGH
                case NAME_START:
                    if (next != ' ' && next != '\t') {
                        // reset StringBuilder so it can be used to store the header name
                        sb.setLength(0);
                        parseState = HeaderParseState.NAME_END;
                        sb.append(next);
                    }
                    break;
                case NAME_END:
                    if (next == ':') {
                        // store current content of StringBuilder as header name and reset it
                        // so it can be used to store the header name
                        name = sb.toString();
                        sb.setLength(0);

                        parseState = HeaderParseState.VALUE_START;
                    } else if (next == ' ') {
                        // store current content of StringBuilder as header name and reset it
                        // so it can be used to store the header name
                        name = sb.toString();
                        sb.setLength(0);

                        parseState = HeaderParseState.COMMA_END;
                    } else {
                        sb.append(next);
                    }
                    break;
                case COMMA_END:
                    if (next == ':') {
                        parseState = HeaderParseState.VALUE_START;
                    }
                    break;
                case VALUE_START:
                    if (next != ' ' && next != '\t') {
                        parseState = HeaderParseState.VALUE_END;
                        sb.append(next);
                    }
                    break;
                case VALUE_END:
                    if (HttpConstants.CR == next) {
                        // ignore CR and use LF to detect line delimiter
                        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html#sec19.3
                        break;
                    }
                    if (HttpConstants.LF == next) {
                        // need to check for multi line header value
                        parseState = HeaderParseState.LINE_END;
                        break;
                    }
                    sb.append(next);
                    break;
                case LINE_END:
                    if (next == '\t' || next == ' ') {
                        // This is a multine line header
                        // skip char and move on
                        sb.append(next);
                        parseState = HeaderParseState.VALUE_START;
                        break;
                    }

                    // remove trailing white spaces
                    int end = findEndOfString(sb);
                    if (end + 1 < sb.length()) {
                        sb.setLength(end);
                    }

                    String value = sb.toString();
                    if (value.length() == 1 && value.charAt(0) == HttpConstants.CR) {
                        headers.add(name, "");
                    } else {
                        headers.add(name, value);
                    }

                    parseState = HeaderParseState.LINE_START;
                    // unread one byte to process it in LINE_START
                    buffer.readerIndex(buffer.readerIndex() - 1);
                    // mark the reader index on each line start so we can preserve already parsed headers
                    buffer.markReaderIndex();
                case HEADERS_END:
                    break;
            }
        }

        if (parseState != HeaderParseState.HEADERS_END) {
            // not enough data try again later
            buffer.resetReaderIndex();
            return false;
        } else {
            // reset header size
            headerSize = 0;
            buffer.markReaderIndex();
            return true;
        }
    }
    private LastHttpContent readTrailingHeaders(ByteBuf buffer) {
        StringBuilder line = readHeader(buffer);
        if (line == null) {
            // not enough data
            return null;
        }
        // this means we consumed the header completly
        String lastHeader = null;
        if (line.length() > 0) {
            buffer.markReaderIndex();

            LastHttpContent trailer = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
            final HttpHeaders headers = trailer.trailingHeaders();
            headers.clear();

            do {
                char firstChar = line.charAt(0);
                if (lastHeader != null && (firstChar == ' ' || firstChar == '\t')) {
                    List<String> current = headers.getAll(lastHeader);
                    if (!current.isEmpty()) {
                        int lastPos = current.size() - 1;
                        String newString = current.get(lastPos) + line.toString().trim();
                        current.set(lastPos, newString);
                    } else {
                        // Content-Length, Transfer-Encoding, or Trailer
                    }
                } else {
                    String[] header = splitHeader(line);
                    String name = header[0];
                    if (!HttpHeaders.equalsIgnoreCase(name, HttpHeaders.Names.CONTENT_LENGTH) &&
                        !HttpHeaders.equalsIgnoreCase(name, HttpHeaders.Names.TRANSFER_ENCODING) &&
                        !HttpHeaders.equalsIgnoreCase(name, HttpHeaders.Names.TRAILER)) {
                        headers.add(name, header[1]);
                    }
                    lastHeader = name;
                }

                line = readHeader(buffer);
                if (line == null) {
                    // not enough data
                    buffer.resetReaderIndex();
                    return null;
                }
            } while (line.length() > 0);

            return trailer;
        }

        return LastHttpContent.EMPTY_LAST_CONTENT;
    }

    private StringBuilder readHeader(ByteBuf buffer) {
        StringBuilder sb = this.sb;
        sb.setLength(0);
        int headerSize = this.headerSize;
        buffer.markReaderIndex();
        while (buffer.isReadable()) {
            char nextByte = (char) buffer.readByte();
            headerSize ++;
            switch (nextByte) {
            case HttpConstants.CR:
                if (!buffer.isReadable()) {
                    buffer.resetReaderIndex();
                    return null;
                }
                nextByte = (char) buffer.readByte();
                headerSize ++;
                if (nextByte == HttpConstants.LF) {
                    this.headerSize = headerSize;
                    return sb;
                }
                break;
            case HttpConstants.LF:
                this.headerSize = headerSize;
                return sb;
            }

            // Abort decoding if the header part is too large.
            if (headerSize >= maxHeaderSize) {
                // TODO: Respond with Bad Request and discard the traffic
                //    or close the connection.
                //       No need to notify the upstream handlers - just log.
                //       If decoding a response, just throw an exception.
                throw new TooLongFrameException(
                        "HTTP header is larger than " +
                        maxHeaderSize + " bytes.");
            }

            sb.append(nextByte);
        }
        buffer.resetReaderIndex();
        return null;
    }

    protected abstract boolean isDecodingRequest();
    protected abstract HttpMessage createMessage(String first, String second, String third) throws Exception;
    protected abstract HttpMessage createInvalidMessage();

    private static int getChunkSize(String hex) {
        hex = hex.trim();
        for (int i = 0; i < hex.length(); i ++) {
            char c = hex.charAt(i);
            if (c == ';' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                hex = hex.substring(0, i);
                break;
            }
        }

        return Integer.parseInt(hex, 16);
    }

    private StringBuilder readLine(ByteBuf buffer, int maxLineLength) {
        StringBuilder sb = this.sb;
        sb.setLength(0);
        int lineLength = 0;
        buffer.markReaderIndex();

        while (buffer.isReadable()) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpConstants.CR) {
                if (!buffer.isReadable()) {
                    break;
                }
                nextByte = buffer.readByte();
                if (nextByte == HttpConstants.LF) {
                    return sb;
                }
            } else if (nextByte == HttpConstants.LF) {
                return sb;
            } else {
                if (lineLength >= maxLineLength) {
                    // TODO: Respond with Bad Request and discard the traffic
                    //    or close the connection.
                    //       No need to notify the upstream handlers - just log.
                    //       If decoding a response, just throw an exception.
                    throw new TooLongFrameException(
                            "An HTTP line is larger than " + maxLineLength +
                            " bytes.");
                }
                lineLength ++;
                sb.append((char) nextByte);
            }
        }
        buffer.resetReaderIndex();
        // TODO: Optimize this
        return null;
    }

    private enum InitialLineState {
        START_A,
        END_A,
        START_B,
        END_B,
        START_C,
        END_C
    }

    private HttpMessage splitInitialLine(StringBuilder sb, ByteBuf buffer, int maxLineLength) throws Exception {
        InitialLineState state = InitialLineState.START_A;
        int aStart = 0;
        int aEnd = 0;
        int bStart = 0;
        int bEnd = 0;
        int cStart = 0;
        int cEnd = 0;

        sb.setLength(0);
        int index = 0;
        int lineLength = 0;

        buffer.markReaderIndex();

        while (buffer.isReadable()) {
            char next = (char) buffer.readByte();

            switch (state) {
                case START_A:
                case START_B:
                case START_C:
                    if (!Character.isWhitespace(next)) {
                        if (state == InitialLineState.START_A) {
                            aStart = index;
                            state = InitialLineState.END_A;
                        } else if (state == InitialLineState.START_B) {
                            bStart = index;
                            state = InitialLineState.END_B;
                        } else {
                            cStart = index;
                            state = InitialLineState.END_C;
                        }
                    }
                    break;
                case END_A:
                case END_B:
                    if (Character.isWhitespace(next)) {
                        if (state == InitialLineState.END_A) {
                            aEnd = index;
                            state = InitialLineState.START_B;
                        } else {
                            bEnd = index;
                            state = InitialLineState.START_C;
                        }
                    }
                    break;
                case END_C:
                    if (HttpConstants.CR == next) {
                        if (!buffer.isReadable()) {
                            buffer.resetReaderIndex();
                            return null;
                        }
                        next = (char) buffer.readByte();
                        if (HttpConstants.LF == next) {
                            cEnd = index;
                            return createMessage(
                                    sb.substring(aStart, aEnd),
                                    sb.substring(bStart, bEnd),
                                    cStart < cEnd? sb.substring(cStart, cEnd) : "");
                        }
                        index ++;

                        break;
                    }
                    if (HttpConstants.LF == next) {
                        cEnd = index;
                        return createMessage(
                                sb.substring(aStart, aEnd),
                                sb.substring(bStart, bEnd),
                                cStart < cEnd? sb.substring(cStart, cEnd) : "");
                    }
                    break;
            }
            if (lineLength >= maxLineLength) {
                // TODO: Respond with Bad Request and discard the traffic
                //    or close the connection.
                //       No need to notify the upstream handlers - just log.
                //       If decoding a response, just throw an exception.
                throw new TooLongFrameException(
                        "An HTTP line is larger than " + maxLineLength +
                                " bytes.");
            }
            lineLength ++;
            index ++;
            sb.append(next);
        }
        // reset index as we need to parse the line again once more data was received
        buffer.resetReaderIndex();
        return null;
    }

    private static String[] splitHeader(StringBuilder sb) {
        final int length = sb.length();
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;

        nameStart = findNonWhitespace(sb, 0);
        for (nameEnd = nameStart; nameEnd < length; nameEnd ++) {
            char ch = sb.charAt(nameEnd);
            if (ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
        }

        for (colonEnd = nameEnd; colonEnd < length; colonEnd ++) {
            if (sb.charAt(colonEnd) == ':') {
                colonEnd ++;
                break;
            }
        }

        valueStart = findNonWhitespace(sb, colonEnd);
        if (valueStart == length) {
            return new String[] {
                    sb.substring(nameStart, nameEnd),
                    ""
            };
        }

        valueEnd = findEndOfString(sb);
        return new String[] {
                sb.substring(nameStart, nameEnd),
                sb.substring(valueStart, valueEnd)
        };
    }

    private static int findNonWhitespace(CharSequence sb, int offset) {
        int result;
        for (result = offset; result < sb.length(); result ++) {
            if (!Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    private static int findEndOfString(CharSequence sb) {
        int result;
        for (result = sb.length(); result > 0; result --) {
            if (!Character.isWhitespace(sb.charAt(result - 1))) {
                break;
            }
        }
        return result;
    }
}
