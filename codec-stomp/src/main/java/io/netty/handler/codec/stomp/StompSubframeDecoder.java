/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import java.util.List;
import java.util.Locale;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.stomp.StompSubframeDecoder.State;
import io.netty.util.internal.AppendableCharSequence;

import static io.netty.buffer.ByteBufUtil.indexOf;
import static io.netty.buffer.ByteBufUtil.readBytes;

/**
 * Decodes {@link ByteBuf}s into {@link StompHeadersSubframe}s and
 * {@link StompContentSubframe}s.
 *
 * <h3>Parameters to control memory consumption: </h3>
 * {@code maxLineLength} the maximum length of line -
 * restricts length of command and header lines
 * If the length of the initial line exceeds this value, a
 * {@link TooLongFrameException} will be raised.
 * <br>
 * {@code maxChunkSize}
 * The maximum length of the content or each chunk.  If the content length
 * (or the length of each chunk) exceeds this value, the content or chunk
 * ill be split into multiple {@link StompContentSubframe}s whose length is
 * {@code maxChunkSize} at maximum.
 *
 * <h3>Chunked Content</h3>
 *
 * If the content of a stomp message is greater than {@code maxChunkSize}
 * the transfer encoding of the HTTP message is 'chunked', this decoder
 * generates multiple {@link StompContentSubframe} instances to avoid excessive memory
 * consumption. Note, that every message, even with no content decodes with
 * {@link LastStompContentSubframe} at the end to simplify upstream message parsing.
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

    private final int maxLineLength;
    private final int maxChunkSize;
    private final boolean validateHeaders;
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
        if (maxLineLength <= 0) {
            throw new IllegalArgumentException(
                    "maxLineLength must be a positive integer: " +
                            maxLineLength);
        }
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "maxChunkSize must be a positive integer: " +
                            maxChunkSize);
        }
        this.maxChunkSize = maxChunkSize;
        this.maxLineLength = maxLineLength;
        this.validateHeaders = validateHeaders;
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
        String commandStr = readLine(in, 16);
        StompCommand command = null;
        try {
            command = StompCommand.valueOf(commandStr);
        } catch (IllegalArgumentException iae) {
            //do nothing
        }
        if (command == null) {
            commandStr = commandStr.toUpperCase(Locale.US);
            try {
                command = StompCommand.valueOf(commandStr);
            } catch (IllegalArgumentException iae) {
                //do nothing
            }
        }
        if (command == null) {
            throw new DecoderException("failed to read command from channel");
        }
        return command;
    }

    private State readHeaders(ByteBuf buffer, StompHeaders headers) {
        AppendableCharSequence buf = new AppendableCharSequence(128);
        for (;;) {
            boolean headerRead = readHeader(headers, buf, buffer);
            if (!headerRead) {
                if (headers.contains(StompHeaders.CONTENT_LENGTH)) {
                    contentLength = getContentLength(headers, 0);
                    if (contentLength == 0) {
                        return State.FINALIZE_FRAME_READ;
                    }
                }
                return State.READ_CONTENT;
            }
        }
    }

    private static long getContentLength(StompHeaders headers, long defaultValue) {
        long contentLength = headers.getLong(StompHeaders.CONTENT_LENGTH, defaultValue);
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

    private String readLine(ByteBuf buffer, int initialBufferSize) {
        AppendableCharSequence buf = new AppendableCharSequence(initialBufferSize);
        int lineLength = 0;
        for (;;) {
            byte nextByte = buffer.readByte();
            if (nextByte == StompConstants.CR) {
                //do nothing
            } else if (nextByte == StompConstants.LF) {
                return buf.toString();
            } else {
                if (lineLength >= maxLineLength) {
                    invalidLineLength();
                }
                lineLength ++;
                buf.append((char) nextByte);
            }
        }
    }

    private boolean readHeader(StompHeaders headers, AppendableCharSequence buf, ByteBuf buffer) {
        buf.reset();
        int lineLength = 0;
        String key = null;
        boolean valid = false;
        for (;;) {
            byte nextByte = buffer.readByte();

            if (nextByte == StompConstants.COLON && key == null) {
                key = buf.toString();
                valid = true;
                buf.reset();
            } else if (nextByte == StompConstants.CR) {
                //do nothing
            } else if (nextByte == StompConstants.LF) {
                if (key == null && lineLength == 0) {
                    return false;
                } else if (valid) {
                    headers.add(key, buf.toString());
                } else if (validateHeaders) {
                    invalidHeader(key, buf.toString());
                }
                return true;
            } else {
                if (lineLength >= maxLineLength) {
                    invalidLineLength();
                }
                if (nextByte == StompConstants.COLON && key != null) {
                    valid = false;
                }
                lineLength ++;
                buf.append((char) nextByte);
            }
        }
    }

    private void invalidHeader(String key, String value) {
        String line = key != null ? key + ":" + value : value;
        throw new IllegalArgumentException("a header value or name contains a prohibited character ':'"
            + ", " + line);
    }

    private void invalidLineLength() {
        throw new TooLongFrameException("An STOMP line is larger than " + maxLineLength + " bytes.");
    }

    private void resetDecoder() {
        checkpoint(State.SKIP_CONTROL_CHARACTERS);
        contentLength = -1;
        alreadyReadChunkSize = 0;
        lastContent = null;
    }
}
