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
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpObjectDecoder.State;
import io.netty.util.internal.AppendableCharSequence;

import java.util.List;

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
public abstract class HttpObjectDecoder extends ReplayingDecoder<State> {
    private static final String EMPTY_VALUE = "";

    private final int maxChunkSize;
    private final boolean chunkedSupported;
    protected final boolean validateHeaders;
    private final HeaderParser headerParser;
    private final LineParser lineParser;

    private HttpMessage message;
    private long chunkSize;
    private long contentLength = Long.MIN_VALUE;

    // These will be updated by splitHeader(...)
    private CharSequence name;
    private CharSequence value;

    /**
     * The internal state of {@link HttpObjectDecoder}.
     * <em>Internal use only</em>.
     */
    enum State {
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

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}.
     */
    protected HttpObjectDecoder() {
        this(4096, 8192, 8192, true);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean chunkedSupported) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported, true);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders) {

        super(State.SKIP_CONTROL_CHARS);

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
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be a positive integer: " +
                    maxChunkSize);
        }
        this.maxChunkSize = maxChunkSize;
        this.chunkedSupported = chunkedSupported;
        this.validateHeaders = validateHeaders;
        AppendableCharSequence seq = new AppendableCharSequence(128);
        lineParser = new LineParser(seq, maxInitialLineLength);
        headerParser = new HeaderParser(seq, maxHeaderSize);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        switch (state()) {
        case SKIP_CONTROL_CHARS: {
            try {
                skipControlCharacters(buffer);
                checkpoint(State.READ_INITIAL);
            } finally {
                checkpoint();
            }
            // fall-through
        }
        case READ_INITIAL: try {
            String[] initialLine = splitInitialLine(lineParser.parse(buffer));
            if (initialLine.length < 3) {
                // Invalid initial line - ignore.
                checkpoint(State.SKIP_CONTROL_CHARS);
                return;
            }

            message = createMessage(initialLine);
            checkpoint(State.READ_HEADER);
            // fall-through
        } catch (Exception e) {
            out.add(invalidMessage(e));
            return;
        }
        case READ_HEADER: try {
            State nextState = readHeaders(buffer);
            checkpoint(nextState);
            switch (nextState) {
                case SKIP_CONTROL_CHARS:
                    // fast-path
                    // No content is expected.
                    out.add(message);
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                    reset();
                    return;
                case READ_CHUNK_SIZE:
                    if (!chunkedSupported) {
                        throw new IllegalArgumentException("Chunked messages not supported");
                    }
                    // Chunked encoding - generate HttpMessage first.  HttpChunks will follow.
                    out.add(message);
                    return;
                default:
                    long contentLength = contentLength();
                    if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                        out.add(message);
                        out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                        reset();
                        return;
                    }

                    assert nextState == State.READ_FIXED_LENGTH_CONTENT ||
                            nextState == State.READ_VARIABLE_LENGTH_CONTENT;

                    out.add(message);

                    if (nextState == State.READ_FIXED_LENGTH_CONTENT) {
                        // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT state reads data chunk by chunk.
                        chunkSize = contentLength;
                    }

                    // We return here, this forces decode to be called again where we will decode the content
                    return;
            }
        } catch (Exception e) {
            out.add(invalidMessage(e));
            return;
        }
        case READ_VARIABLE_LENGTH_CONTENT: {
            // Keep reading data as a chunk until the end of connection is reached.
            int toRead = Math.min(actualReadableBytes(), maxChunkSize);
            if (toRead > 0) {
                ByteBuf content = buffer.readSlice(toRead).retain();
                out.add(new DefaultHttpContent(content));
            }
            return;
        }
        case READ_FIXED_LENGTH_CONTENT: {
            int readLimit = actualReadableBytes();

            // Check if the buffer is readable first as we use the readable byte count
            // to create the HttpChunk. This is needed as otherwise we may end up with
            // create a HttpChunk instance that contains an empty buffer and so is
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
            ByteBuf content = buffer.readSlice(toRead).retain();
            chunkSize -= toRead;

            if (chunkSize == 0) {
                // Read all content.
                out.add(new DefaultLastHttpContent(content, validateHeaders));
                reset();
            } else {
                out.add(new DefaultHttpContent(content));
            }
            return;
        }
        /**
         * everything else after this point takes care of reading chunked content. basically, read chunk size,
         * read chunk, read and ignore the CRLF and repeat until 0
         */
        case READ_CHUNK_SIZE: try {
            AppendableCharSequence line = lineParser.parse(buffer);
            int chunkSize = getChunkSize(line.toString());
            this.chunkSize = chunkSize;
            if (chunkSize == 0) {
                checkpoint(State.READ_CHUNK_FOOTER);
                return;
            }
            checkpoint(State.READ_CHUNKED_CONTENT);
            // fall-through
        } catch (Exception e) {
            out.add(invalidChunk(e));
            return;
        }
        case READ_CHUNKED_CONTENT: {
            assert chunkSize <= Integer.MAX_VALUE;
            int toRead = Math.min((int) chunkSize, maxChunkSize);
            toRead = Math.min(toRead, actualReadableBytes());
            if (toRead == 0) {
                return;
            }
            HttpContent chunk = new DefaultHttpContent(buffer.readSlice(toRead).retain());
            chunkSize -= toRead;

            out.add(chunk);

            if (chunkSize != 0) {
                return;
            }
            checkpoint(State.READ_CHUNK_DELIMITER);
            // fall-through
        }
        case READ_CHUNK_DELIMITER: {
            for (;;) {
                byte next = buffer.readByte();
                if (next == HttpConstants.CR) {
                    if (buffer.readByte() == HttpConstants.LF) {
                        checkpoint(State.READ_CHUNK_SIZE);
                        return;
                    }
                } else if (next == HttpConstants.LF) {
                    checkpoint(State.READ_CHUNK_SIZE);
                    return;
                } else {
                    checkpoint();
                }
            }
        }
        case READ_CHUNK_FOOTER: try {
            LastHttpContent trailer = readTrailingHeaders(buffer);
            out.add(trailer);
            reset();
            return;
        } catch (Exception e) {
            out.add(invalidChunk(e));
            return;
        }
        case BAD_MESSAGE: {
            // Keep discarding until disconnection.
            buffer.skipBytes(actualReadableBytes());
            break;
        }
        case UPGRADED: {
            int readableBytes = actualReadableBytes();
            if (readableBytes > 0) {
                // Keep on consuming as otherwise we may trigger an DecoderException,
                // other handler will replace this codec with the upgraded protocol codec to
                // take the traffic over at some point then.
                // See https://github.com/netty/netty/issues/2173
                out.add(buffer.readBytes(actualReadableBytes()));
            }
            break;
        }
        }
    }

    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        decode(ctx, in, out);

        // Handle the last unfinished message.
        if (message != null) {
            boolean chunked = HttpHeaders.isTransferEncodingChunked(message);
             if (state() == State.READ_VARIABLE_LENGTH_CONTENT && !in.isReadable() && !chunked) {
                // End of connection.
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                reset();
                return;
            }
            // Check if the closure of the connection signifies the end of the content.
            boolean prematureClosure;
            if (isDecodingRequest() || chunked) {
                // The last request did not wait for a response.
                prematureClosure = true;
            } else {
                // Compare the length of the received content and the 'Content-Length' header.
                // If the 'Content-Length' header is absent, the length of the content is determined by the end of the
                // connection, so it is perfectly fine.
                prematureClosure = contentLength() > 0;
            }
            reset();

            if (!prematureClosure) {
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
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
                // One exception: Hixie 76 websocket handshake response
                return !(code == 101 && !res.headers().contains(HttpHeaders.Names.SEC_WEBSOCKET_ACCEPT));
            }

            switch (code) {
            case 204: case 205: case 304:
                return true;
            }
        }
        return false;
    }

    private void reset() {
        HttpMessage message = this.message;
        this.message = null;
        name = null;
        value = null;
        contentLength = Long.MIN_VALUE;
        lineParser.reset();
        headerParser.reset();
        if (!isDecodingRequest()) {
            HttpResponse res = (HttpResponse) message;
            if (res != null && res.getStatus().code() == 101) {
                checkpoint(State.UPGRADED);
                return;
            }
        }

        checkpoint(State.SKIP_CONTROL_CHARS);
    }

    private HttpMessage invalidMessage(Exception cause) {
        checkpoint(State.BAD_MESSAGE);
        if (message != null) {
            message.setDecoderResult(DecoderResult.failure(cause));
        } else {
            message = createInvalidMessage();
            message.setDecoderResult(DecoderResult.failure(cause));
        }

        HttpMessage ret = message;
        message = null;
        return ret;
    }

    private HttpContent invalidChunk(Exception cause) {
        checkpoint(State.BAD_MESSAGE);
        HttpContent chunk = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        chunk.setDecoderResult(DecoderResult.failure(cause));
        message = null;
        return chunk;
    }

    private static void skipControlCharacters(ByteBuf buffer) {
        for (;;) {
            char c = (char) buffer.readUnsignedByte();
            if (!Character.isISOControl(c) &&
                !Character.isWhitespace(c)) {
                buffer.readerIndex(buffer.readerIndex() - 1);
                break;
            }
        }
    }

    private State readHeaders(ByteBuf buffer) {
        final HttpMessage message = this.message;
        final HttpHeaders headers = message.headers();

        AppendableCharSequence line = headerParser.parse(buffer);
        if (line.length() > 0) {
            do {
                char firstChar = line.charAt(0);
                if (name != null && (firstChar == ' ' || firstChar == '\t')) {
                    value = value.toString() + ' ' + line.toString().trim();
                } else {
                    if (name != null) {
                        headers.add(name, value);
                    }
                    splitHeader(line);
                }

                line = headerParser.parse(buffer);
            } while (line.length() > 0);
        }

        // Add the last header.
        if (name != null) {
            headers.add(name, value);
        }
        // reset name and value fields
        name = null;
        value = null;

        State nextState;

        if (isContentAlwaysEmpty(message)) {
            HttpHeaders.removeTransferEncodingChunked(message);
            nextState = State.SKIP_CONTROL_CHARS;
        } else if (HttpHeaders.isTransferEncodingChunked(message)) {
            nextState = State.READ_CHUNK_SIZE;
        } else if (contentLength() >= 0) {
            nextState = State.READ_FIXED_LENGTH_CONTENT;
        } else {
            nextState = State.READ_VARIABLE_LENGTH_CONTENT;
        }
        return nextState;
    }

    private long contentLength() {
        if (contentLength == Long.MIN_VALUE) {
            contentLength = HttpHeaders.getContentLength(message, -1);
        }
        return contentLength;
    }

    private LastHttpContent readTrailingHeaders(ByteBuf buffer) {
        AppendableCharSequence line = headerParser.parse(buffer);
        CharSequence lastHeader = null;
        if (line.length() > 0) {
            LastHttpContent trailer = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, validateHeaders);
            do {
                char firstChar = line.charAt(0);
                if (lastHeader != null && (firstChar == ' ' || firstChar == '\t')) {
                    List<String> current = trailer.trailingHeaders().getAll(lastHeader);
                    if (!current.isEmpty()) {
                        int lastPos = current.size() - 1;
                        String newString = current.get(lastPos) + line.toString().trim();
                        current.set(lastPos, newString);
                    } else {
                        // Content-Length, Transfer-Encoding, or Trailer
                    }
                } else {
                    splitHeader(line);
                    CharSequence headerName = name;
                    if (!HttpHeaders.equalsIgnoreCase(headerName, HttpHeaders.Names.CONTENT_LENGTH) &&
                            !HttpHeaders.equalsIgnoreCase(headerName, HttpHeaders.Names.TRANSFER_ENCODING) &&
                            !HttpHeaders.equalsIgnoreCase(headerName, HttpHeaders.Names.TRAILER)) {
                        trailer.trailingHeaders().add(headerName, value);
                    }
                    lastHeader = name;
                    // reset name and value fields
                    name = null;
                    value = null;
                }

                line = headerParser.parse(buffer);
            } while (line.length() > 0);

            return trailer;
        }

        return LastHttpContent.EMPTY_LAST_CONTENT;
    }

    protected abstract boolean isDecodingRequest();
    protected abstract HttpMessage createMessage(String[] initialLine) throws Exception;
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

    private static String[] splitInitialLine(AppendableCharSequence sb) {
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        int cStart;
        int cEnd;

        aStart = findNonWhitespace(sb, 0);
        aEnd = findWhitespace(sb, aStart);

        bStart = findNonWhitespace(sb, aEnd);
        bEnd = findWhitespace(sb, bStart);

        cStart = findNonWhitespace(sb, bEnd);
        cEnd = findEndOfString(sb);

        return new String[] {
                sb.substring(aStart, aEnd),
                sb.substring(bStart, bEnd),
                cStart < cEnd? sb.substring(cStart, cEnd) : "" };
    }

    private void splitHeader(AppendableCharSequence sb) {
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

        name = sb.substring(nameStart, nameEnd);
        valueStart = findNonWhitespace(sb, colonEnd);
        if (valueStart == length) {
            value = EMPTY_VALUE;
        } else {
            valueEnd = findEndOfString(sb);
            value = sb.substring(valueStart, valueEnd);
        }
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

    private static int findWhitespace(CharSequence sb, int offset) {
        int result;
        for (result = offset; result < sb.length(); result ++) {
            if (Character.isWhitespace(sb.charAt(result))) {
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

    private class HeaderParser implements ByteBufProcessor {
        private final AppendableCharSequence seq;
        private final int maxLength;
        private int size;

        HeaderParser(AppendableCharSequence seq, int maxLength) {
            this.seq = seq;
            this.maxLength = maxLength;
        }

        public AppendableCharSequence parse(ByteBuf buffer) {
            seq.reset();
            int i = buffer.forEachByte(this);
            buffer.readerIndex(i + 1);

            // Call checkpoint to make sure the readerIndex is updated correctly
            checkpoint();
            return seq;
        }

        public void reset() {
            size = 0;
        }

        @Override
        public boolean process(byte value) throws Exception {
            char nextByte = (char) value;
            if (nextByte == HttpConstants.CR) {
                return true;
            }
            if (nextByte == HttpConstants.LF) {
                return false;
            }
            if (size >= maxLength) {
                // TODO: Respond with Bad Request and discard the traffic
                //    or close the connection.
                //       No need to notify the upstream handlers - just log.
                //       If decoding a response, just throw an exception.
                throw newException(maxLength);
            }
            size ++;
            seq.append(nextByte);
            return true;
        }

        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException(
                    "HTTP header is larger than  " + maxLength +
                            " bytes.");
        }
    }

    private final class LineParser extends HeaderParser {

        LineParser(AppendableCharSequence seq, int maxLength) {
            super(seq, maxLength);
        }

        @Override
        public AppendableCharSequence parse(ByteBuf buffer) {
            reset();
            return super.parse(buffer);
        }

        @Override
        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException(
                    "An HTTP line is larger than " + maxLength +
                            " bytes.");
        }
    }
}
