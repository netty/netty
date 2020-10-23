/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.stomp.StompSubframeDecoder.State;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.AppendableCharSequence;
import io.netty.util.internal.StringUtil;

import java.util.List;

import static io.netty.buffer.ByteBufUtil.*;
import static io.netty.util.internal.ObjectUtil.*;

/**
 * Decodes {@link ByteBuf}s into {@link StompHeadersSubframe}s and {@link StompContentSubframe}s.
 *
 * <h3>Parameters to control memory consumption: </h3>
 * {@code maxLineLength} the maximum length of line - restricts length of command and header lines If the length of the
 * initial line exceeds this value, a {@link TooLongFrameException} will be raised.
 * <br>
 * {@code maxChunkSize} The maximum length of the content or each chunk.  If the content length (or the length of each
 * chunk) exceeds this value, the content or chunk ill be split into multiple {@link StompContentSubframe}s whose length
 * is {@code maxChunkSize} at maximum.
 *
 * <h3>Chunked Content</h3>
 * <p>
 * If the content of a stomp message is greater than {@code maxChunkSize} the transfer encoding of the HTTP message is
 * 'chunked', this decoder generates multiple {@link StompContentSubframe} instances to avoid excessive memory
 * consumption. Note, that every message, even with no content decodes with {@link LastStompContentSubframe} at the end
 * to simplify upstream message parsing.
 */
public class StompSubframeDecoder extends ReplayingDecoder<State> {

    private static final int DEFAULT_CHUNK_SIZE = 8132;
    private static final int DEFAULT_MAX_LINE_LENGTH = 1024;

    enum State {
        SKIP_CONTROL_CHARACTERS,
        READ_HEADERS,
        READ_CONTENT,
        FINALIZE_FRAME_READ,
        BAD_FRAME,
        INVALID_CHUNK
    }

    private final Utf8LineParser commandParser;
    private final HeaderParser headerParser;
    private final int maxChunkSize;
    private int alreadyReadChunkSize;
    private LastStompContentSubframe lastContent;
    private long contentLength = -1;

    public StompSubframeDecoder() {
        this(DEFAULT_MAX_LINE_LENGTH, DEFAULT_CHUNK_SIZE);
    }

    public StompSubframeDecoder(boolean validateHeaders) {
        this(DEFAULT_MAX_LINE_LENGTH, DEFAULT_CHUNK_SIZE, validateHeaders);
    }

    public StompSubframeDecoder(int maxLineLength, int maxChunkSize) {
        this(maxLineLength, maxChunkSize, false);
    }

    public StompSubframeDecoder(int maxLineLength, int maxChunkSize, boolean validateHeaders) {
        super(State.SKIP_CONTROL_CHARACTERS);
        checkPositive(maxLineLength, "maxLineLength");
        checkPositive(maxChunkSize, "maxChunkSize");
        this.maxChunkSize = maxChunkSize;
        commandParser = new Utf8LineParser(new AppendableCharSequence(16), maxLineLength);
        headerParser = new HeaderParser(new AppendableCharSequence(128), maxLineLength, validateHeaders);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case SKIP_CONTROL_CHARACTERS:
                skipControlCharacters(in);
                checkpoint(State.READ_HEADERS);
                // Fall through.
            case READ_HEADERS:
                StompCommand command = StompCommand.UNKNOWN;
                StompHeadersSubframe frame = null;
                try {
                    command = readCommand(in);
                    frame = new DefaultStompHeadersSubframe(command);
                    checkpoint(readHeaders(in, frame.headers()));
                    out.add(frame);
                } catch (Exception e) {
                    if (frame == null) {
                        frame = new DefaultStompHeadersSubframe(command);
                    }
                    frame.setDecoderResult(DecoderResult.failure(e));
                    out.add(frame);
                    checkpoint(State.BAD_FRAME);
                    return;
                }
                break;
            case BAD_FRAME:
                in.skipBytes(actualReadableBytes());
                return;
        }
        try {
            switch (state()) {
                case READ_CONTENT:
                    int toRead = in.readableBytes();
                    if (toRead == 0) {
                        return;
                    }
                    if (toRead > maxChunkSize) {
                        toRead = maxChunkSize;
                    }
                    if (contentLength >= 0) {
                        int remainingLength = (int) (contentLength - alreadyReadChunkSize);
                        if (toRead > remainingLength) {
                            toRead = remainingLength;
                        }
                        ByteBuf chunkBuffer = readBytes(ctx.alloc(), in, toRead);
                        if ((alreadyReadChunkSize += toRead) >= contentLength) {
                            lastContent = new DefaultLastStompContentSubframe(chunkBuffer);
                            checkpoint(State.FINALIZE_FRAME_READ);
                        } else {
                            out.add(new DefaultStompContentSubframe(chunkBuffer));
                            return;
                        }
                    } else {
                        int nulIndex = indexOf(in, in.readerIndex(), in.writerIndex(), StompConstants.NUL);
                        if (nulIndex == in.readerIndex()) {
                            checkpoint(State.FINALIZE_FRAME_READ);
                        } else {
                            if (nulIndex > 0) {
                                toRead = nulIndex - in.readerIndex();
                            } else {
                                toRead = in.writerIndex() - in.readerIndex();
                            }
                            ByteBuf chunkBuffer = readBytes(ctx.alloc(), in, toRead);
                            alreadyReadChunkSize += toRead;
                            if (nulIndex > 0) {
                                lastContent = new DefaultLastStompContentSubframe(chunkBuffer);
                                checkpoint(State.FINALIZE_FRAME_READ);
                            } else {
                                out.add(new DefaultStompContentSubframe(chunkBuffer));
                                return;
                            }
                        }
                    }
                    // Fall through.
                case FINALIZE_FRAME_READ:
                    skipNullCharacter(in);
                    if (lastContent == null) {
                        lastContent = LastStompContentSubframe.EMPTY_LAST_CONTENT;
                    }
                    out.add(lastContent);
                    resetDecoder();
            }
        } catch (Exception e) {
            StompContentSubframe errorContent = new DefaultLastStompContentSubframe(Unpooled.EMPTY_BUFFER);
            errorContent.setDecoderResult(DecoderResult.failure(e));
            out.add(errorContent);
            checkpoint(State.BAD_FRAME);
        }
    }

    private StompCommand readCommand(ByteBuf in) {
        CharSequence commandSequence = commandParser.parse(in);
        if (commandSequence == null) {
            throw new DecoderException("Failed to read command from channel");
        }
        String commandStr = commandSequence.toString();
        try {
            return StompCommand.valueOf(commandStr);
        } catch (IllegalArgumentException iae) {
            throw new DecoderException("Cannot to parse command " + commandStr);
        }
    }

    private State readHeaders(ByteBuf buffer, StompHeaders headers) {
        for (;;) {
            boolean headerRead = headerParser.parseHeader(headers, buffer);
            if (!headerRead) {
                if (headers.contains(StompHeaders.CONTENT_LENGTH)) {
                    contentLength = getContentLength(headers);
                    if (contentLength == 0) {
                        return State.FINALIZE_FRAME_READ;
                    }
                }
                return State.READ_CONTENT;
            }
        }
    }

    private static long getContentLength(StompHeaders headers) {
        long contentLength = headers.getLong(StompHeaders.CONTENT_LENGTH, 0L);
        if (contentLength < 0) {
            throw new DecoderException(StompHeaders.CONTENT_LENGTH + " must be non-negative");
        }
        return contentLength;
    }

    private static void skipNullCharacter(ByteBuf buffer) {
        byte b = buffer.readByte();
        if (b != StompConstants.NUL) {
            throw new IllegalStateException("unexpected byte in buffer " + b + " while expecting NULL byte");
        }
    }

    private static void skipControlCharacters(ByteBuf buffer) {
        byte b;
        for (;;) {
            b = buffer.readByte();
            if (b != StompConstants.CR && b != StompConstants.LF) {
                buffer.readerIndex(buffer.readerIndex() - 1);
                break;
            }
        }
    }

    private void resetDecoder() {
        checkpoint(State.SKIP_CONTROL_CHARACTERS);
        contentLength = -1;
        alreadyReadChunkSize = 0;
        lastContent = null;
    }

    private static class Utf8LineParser implements ByteProcessor {

        private final AppendableCharSequence charSeq;
        private final int maxLineLength;

        private int lineLength;
        private char interim;
        private boolean nextRead;

        Utf8LineParser(AppendableCharSequence charSeq, int maxLineLength) {
            this.charSeq = checkNotNull(charSeq, "charSeq");
            this.maxLineLength = maxLineLength;
        }

        AppendableCharSequence parse(ByteBuf byteBuf) {
            reset();
            int offset = byteBuf.forEachByte(this);
            if (offset == -1) {
                return null;
            }

            byteBuf.readerIndex(offset + 1);
            return charSeq;
        }

        AppendableCharSequence charSequence() {
            return charSeq;
        }

        @Override
        public boolean process(byte nextByte) throws Exception {
            if (nextByte == StompConstants.CR) {
                ++lineLength;
                return true;
            }

            if (nextByte == StompConstants.LF) {
                return false;
            }

            if (++lineLength > maxLineLength) {
                throw new TooLongFrameException("An STOMP line is larger than " + maxLineLength + " bytes.");
            }

            // 1 byte   -   0xxxxxxx                    -  7 bits
            // 2 byte   -   110xxxxx 10xxxxxx           -  11 bits
            // 3 byte   -   1110xxxx 10xxxxxx 10xxxxxx  -  16 bits
            if (nextRead) {
                interim |= (nextByte & 0x3F) << 6;
                nextRead = false;
            } else if (interim != 0) { // flush 2 or 3 byte
                charSeq.append((char) (interim | (nextByte & 0x3F)));
                interim = 0;
            } else if (nextByte >= 0) { // INITIAL BRANCH
                // The first 128 characters (US-ASCII) need one byte.
                charSeq.append((char) nextByte);
            } else if ((nextByte & 0xE0) == 0xC0) {
                // The next 1920 characters need two bytes and we can define
                // a first byte by mask 110xxxxx.
                interim = (char) ((nextByte & 0x1F) << 6);
            } else {
                // The rest of characters need three bytes.
                interim = (char) ((nextByte & 0x0F) << 12);
                nextRead = true;
            }

            return true;
        }

        protected void reset() {
            charSeq.reset();
            lineLength = 0;
            interim = 0;
            nextRead = false;
        }
    }

    private static final class HeaderParser extends Utf8LineParser {

        private final boolean validateHeaders;

        private String name;
        private boolean valid;

        HeaderParser(AppendableCharSequence charSeq, int maxLineLength, boolean validateHeaders) {
            super(charSeq, maxLineLength);
            this.validateHeaders = validateHeaders;
        }

        boolean parseHeader(StompHeaders headers, ByteBuf buf) {
            AppendableCharSequence value = super.parse(buf);
            if (value == null || (name == null && value.length() == 0)) {
                return false;
            }

            if (valid) {
                headers.add(name, value.toString());
            } else if (validateHeaders) {
                if (StringUtil.isNullOrEmpty(name)) {
                    throw new IllegalArgumentException("received an invalid header line '" + value.toString() + '\'');
                }
                String line = name + ':' + value.toString();
                throw new IllegalArgumentException("a header value or name contains a prohibited character ':'"
                                                   + ", " + line);
            }
            return true;
        }

        @Override
        public boolean process(byte nextByte) throws Exception {
            if (nextByte == StompConstants.COLON) {
                if (name == null) {
                    AppendableCharSequence charSeq = charSequence();
                    if (charSeq.length() != 0) {
                        name = charSeq.substring(0, charSeq.length());
                        charSeq.reset();
                        valid = true;
                        return true;
                    } else {
                        name = StringUtil.EMPTY_STRING;
                    }
                } else {
                    valid = false;
                }
            }

            return super.process(nextByte);
        }

        @Override
        protected void reset() {
            name = null;
            valid = false;
            super.reset();
        }
    }
}
