/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.http;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;

/**
 * Decodes an Http type message.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public abstract class HttpMessageDecoder extends ReplayingDecoder<HttpMessageDecoder.State> {

    private static final Pattern INITIAL_PATTERN = Pattern.compile(
            "^\\s*(\\S+)\\s+(\\S+)\\s+(.*)\\s*$");
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^\\s*(\\S+)\\s*:\\s*(.*)\\s*$");

    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int maxChunkSize;
    protected volatile HttpMessage message;
    private volatile ChannelBuffer content;
    private volatile int headerSize;
    private volatile int chunkSize;

    /**
     * @author The Netty Project (netty-dev@lists.jboss.org)
     * @author Trustin Lee (tlee@redhat.com)
     * @version $Rev$, $Date$
     *
     * @apiviz.exclude
     */
    protected enum State {
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
        READ_CHUNK_FOOTER;
    }

    protected HttpMessageDecoder() {
        this(4096, 8192, 8192);
    }

    protected HttpMessageDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {

        super(State.SKIP_CONTROL_CHARS, true);

        if (maxInitialLineLength <= 0) {
            throw new IllegalArgumentException(
                    "maxInitialLineLength must be a positive integer: " +
                    maxInitialLineLength);
        }
        if (maxHeaderSize <= 0) {
            throw new IllegalArgumentException(
                    "maxHeaderSize must be a positive integer: " +
                    maxChunkSize);
        }
        if (maxChunkSize < 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be a positive integer: " +
                    maxChunkSize);
        }
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxHeaderSize = maxHeaderSize;
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, State state) throws Exception {
        switch (state) {
        case SKIP_CONTROL_CHARS: {
            try {
                skipControlCharacters(buffer);
                checkpoint(State.READ_INITIAL);
            } finally {
                checkpoint();
            }
        }
        case READ_INITIAL: {
            String[] initialLine = splitInitialLine(readLine(buffer, maxInitialLineLength));
            if (initialLine.length < 3) {
                // Invalid initial line - ignore.
                checkpoint(State.SKIP_CONTROL_CHARS);
                return null;
            }

            message = createMessage(initialLine);
            checkpoint(State.READ_HEADER);
            headerSize = 0;
        }
        case READ_HEADER: {
            State nextState = readHeaders(buffer);
            checkpoint(nextState);
            if (nextState == State.READ_CHUNK_SIZE) {
                // Chunked encoding
                // Generate HttpMessage first.  HttpChunks will follow.
                return message;
            } else {
                int contentLength = message.getContentLength(-1);
                if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                    content = ChannelBuffers.EMPTY_BUFFER;
                    return reset();
                }

                switch (nextState) {
                case READ_FIXED_LENGTH_CONTENT:
                    if (contentLength > maxChunkSize) {
                        // Generate HttpMessage first.  HttpChunks will follow.
                        checkpoint(State.READ_FIXED_LENGTH_CONTENT_AS_CHUNKS);
                        message.addHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
                        // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT_AS_CHUNKS
                        // state reads data chunk by chunk.
                        chunkSize = message.getContentLength(-1);
                        return message;
                    }
                    break;
                case READ_VARIABLE_LENGTH_CONTENT:
                    if (buffer.readableBytes() > maxChunkSize) {
                        // Generate HttpMessage first.  HttpChunks will follow.
                        checkpoint(State.READ_VARIABLE_LENGTH_CONTENT_AS_CHUNKS);
                        message.addHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
                        return message;
                    }
                    break;
                }
            }
            // We return null here, this forces decode to be called again where we will decode the content
            return null;
        }
        case READ_VARIABLE_LENGTH_CONTENT: {
            if (content == null) {
                content = ChannelBuffers.dynamicBuffer(channel.getConfig().getBufferFactory());
            }
            //this will cause a replay error until the channel is closed where this will read what's left in the buffer
            content.writeBytes(buffer.readBytes(buffer.readableBytes()));
            return reset();
        }
        case READ_VARIABLE_LENGTH_CONTENT_AS_CHUNKS: {
            // Keep reading data as a chunk until the end of connection is reached.
            int chunkSize = Math.min(maxChunkSize, buffer.readableBytes());
            HttpChunk chunk = new DefaultHttpChunk(buffer.readBytes(chunkSize));

            if (!buffer.readable()) {
                // Reached to the end of the connection.
                reset();
                if (!chunk.isLast()) {
                    // Append the last chunk.
                    return new HttpChunk[] { chunk, HttpChunk.LAST_CHUNK };
                }
            }
            return chunk;
        }
        case READ_FIXED_LENGTH_CONTENT: {
            //we have a content-length so we just read the correct number of bytes
            readFixedLengthContent(buffer);
            return reset();
        }
        case READ_FIXED_LENGTH_CONTENT_AS_CHUNKS: {
            int chunkSize = this.chunkSize;
            HttpChunk chunk;
            if (chunkSize > maxChunkSize) {
                chunk = new DefaultHttpChunk(buffer.readBytes(maxChunkSize));
                chunkSize -= maxChunkSize;
            } else {
                chunk = new DefaultHttpChunk(buffer.readBytes(chunkSize));
                chunkSize = 0;
            }
            this.chunkSize = chunkSize;

            if (chunkSize == 0) {
                // Read all content.
                reset();
                if (!chunk.isLast()) {
                    // Append the last chunk.
                    return new HttpChunk[] { chunk, HttpChunk.LAST_CHUNK };
                }
            }
            return chunk;
        }
        /**
         * everything else after this point takes care of reading chunked content. basically, read chunk size,
         * read chunk, read and ignore the CRLF and repeat until 0
         */
        case READ_CHUNK_SIZE: {
            String line = readLine(buffer, maxInitialLineLength);
            int chunkSize = getChunkSize(line);
            this.chunkSize = chunkSize;
            if (chunkSize == 0) {
                checkpoint(State.READ_CHUNK_FOOTER);
                return null;
            } else if (chunkSize > maxChunkSize) {
                // A chunk is too large. Split them into multiple chunks again.
                checkpoint(State.READ_CHUNKED_CONTENT_AS_CHUNKS);
            } else {
                checkpoint(State.READ_CHUNKED_CONTENT);
            }
        }
        case READ_CHUNKED_CONTENT: {
            HttpChunk chunk = new DefaultHttpChunk(buffer.readBytes(chunkSize));
            checkpoint(State.READ_CHUNK_DELIMITER);
            return chunk;
        }
        case READ_CHUNKED_CONTENT_AS_CHUNKS: {
            int chunkSize = this.chunkSize;
            HttpChunk chunk;
            if (chunkSize > maxChunkSize) {
                chunk = new DefaultHttpChunk(buffer.readBytes(maxChunkSize));
                chunkSize -= maxChunkSize;
            } else {
                chunk = new DefaultHttpChunk(buffer.readBytes(chunkSize));
                chunkSize = 0;
            }
            this.chunkSize = chunkSize;

            if (chunkSize == 0) {
                // Read all content.
                checkpoint(State.READ_CHUNK_DELIMITER);
            }

            if (!chunk.isLast()) {
                return chunk;
            }
        }
        case READ_CHUNK_DELIMITER: {
            for (;;) {
                byte next = buffer.readByte();
                if (next == HttpCodecUtil.CR) {
                    if (buffer.readByte() == HttpCodecUtil.LF) {
                        checkpoint(State.READ_CHUNK_SIZE);
                        return null;
                    }
                } else if (next == HttpCodecUtil.LF) {
                    checkpoint(State.READ_CHUNK_SIZE);
                    return null;
                }
            }
        }
        case READ_CHUNK_FOOTER: {
            // Skip the footer; does anyone use it?
            try {
                if (!skipLine(buffer)) {
                    if (maxChunkSize == 0) {
                        // Chunked encoding disabled.
                        return reset();
                    } else {
                        reset();
                        // The last chunk, which is empty
                        return HttpChunk.LAST_CHUNK;
                    }
                }
            } finally {
                checkpoint();
            }
            return null;
        }
        default: {
            throw new Error("Shouldn't reach here.");
        }

        }
    }

    private Object reset() {
        HttpMessage message = this.message;
        ChannelBuffer content = this.content;

        if (content != null) {
            message.setContent(content);
            this.content = null;
        }
        this.message = null;

        checkpoint(State.SKIP_CONTROL_CHARS);
        return message;
    }

    private void skipControlCharacters(ChannelBuffer buffer) {
        for (;;) {
            char c = (char) buffer.readUnsignedByte();
            if (!Character.isISOControl(c) &&
                !Character.isWhitespace(c)) {
                buffer.readerIndex(buffer.readerIndex() - 1);
                break;
            }
        }
    }

    private void readFixedLengthContent(ChannelBuffer buffer) {
        int length = message.getContentLength(-1);
        if (content == null) {
            content = buffer.readBytes(length);
        } else {
            content.writeBytes(buffer.readBytes(length));
        }
    }

    private State readHeaders(ChannelBuffer buffer) throws TooLongFrameException {
        message.clearHeaders();
        String line = readHeader(buffer);
        String lastHeader = null;
        while (line.length() != 0) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                List<String> current = message.getHeaders(lastHeader);
                int lastPos = current.size() - 1;
                String newString = current.get(lastPos) + line.trim();
                current.remove(lastPos);
                current.add(newString);
            }
            else {
                String[] header = splitHeader(line);
                message.addHeader(header[0], header[1]);
                lastHeader = header[0];
            }
            line = readHeader(buffer);
        }

        State nextState;
        if (message.isChunked()) {
            nextState = State.READ_CHUNK_SIZE;
        } else if (message.getContentLength(-1) >= 0) {
            nextState = State.READ_FIXED_LENGTH_CONTENT;
        } else {
            nextState = State.READ_VARIABLE_LENGTH_CONTENT;
        }
        return nextState;
    }

    private String readHeader(ChannelBuffer buffer) throws TooLongFrameException {
        StringBuilder sb = new StringBuilder(64);
        int headerSize = this.headerSize;
        while (true) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpCodecUtil.CR) {
                nextByte = buffer.readByte();
                if (nextByte == HttpCodecUtil.LF) {
                    this.headerSize = headerSize + 2;
                    return sb.toString();
                }
            }
            else if (nextByte == HttpCodecUtil.LF) {
                this.headerSize = headerSize + 1;
                return sb.toString();
            }
            else {
                // Abort decoding if the header part is too large.
                if (headerSize >= maxHeaderSize) {
                    throw new TooLongFrameException(
                            "HTTP header is larger than " +
                            maxHeaderSize + " bytes.");

                }
                headerSize ++;
                sb.append((char) nextByte);
            }
        }
    }

    protected abstract boolean isDecodingRequest();
    protected abstract HttpMessage createMessage(String[] initialLine) throws Exception;

    private int getChunkSize(String hex) {
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

    private String readLine(ChannelBuffer buffer, int maxLineLength) throws TooLongFrameException {
        StringBuilder sb = new StringBuilder(64);
        int lineLength = 0;
        while (true) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpCodecUtil.CR) {
                nextByte = buffer.readByte();
                if (nextByte == HttpCodecUtil.LF) {
                    return sb.toString();
                }
            }
            else if (nextByte == HttpCodecUtil.LF) {
                return sb.toString();
            }
            else {
                if (lineLength >= maxLineLength) {
                    throw new TooLongFrameException(
                            "An HTTP line is larger than " + maxLineLength +
                            " bytes.");
                }
                lineLength ++;
                sb.append((char) nextByte);
            }
        }
    }

    /**
     * Returns {@code true} if only if the skipped line was not empty.
     * Please note that an empty line is also skipped, while {@code} false is
     * returned.
     */
    private boolean skipLine(ChannelBuffer buffer) {
        int lineLength = 0;
        while (true) {
            byte nextByte = buffer.readByte();
            if (nextByte == HttpCodecUtil.CR) {
                nextByte = buffer.readByte();
                if (nextByte == HttpCodecUtil.LF) {
                    return lineLength != 0;
                }
            }
            else if (nextByte == HttpCodecUtil.LF) {
                return lineLength != 0;
            }
            else if (!Character.isWhitespace((char) nextByte)) {
                lineLength ++;
            }
        }
    }

    private String[] splitInitialLine(String sb) {
        Matcher m = INITIAL_PATTERN.matcher(sb);
        if (m.matches()) {
            return new String[] { m.group(1), m.group(2), m.group(3) };
        } else {
            throw new IllegalArgumentException("Invalid initial line: " + sb);
        }
    }

    private String[] splitHeader(String sb) {
        Matcher m = HEADER_PATTERN.matcher(sb);
        if (m.matches()) {
            return new String[] { m.group(1), m.group(2) };
        } else {
            throw new IllegalArgumentException("Invalid header syntax: " + sb);
        }
    }
}
