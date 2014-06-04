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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.stomp.StompDecoder.State;

import static io.netty.buffer.ByteBufUtil.readBytes;

/**
 * Decodes {@link ByteBuf}s into {@link StompFrame}s and
 * {@link StompContent}s.
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
 * ill be split into multiple {@link StompContent}s whose length is
 * {@code maxChunkSize} at maximum.
 *
 * <h3>Chunked Content</h3>
 *
 * If the content of a stomp message is greater than {@code maxChunkSize}
 * the transfer encoding of the HTTP message is 'chunked', this decoder
 * generates multiple {@link StompContent} instances to avoid excessive memory
 * consumption. Note, that every message, even with no content decodes with
 * {@link LastStompContent} at the end to simplify upstream message parsing.
 */
public class StompDecoder extends ReplayingDecoder<State> {
    public static final int DEFAULT_CHUNK_SIZE = 8132;
    public static final int DEFAULT_MAX_LINE_LENGTH = 1024;
    private int maxLineLength;
    private int maxChunkSize;
    private int alreadyReadChunkSize;
    private LastStompContent lastContent;
    private long contentLength;

    public StompDecoder() {
        this(DEFAULT_MAX_LINE_LENGTH, DEFAULT_CHUNK_SIZE);
    }

    public StompDecoder(int maxLineLength, int maxChunkSize) {
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
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case SKIP_CONTROL_CHARACTERS:
                skipControlCharacters(in);
                checkpoint(State.READ_HEADERS);
            case READ_HEADERS:
                StompCommand command = StompCommand.UNKNOWN;
                StompFrame frame = null;
                try {
                    command = readCommand(in);
                    frame = new DefaultStompFrame(command);
                    checkpoint(readHeaders(in, frame.headers()));
                    out.add(frame);
                } catch (Exception e) {
                    if (frame == null) {
                        frame = new DefaultStompFrame(command);
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
                    int remainingLength = (int) (contentLength - alreadyReadChunkSize);
                    if (toRead > remainingLength) {
                        toRead = remainingLength;
                    }
                    ByteBuf chunkBuffer = readBytes(ctx.alloc(), in, toRead);
                    if ((alreadyReadChunkSize += toRead) >= contentLength) {
                        lastContent = new DefaultLastStompContent(chunkBuffer);
                        checkpoint(State.FINALIZE_FRAME_READ);
                    } else {
                        DefaultStompContent chunk;
                        chunk = new DefaultStompContent(chunkBuffer);
                        out.add(chunk);
                    }
                    if (alreadyReadChunkSize < contentLength) {
                        return;
                    }
                    //fall through
                case FINALIZE_FRAME_READ:
                    skipNullCharacter(in);
                    if (lastContent == null) {
                        lastContent = LastStompContent.EMPTY_LAST_CONTENT;
                    }
                    out.add(lastContent);
                    resetDecoder();
            }
        } catch (Exception e) {
            StompContent errorContent = new DefaultLastStompContent(Unpooled.EMPTY_BUFFER);
            errorContent.setDecoderResult(DecoderResult.failure(e));
            out.add(errorContent);
            checkpoint(State.BAD_FRAME);
        }
    }

    private StompCommand readCommand(ByteBuf in) {
        String commandStr = readLine(in, maxLineLength);
        StompCommand command = null;
        try {
            command = StompCommand.valueOf(commandStr);
        } catch (IllegalArgumentException iae) {
            //do nothing
        }
        if (command == null) {
            commandStr = commandStr.toUpperCase();
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
        while (true) {
            String line = readLine(buffer, maxLineLength);
            if (line.length() > 0) {
                String[] split = line.split(":");
                if (split.length == 2) {
                    headers.add(split[0], split[1]);
                }
            } else {
                long contentLength = -1;
                if (headers.has(StompHeaders.CONTENT_LENGTH))  {
                    contentLength = StompHeaders.getContentLength(headers, 0);
                } else {
                    int globalIndex = ByteBufUtil.indexOf(buffer, buffer.readerIndex(),
                        buffer.writerIndex(), StompConstants.NULL);
                    if (globalIndex != -1) {
                        contentLength = globalIndex - buffer.readerIndex();
                    }
                }
                if (contentLength > 0) {
                    this.contentLength = contentLength;
                    return State.READ_CONTENT;
                } else {
                    return State.FINALIZE_FRAME_READ;
                }
            }
        }
    }

    private static void skipNullCharacter(ByteBuf buffer) {
        byte b = buffer.readByte();
        if (b != StompConstants.NULL) {
            throw new IllegalStateException("unexpected byte in buffer " + b + " while expecting NULL byte");
        }
    }

    private static void skipControlCharacters(ByteBuf buffer) {
        byte b;
        while (true) {
            b = buffer.readByte();
            if (b != StompConstants.CR && b != StompConstants.LF) {
                buffer.readerIndex(buffer.readerIndex() - 1);
                break;
            }
        }
    }

    private static String readLine(ByteBuf buffer, int maxLineLength) {
        StringBuilder sb = new StringBuilder();
        int lineLength = 0;
        while (true) {
            byte nextByte = buffer.readByte();
            if (nextByte == StompConstants.CR) {
                nextByte = buffer.readByte();
                if (nextByte == StompConstants.LF) {
                    return sb.toString();
                }
            } else if (nextByte == StompConstants.LF) {
                return sb.toString();
            } else {
                if (lineLength >= maxLineLength) {
                    throw new TooLongFrameException("An STOMP line is larger than " + maxLineLength + " bytes.");
                }
                lineLength++;
                sb.append((char) nextByte);
            }
        }
    }

    private void resetDecoder() {
        checkpoint(State.SKIP_CONTROL_CHARACTERS);
        contentLength = 0;
        alreadyReadChunkSize = 0;
        lastContent = null;
    }

    enum State {
        SKIP_CONTROL_CHARACTERS,
        READ_HEADERS,
        READ_CONTENT,
        FINALIZE_FRAME_READ,
        BAD_FRAME,
        INVALID_CHUNK
    }

}
